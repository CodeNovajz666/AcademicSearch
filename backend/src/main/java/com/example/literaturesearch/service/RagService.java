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
        return query(question, topK, null);
    }

    /** ownerScope 非空 = 只在"我的知识库"范围内检索。 */
    public RagAnswerResponse query(String question, int topK, Long ownerScope) {
        VectorService.RetrievalResult retrieval = vectorService.retrieve(question, topK, ownerScope);

        if (retrieval.results().isEmpty()) {
            return RagAnswerResponse.builder()
                    .answer("未找到相关文献，无法回答该问题。")
                    .references(List.of())
                    .build();
        }

        String answer = dashScopeService.chat(question, retrieval.context());

        List<LiteratureResponse> references = retrieval.results().stream()
                .map(SearchResult::getLiterature)
                .collect(Collectors.toList());

        log.info("RAG query completed, found {} relevant literatures", references.size());

        return RagAnswerResponse.builder()
                .answer(answer)
                .references(references)
                .build();
    }
}