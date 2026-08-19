package com.example.literaturesearch.dto.response;

import com.example.literaturesearch.entity.Literature;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiteratureResponse {
    private Long id;
    private String title;
    private String authors;
    private String abstractText;
    private String journal;
    private Integer year;
    private String doi;
    private String fileName;
    private LocalDateTime createdAt;

    public static LiteratureResponse fromEntity(Literature literature) {
        return LiteratureResponse.builder()
                .id(literature.getId())
                .title(literature.getTitle())
                .authors(literature.getAuthors())
                .abstractText(literature.getAbstractText())
                .journal(literature.getJournal())
                .year(literature.getYear())
                .doi(literature.getDoi())
                .fileName(literature.getFileName())
                .createdAt(literature.getCreatedAt())
                .build();
    }
}