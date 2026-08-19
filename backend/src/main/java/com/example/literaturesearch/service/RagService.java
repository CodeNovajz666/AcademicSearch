package com.example.literaturesearch.service;

import com.example.literaturesearch.dto.response.LiteratureResponse;
import com.example.literaturesearch.dto.response.RagAnswerResponse;
import com.example.literaturesearch.dto.response.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorService vectorService;
    private final DashScopeService dashScopeService;

    public RagAnswerResponse query(String question, int topK) {
        List<SearchResult> searchResults = vectorService.search(question, topK);
        
        if (searchResults.isEmpty()) {
            return RagAnswerResponse.builder()
                    .answer("未找到相关文献，无法回答该问题。")
                    .references(List.of())
                    .build();
        }

        List<Long> literatureIds = searchResults.stream()
                .map(r -> r.getLiterature().getId())
                .collect(Collectors.toList());

        String context = vectorService.getContextForLiterature(literatureIds);
        String answer = dashScopeService.chat(question, context);

        List<LiteratureResponse> references = searchResults.stream()
                .map(SearchResult::getLiterature)
                .collect(Collectors.toList());

        log.info("RAG query completed, found {} relevant literatures", references.size());
        
        return RagAnswerResponse.builder()
                .answer(answer)
                .references(references)
                .build();
    }
}