package com.example.videoservice.service;

import com.example.videoservice.dto.VideoResponseDTO;
import com.example.videoservice.entity.Video;
import com.example.videoservice.repository.VideoRepository;
import io.minio.*;
import io.minio.http.Method;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VideoService {
    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.BucketName}")
    String bucketName;

    @Transactional(rollbackOn =  Exception.class)
    public String uploadVideo(MultipartFile file) throws Exception {
        try{
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }


        String videoId = generateKey();
        String fileName = file.getOriginalFilename();
        String extension = fileName.substring(fileName.lastIndexOf("."));
        if(extension.equals(".mp4")){
            String objectKey = videoId + extension;

            Video video = new Video();
            video.setId(videoId);
            video.setObjectKey(objectKey);
            video.setTitle(fileName);
            video.setStatus("PENDING");
            videoRepository.save(video);

            log.info("Upload to bucket " + bucketName);
//            minioClient.putObject(
//                    PutObjectArgs.builder()
//                            .bucket(bucketName)
//                            .object(objectKey)
//                            .stream(file.getInputStream(), file.getSize(), -1)
//                            .contentType(file.getContentType())
//                            .build()
//            );

            Path outputDir = Paths.get(System.getProperty("java.io.tmpdir"), videoId);
            Files.createDirectories(outputDir);

            String[] ffmpegCmd = {
                    "ffmpeg",
                    "-i", "pipe:0",           // Nhận đầu vào từ InputStream (Standard Input)
                    "-hls_time", "5",
                    "-hls_list_size", "0",
                    "-hls_segment_filename", outputDir.resolve("segment_%03d.ts").toString(),
                    "-c:v", "libx264",
                    "-c:a", "aac",
                    outputDir.resolve("playlist.m3u8").toString() // File master phát sóng
            };

            ProcessBuilder pb = new ProcessBuilder(ffmpegCmd);
            // 1. KHẮC PHỤC NGHẼN: Gộp luồng error stream vào input stream
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Luồng đẩy dữ liệu từ MultipartFile vào trong FFmpeg
            try (OutputStream os = process.getOutputStream();
                 InputStream is = file.getInputStream()) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            } // Tự động đóng luồng để báo EOF cho FFmpeg

            // 2. KHẮC PHỤC NGHẼN: Phải đọc hết luồng Log ra để giải phóng bộ đệm hệ thống
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[FFmpeg Log] " + line); // Log này giúp bạn theo dõi tiến độ
                }
            }

            // Đợi FFmpeg hoàn thành hoàn toàn
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg gặp lỗi trong quá trình phân mảnh trực tiếp, Exit code: " + exitCode);
            }

            log.info("FFmpeg đã phân mảnh xong từ luồng Stream trực tiếp!");

            // ---------------------------------------------------------------
            // Đẩy các file phân mảnh từ thư mục đầu ra lên MinIO
            // ---------------------------------------------------------------
            File[] generatedFiles = outputDir.toFile().listFiles();
            if (generatedFiles != null) {
                for (File f : generatedFiles) {
                    // SỬA LỖI ĐƯỜNG DẪN: Tạo object key riêng cho từng file phân đoạn
                    // Cấu trúc dạng: videoId/playlist.m3u8 hoặc videoId/segment_000.ts
                    String fileObjectKey = videoId + "/" + f.getName();

                    try (FileInputStream fis = new FileInputStream(f)) {
                        minioClient.putObject(
                                PutObjectArgs.builder()
                                        .bucket(bucketName)
                                        .object(fileObjectKey) // Sử dụng key riêng biệt
                                        .stream(fis, f.length(), -1)
                                        .contentType(f.getName().endsWith(".m3u8") ? "application/x-mpegURL" : "video/MP2T")
                                        .build()
                        );
                    }
                    // Xóa ngay file mảnh sau khi upload xong để giải phóng bộ nhớ ổ cứng
                    f.delete();
                }
            }
            Files.delete(outputDir);

            // Cập nhật lại Object Key chính của Video để trỏ vào file playlist phát sóng
            video.setObjectKey(videoId + "/playlist.m3u8");
            videoRepository.save(video); // Lưu lại thông tin chuẩn vào DB
        }
        else{
            throw new Exception("File type not supported");
        }
        return videoId;
    }

    String generateKey(){
        byte[] randomBytes = new byte[8];
        new java.security.SecureRandom().nextBytes(randomBytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public VideoResponseDTO findByVideoId(String videoId){
        VideoResponseDTO videoResponseDTO = new VideoResponseDTO();
        try{
            Video video = videoRepository.findById(videoId).orElseThrow(() -> new RuntimeException("Video not found: " + videoId));
            videoResponseDTO.setVideo(video);
            videoResponseDTO.setUrl(getPresignedUrl(video.getObjectKey()));
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
        return videoResponseDTO;
    }

    private String getPresignedUrl(String objectKey){
        try{
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

}
