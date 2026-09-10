package com.example.literaturesearch.service;

import com.example.literaturesearch.dto.response.LiteratureResponse;
import com.example.literaturesearch.dto.response.SearchResult;
import com.example.literaturesearch.entity.Literature;
import com.example.literaturesearch.repository.LiteratureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    private final MilvusVectorStore milvusVectorStore;
    private final LiteratureRepository literatureRepository;
    private final DashScopeService dashScopeService;

    public void createVectorIndex(Long literatureId, String content) {
        List<String> chunks = splitTextIntoChunks(content, 500, 100);
        if (chunks.isEmpty()) {
            log.info("No chunks to index for literature {}", literatureId);
            return;
        }

        List<List<Double>> embeddings = dashScopeService.getEmbeddings(chunks);
        milvusVectorStore.insertChunks(literatureId, chunks, embeddings);

        log.info("Created {} vector indices for literature {}", chunks.size(), literatureId);
    }

    List<String> splitTextIntoChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        text = text.trim();
        int start = 0;
        int end = Math.min(chunkSize, text.length());

        while (start < text.length()) {
            String chunk = text.substring(start, end);

            if (end < text.length() && !endsWithSentenceBoundary(chunk)) {
                int lastBreak = lastSentenceBoundary(chunk);

                if (lastBreak > chunkSize / 2) {
                    end = start + lastBreak + 1;
                    chunk = text.substring(start, end);
                }
            }

            chunks.add(chunk);

            if (end >= text.length()) {
                break;
            }

            start = end - overlap;
            if (start < 0) start = 0;
            end = Math.min(start + chunkSize, text.length());
        }

        return chunks;
    }

    private static boolean endsWithSentenceBoundary(String chunk) {
        return !chunk.isEmpty() && "。！？!?;；.".indexOf(chunk.charAt(chunk.length() - 1)) >= 0;
    }

    private static int lastSentenceBoundary(String chunk) {
        int lastBreak = -1;
        for (int i = 0; i < chunk.length(); i++) {
            if ("。！？!?;；.".indexOf(chunk.charAt(i)) >= 0) {
                lastBreak = i;
            }
        }
        return lastBreak;
    }

    public List<SearchResult> search(String query, int topK) {
        List<Double> queryVector = dashScopeService.getEmbedding(query);

        // Milvus HNSW 检索返回的是文本块级命中，按文献去重保留最高分块
        Map<Long, MilvusVectorStore.SearchHit> bestByLiterature = new java.util.LinkedHashMap<>();
        for (MilvusVectorStore.SearchHit hit : milvusVectorStore.search(queryVector, topK * 4)) {
            bestByLiterature.merge(hit.literatureId(), hit,
                    (a, b) -> b.score() > a.score() ? b : a);
        }

        List<MilvusVectorStore.SearchHit> ranked = new ArrayList<>(bestByLiterature.values());
        ranked.sort((a, b) -> Double.compare(b.score(), a.score()));

        List<SearchResult> results = new ArrayList<>();
        for (MilvusVectorStore.SearchHit candidate : ranked) {
            if (results.size() >= topK) {
                break;
            }
            Literature literature = literatureRepository.findById(candidate.literatureId())
                    .orElse(null);
            if (literature == null) {
                continue;
            }
            results.add(SearchResult.builder()
                    .literature(LiteratureResponse.fromEntity(literature))
                    .similarity(candidate.score())
                    .snippet(truncateText(candidate.chunkText(), 100))
                    .build());
        }

        return results;
    }

    public void deleteVectorsByLiteratureId(Long literatureId) {
        milvusVectorStore.deleteByLiteratureId(literatureId);
    }

    public String getContextForLiterature(List<Long> literatureIds) {
        List<Literature> literatures = literatureRepository.findAllById(literatureIds);
        return literatures.stream()
                .map(l -> "标题: " + l.getTitle() + "\n摘要: " + (l.getAbstractText() != null ? l.getAbstractText() : "") + "\n内容: " + (l.getContent() != null ? l.getContent().substring(0, Math.min(1000, l.getContent().length())) : ""))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
