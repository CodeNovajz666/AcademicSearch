package com.example.literaturesearch.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiteratureUploadRequest {
    private String title;
    private String authors;
    private String journal;
    private Integer year;
    private String doi;
}