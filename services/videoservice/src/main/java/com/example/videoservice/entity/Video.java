package com.example.videoservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Video {
    @Id
    @Column(nullable = false, unique = true, updatable = false)
    private String id;

    @Column(nullable = false, unique = true, updatable = false)
    private String objectKey;

    @Column(nullable = false)
    private String title;
}
