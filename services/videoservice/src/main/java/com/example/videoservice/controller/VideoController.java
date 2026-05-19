package com.example.videoservice.controller;

import com.example.videoservice.dto.VideoResponseDTO;
import com.example.videoservice.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/videos")
@Slf4j
public class VideoController {
    @Autowired
    private VideoService videoService;

    private static final List<String> ALLOWED_VIDEO_TYPES = List.of(
            "video/mp4"
    );

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file){
        if(file.isEmpty()){
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng chọn một file video hợp lệ!"));
        }
        String contentType = file.getContentType();
        if(contentType == null || !ALLOWED_VIDEO_TYPES.contains(contentType)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "INVALID_FILE_FORMAT",
                    "message", "Định dạng file không hợp lệ! Hệ thống chỉ chấp nhận MP4, MKV, MOV."
            ));
        }
        try{
            String videoId = videoService.uploadVideo(file);
            return ResponseEntity.ok(Map.of(
                    "message", "Upload video thành công!",
                    "videoId", videoId
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/watch")
    public ResponseEntity<?> watchVideo(@RequestParam("v") String videoId){
        VideoResponseDTO videoResponseDTO = videoService.findByVideoId(videoId);
        return ResponseEntity.ok(videoResponseDTO);
    }
}
