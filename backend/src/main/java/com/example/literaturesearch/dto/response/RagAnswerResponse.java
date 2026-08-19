package com.example.literaturesearch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagAnswerResponse {
    private String answer;
    private List<LiteratureResponse> references;
}