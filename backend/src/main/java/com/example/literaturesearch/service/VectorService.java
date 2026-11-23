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
    private final com.example.literaturesearch.repository.UserRepository userRepository;
    private final DashScopeService dashScopeService;

    public void createVectorIndex(Literature literature, String content) {
        List<String> chunks = splitTextIntoChunks(content, 500, 100);
        if (chunks.isEmpty()) {
            log.info("No chunks to index for literature {}", literature.getId());
            return;
        }

        List<List<Double>> embeddings = dashScopeService.getEmbeddings(chunks);
        // 插入时带上属主/可见性标量（v2 schema）：Milvus 内直接过滤，无需拼长 id 列表
        milvusVectorStore.insertChunks(literature.getId(), chunks, embeddings,
                literature.getUserId(), LiteratureService.isPubliclyVisible(literature));

        log.info("Created {} vector indices for literature {}", chunks.size(), literature.getId());
    }

    /** 可见性变更后同步 Milvus 标量字段（不重新 embedding；v1 collection 或未建索引时为空操作）。 */
    public void syncVisibility(Long literatureId, Long userId, boolean isPublic) {
        milvusVectorStore.syncVisibility(literatureId, userId, isPublic);
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

    /**
     * 检索 + RAG 上下文一体化的结果：results 为去重后的 topK 文献，
     * context 由每篇文献命中分数最高的分块（而非正文开头）拼成，
     * 保证“检索依据什么、模型就看到什么”。
     */
    public record RetrievalResult(List<SearchResult> results, String context) {}

    public List<SearchResult> search(String query, int topK) {
        return retrieve(query, topK, null).results();
    }

    /** ownerScope 非空 = "我的知识库"私有范围检索。 */
    public List<SearchResult> search(String query, int topK, Long ownerScope) {
        return retrieve(query, topK, ownerScope).results();
    }

    public RetrievalResult retrieve(String query, int topK) {
        return retrieve(query, topK, null);
    }

    public RetrievalResult retrieve(String query, int topK, Long ownerScope) {
        List<Double> queryVector = dashScopeService.getEmbedding(query);

        // 范围过滤：v2 schema 直接用 Milvus 标量字段（表达式恒定长度，规模无关）；
        // 存量 v1 collection（无标量字段）回退"MySQL 全量 id 列表"拼表达式
        String filterExpr;
        if (ownerScope != null) {
            if (milvusVectorStore.isScalarFieldsSupported()) {
                filterExpr = "user_id == " + ownerScope;
            } else {
                List<Long> myIds = literatureRepository.findByUserId(ownerScope).stream()
                        .map(Literature::getId)
                        .toList();
                if (myIds.isEmpty()) {
                    return new RetrievalResult(List.of(), "");
                }
                filterExpr = buildLiteratureIdFilter(myIds);
            }
        } else {
            // 总知识库 = 仅社区公开贡献的文献（历史 NULL 在迁移时固化为 true）
            if (milvusVectorStore.isScalarFieldsSupported()) {
                filterExpr = "is_public == true";
            } else {
                List<Long> publicIds = literatureRepository.findByIsPublicTrueOrIsPublicIsNull().stream()
                        .filter(l -> !Boolean.FALSE.equals(l.getIsPublic()))
                        .map(Literature::getId)
                        .toList();
                if (publicIds.isEmpty()) {
                    return new RetrievalResult(List.of(), "");
                }
                filterExpr = buildLiteratureIdFilter(publicIds);
            }
        }

        // Milvus HNSW 返回文本块级命中（按分数降序），按文献分组后：
        // 最高分块参与排序，其余分块留作 RAG 上下文素材
        Map<Long, List<MilvusVectorStore.SearchHit>> hitsByLiterature = new java.util.LinkedHashMap<>();
        for (MilvusVectorStore.SearchHit hit : milvusVectorStore.search(queryVector, topK * 4, filterExpr)) {
            hitsByLiterature.computeIfAbsent(hit.literatureId(), k -> new ArrayList<>()).add(hit);
        }
        hitsByLiterature.values().forEach(hits ->
                hits.sort((a, b) -> Double.compare(b.score(), a.score())));

        List<MilvusVectorStore.SearchHit> ranked = hitsByLiterature.values().stream()
                .map(hits -> hits.get(0))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();

        List<SearchResult> results = new ArrayList<>();
        Map<Long, Literature> contextLiteratures = new java.util.LinkedHashMap<>();
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
            contextLiteratures.put(literature.getId(), literature);
        }

        attachUploaderNames(results);
        return new RetrievalResult(results, buildContext(contextLiteratures, hitsByLiterature));
    }

    /** Milvus 标量过滤表达式：literature_id in [1,2,3]（单条无括号歧义也兼容此写法）。 */
    static String buildLiteratureIdFilter(List<Long> ids) {
        return "literature_id in [" + ids.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    private void attachUploaderNames(List<SearchResult> results) {
        List<Long> ownerIds = results.stream()
                .map(r -> r.getLiterature().getUserId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameByUser = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(
                        com.example.literaturesearch.entity.User::getId,
                        com.example.literaturesearch.entity.User::getUsername));
        for (SearchResult r : results) {
            r.getLiterature().setUploaderName(nameByUser.get(r.getLiterature().getUserId()));
        }
    }

    /** 每篇文献取命中分数最高的两个分块作为正文依据，拼上标题/作者/摘要。 */
    private String buildContext(Map<Long, Literature> literatures,
                                Map<Long, List<MilvusVectorStore.SearchHit>> hitsByLiterature) {
        StringBuilder context = new StringBuilder();
        for (Map.Entry<Long, Literature> entry : literatures.entrySet()) {
            Literature l = entry.getValue();
            if (context.length() > 0) {
                context.append("\n\n---\n\n");
            }
            context.append("标题: ").append(l.getTitle())
                    .append("\n作者: ").append(l.getAuthors() != null ? l.getAuthors() : "")
                    .append("\n摘要: ").append(l.getAbstractText() != null ? l.getAbstractText() : "");
            List<MilvusVectorStore.SearchHit> hits = hitsByLiterature.getOrDefault(entry.getKey(), List.of());
            int appended = 0;
            for (MilvusVectorStore.SearchHit hit : hits) {
                if (appended >= 2) {
                    break;
                }
                context.append("\n相关片段: ").append(truncateText(hit.chunkText(), 800));
                appended++;
            }
        }
        return context.toString();
    }

    public void deleteVectorsByLiteratureId(Long literatureId) {
        milvusVectorStore.deleteByLiteratureId(literatureId);
    }

    public long countByLiteratureId(Long literatureId) {
        return milvusVectorStore.countByLiteratureId(literatureId);
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
