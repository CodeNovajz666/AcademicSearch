package com.example.literaturesearch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "literature")
public class Literature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "authors", length = 1000)
    private String authors;

    @Column(name = "abstract_text", columnDefinition = "TEXT")
    private String abstractText;

    @Column(name = "journal", length = 200)
    private String journal;

    @Column(name = "year")
    private Integer year;

    @Column(name = "doi", length = 200)
    private String doi;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}