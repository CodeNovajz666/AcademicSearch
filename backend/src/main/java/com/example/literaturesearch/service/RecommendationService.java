package com.example.literaturesearch.service;

import com.example.literaturesearch.dto.response.LiteratureResponse;
import com.example.literaturesearch.dto.response.RecommendationResponse;
import com.example.literaturesearch.entity.Literature;
import com.example.literaturesearch.repository.LiteratureRepository;
import com.example.literaturesearch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 社区推荐：以"我的文献"内容为兴趣画像，在社区公开文献中做向量相似度召回。
 * 实现上复用已有 embedding + Milvus 检索链路，不新增存储结构：
 * 1. 取我最近上传的若干篇文献，用 标题 + 摘要（无摘要时退化为正文开头）构造查询文本；
 * 2. 批量 embedding 后逐篇在"他人公开文献"范围内检索，按文献去重并保留最高分分块；
 * 3. 汇总排序，补充上传者昵称后返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    /** 参与画像的我的文献上限：来源过多会稀释主题，3 篇足以覆盖当前研究方向。 */
    private static final int MAX_SOURCES = 3;
    /** 送入 embedding 的查询文本上限，避免超长正文。 */
    private static final int MAX_QUERY_CHARS = 1500;
    /** 分块级召回的放大倍数：最终要按文献去重，需多召回候选。 */
    private static final int RECALL_MULTIPLIER = 6;

    private final LiteratureRepository literatureRepository;
    private final UserRepository userRepository;
    private final DashScopeService dashScopeService;
    private final MilvusVectorStore milvusVectorStore;

    public RecommendationResponse recommend(Long userId, int topK) {
        int limit = Math.max(1, Math.min(topK, 20));

        List<Literature> mine = literatureRepository.findByUserId(userId).stream()
                .filter(l -> l.getContent() != null && !l.getContent().isBlank())
                .sorted(Comparator.comparing(
                        (Literature l) -> l.getCreatedAt() == null ? LocalDateTime.MIN : l.getCreatedAt())
                        .reversed())
                .limit(MAX_SOURCES)
                .toList();
        if (mine.isEmpty()) {
            return RecommendationResponse.builder().basedOn(List.of()).items(List.of()).build();
        }

        List<String> basedOn = mine.stream().map(Literature::getTitle).toList();

        List<String> queries = mine.stream().map(RecommendationService::buildQueryText).toList();
        List<List<Double>> vectors = dashScopeService.getEmbeddings(queries);

        // 社区候选 = 公开文献中排除自己上传的。
        // v2 schema 直接用 Milvus 标量过滤（恒定长度表达式）；v1 collection 回退 id 列表
        String filter;
        if (milvusVectorStore.isScalarFieldsSupported()) {
            filter = "is_public == true and user_id != " + userId;
        } else {
            List<Long> communityIds = literatureRepository.findByIsPublicTrueOrIsPublicIsNull().stream()
                    .filter(LiteratureService::isPubliclyVisible)
                    .filter(l -> l.getUserId() == null || !l.getUserId().equals(userId))
                    .map(Literature::getId)
                    .toList();
            if (communityIds.isEmpty()) {
                return RecommendationResponse.builder().basedOn(basedOn).items(List.of()).build();
            }
            filter = VectorService.buildLiteratureIdFilter(communityIds);
        }

        // 同一篇社区文献可能被多篇"我的文献"命中：保留最高分及其来源
        Map<Long, Hit> bestByLiterature = new LinkedHashMap<>();
        for (int i = 0; i < mine.size(); i++) {
            Literature source = mine.get(i);
            List<MilvusVectorStore.SearchHit> hits =
                    milvusVectorStore.search(vectors.get(i), limit * RECALL_MULTIPLIER, filter);
            for (MilvusVectorStore.SearchHit hit : hits) {
                Hit current = bestByLiterature.get(hit.literatureId());
                if (current == null || hit.score() > current.score()) {
                    bestByLiterature.put(hit.literatureId(),
                            new Hit(hit.literatureId(), hit.score(), hit.chunkText(), source.getTitle()));
                }
            }
        }

        List<Hit> ranked = bestByLiterature.values().stream()
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(limit)
                .toList();
        if (ranked.isEmpty()) {
            return RecommendationResponse.builder().basedOn(basedOn).items(List.of()).build();
        }

        Map<Long, Literature> byId = new LinkedHashMap<>();
        literatureRepository.findAllById(ranked.stream().map(Hit::literatureId).toList())
                .forEach(l -> byId.put(l.getId(), l));

        List<RecommendationResponse.Item> items = new ArrayList<>();
        for (Hit hit : ranked) {
            Literature literature = byId.get(hit.literatureId());
            if (literature == null || !LiteratureService.isPubliclyVisible(literature)) {
                continue;
            }
            items.add(RecommendationResponse.Item.builder()
                    .literature(LiteratureResponse.fromEntity(literature))
                    .similarity(hit.score())
                    .snippet(truncate(hit.chunkText(), 100))
                    .basedOnTitle(hit.basedOnTitle())
                    .build());
        }
        attachUploaderNames(items);
        log.info("Recommendations for user {}: {} items (based on {})",
                userId, items.size(), basedOn);
        return RecommendationResponse.builder().basedOn(basedOn).items(items).build();
    }

    /** 标题 + 摘要构造查询文本；摘要抽取失败（较常见）时退化为正文开头。 */
    static String buildQueryText(Literature literature) {
        StringBuilder sb = new StringBuilder();
        if (literature.getTitle() != null) {
            sb.append(literature.getTitle());
        }
        String body = literature.getAbstractText();
        if (body == null || body.isBlank()) {
            body = literature.getContent();
        }
        if (body != null && !body.isBlank()) {
            sb.append("\n").append(body);
        }
        String text = sb.toString().trim();
        return text.length() > MAX_QUERY_CHARS ? text.substring(0, MAX_QUERY_CHARS) : text;
    }

    private void attachUploaderNames(List<RecommendationResponse.Item> items) {
        List<Long> ownerIds = items.stream()
                .map(i -> i.getLiterature().getUserId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameByUser = userRepository.findAllById(ownerIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.example.literaturesearch.entity.User::getId,
                        com.example.literaturesearch.entity.User::getUsername));
        for (RecommendationResponse.Item item : items) {
            item.getLiterature().setUploaderName(nameByUser.get(item.getLiterature().getUserId()));
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }

    private record Hit(long literatureId, double score, String chunkText, String basedOnTitle) {
    }
}
