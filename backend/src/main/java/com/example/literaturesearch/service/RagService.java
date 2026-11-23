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
<<<<<<< HEAD
        return query(question, topK, null);
    }

    /** ownerScope 非空 = 只在"我的知识库"范围内检索。 */
    public RagAnswerResponse query(String question, int topK, Long ownerScope) {
        VectorService.RetrievalResult retrieval = vectorService.retrieve(question, topK, ownerScope);

        if (retrieval.results().isEmpty()) {
=======
        List<SearchResult> searchResults = vectorService.search(question, topK);
        
        if (searchResults.isEmpty()) {
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
            return RagAnswerResponse.builder()
                    .answer("未找到相关文献，无法回答该问题。")
                    .references(List.of())
                    .build();
        }

<<<<<<< HEAD
        String answer = dashScopeService.chat(question, retrieval.context());

        List<LiteratureResponse> references = retrieval.results().stream()
=======
        List<Long> literatureIds = searchResults.stream()
                .map(r -> r.getLiterature().getId())
                .collect(Collectors.toList());

        String context = vectorService.getContextForLiterature(literatureIds);
        String answer = dashScopeService.chat(question, context);

        List<LiteratureResponse> references = searchResults.stream()
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
                .map(SearchResult::getLiterature)
                .collect(Collectors.toList());

        log.info("RAG query completed, found {} relevant literatures", references.size());
<<<<<<< HEAD

=======
        
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        return RagAnswerResponse.builder()
                .answer(answer)
                .references(references)
                .build();
    }
}