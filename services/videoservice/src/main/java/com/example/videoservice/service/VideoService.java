package com.example.videoservice.service;

import com.example.videoservice.entity.Video;
import com.example.videoservice.repository.VideoRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!bucketExists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
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
            log.info("wrong extension: " + extension);
        }
        return videoId;
    }

    public String generateKey(){
        byte[] randomBytes = new byte[8];
        new java.security.SecureRandom().nextBytes(randomBytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
