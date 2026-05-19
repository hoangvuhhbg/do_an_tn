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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VideoService {
    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucketName}")
    String bucketName;

    @Transactional
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
            log.info("Upload to bucket " + bucketName);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            videoRepository.save(video);
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
