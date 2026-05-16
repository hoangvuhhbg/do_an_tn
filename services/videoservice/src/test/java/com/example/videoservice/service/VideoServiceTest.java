package com.example.videoservice.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VideoServiceTest {

    @Test
    void testGenerateKey() {
        VideoService service = new VideoService();

        String id = service.generateKey();

        System.out.println("Generated ID: " + id);

        assertEquals(11, id.length());
    }
}