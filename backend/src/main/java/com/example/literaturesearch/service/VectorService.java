package com.example.literaturesearch.service;

import com.example.literaturesearch.dto.response.SearchResult;
import com.example.literaturesearch.entity.Literature;
import com.example.literaturesearch.entity.VectorIndex;
import com.example.literaturesearch.repository.LiteratureRepository;
import com.example.literaturesearch.repository.VectorIndexRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    private final VectorIndexRepository vectorIndexRepository;
    private final LiteratureRepository literatureRepository;
    private final DashScopeService dashScopeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void createVectorIndex(Long literatureId, String content) {
        List<String> chunks = splitTextIntoChunks(content, 500, 100);
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            List<Double> embedding = dashScopeService.getEmbedding(chunk);
            
            String vectorData;
            try {
                vectorData = objectMapper.writeValueAsString(embedding);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize vector", e);
            }

            VectorIndex vectorIndex = VectorIndex.builder()
                    .literatureId(literatureId)
                    .vectorData(vectorData)
                    .chunkText(chunk)
                    .chunkIndex(i)
                    .build();

            vectorIndexRepository.save(vectorIndex);
        }
        
        log.info("Created {} vector indices for literature {}", chunks.size(), literatureId);
    }

    private List<String> splitTextIntoChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        text = text.trim();
        int start = 0;
        int end = Math.min(chunkSize, text.length());

        while (start < text.length()) {
            String chunk = text.substring(start, end);
            
            if (end < text.length() && !chunk.endsWith("。") && !chunk.endsWith("！") && !chunk.endsWith("？") && !chunk.endsWith(";") && !chunk.endsWith("。")) {
                int lastPeriod = chunk.lastIndexOf("。");
                int lastQuestion = chunk.lastIndexOf("？");
                int lastExclaim = chunk.lastIndexOf("！");
                int lastSemicolon = chunk.lastIndexOf(";");
                int lastBreak = Math.max(Math.max(lastPeriod, lastQuestion), Math.max(lastExclaim, lastSemicolon));
                
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

    public List<SearchResult> search(String query, int topK) {
        List<Double> queryVector = dashScopeService.getEmbedding(query);
        
        List<VectorIndex> allVectors = vectorIndexRepository.findAll();
        if (allVectors.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<VectorIndex, Double>> similarities = new ArrayList<>();
        for (VectorIndex vectorIndex : allVectors) {
            List<Double> vector = parseVector(vectorIndex.getVectorData());
            double similarity = cosineSimilarity(queryVector, vector);
            similarities.add(new AbstractMap.SimpleEntry<>(vectorIndex, similarity));
        }

        similarities.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        
        int limit = Math.min(topK, similarities.size());
        Set<Long> seenLiteratureIds = new HashSet<>();
        List<SearchResult> results = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            Map.Entry<VectorIndex, Double> entry = similarities.get(i);
            VectorIndex vectorIndex = entry.getKey();
            Double similarity = entry.getValue();

            if (!seenLiteratureIds.contains(vectorIndex.getLiteratureId())) {
                seenLiteratureIds.add(vectorIndex.getLiteratureId());
                
                Literature literature = literatureRepository.findById(vectorIndex.getLiteratureId())
                        .orElse(null);
                
                if (literature != null) {
                    SearchResult result = SearchResult.builder()
                            .literature(com.example.literaturesearch.dto.response.LiteratureResponse.fromEntity(literature))
                            .similarity(similarity)
                            .snippet(truncateText(vectorIndex.getChunkText(), 100))
                            .build();
                    results.add(result);
                }
            }
        }

        return results;
    }

    private List<Double> parseVector(String vectorData) {
        try {
            return objectMapper.readValue(vectorData, new TypeReference<List<Double>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse vector data", e);
        }
    }

    private double cosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1 == null || vec2 == null || vec1.size() != vec2.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
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

    public List<VectorIndex> getVectorsByLiteratureId(Long literatureId) {
        return vectorIndexRepository.findByLiteratureId(literatureId);
    }

    @Transactional
    public void deleteVectorsByLiteratureId(Long literatureId) {
        vectorIndexRepository.deleteByLiteratureId(literatureId);
    }

    public String getContextForLiterature(List<Long> literatureIds) {
        List<Literature> literatures = literatureRepository.findAllById(literatureIds);
        return literatures.stream()
                .map(l -> "标题: " + l.getTitle() + "\n摘要: " + (l.getAbstractText() != null ? l.getAbstractText() : "") + "\n内容: " + (l.getContent() != null ? l.getContent().substring(0, Math.min(1000, l.getContent().length())) : ""))
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}