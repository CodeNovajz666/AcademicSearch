package com.example.literaturesearch.service;

import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量库访问层：literature_chunks collection 的建/插/搜/删。
 * 结构与 agent-python/milvus_store.py 保持一致（embedding 维度见 milvus.embedding-dim 配置）。
 * 通过 ObjectProvider 惰性获取客户端：Milvus 未启动时后端仍可正常启动，
 * 仅向量相关操作会返回明确错误。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MilvusVectorStore {

    public static final String COLLECTION = "literature_chunks";

    private final ObjectProvider<MilvusClientV2> clientProvider;

    @Value("${milvus.embedding-dim:1536}")
    private int embeddingDim;

    // 建表检查结果缓存：避免每次检索都向 Milvus 发 hasCollection RPC
    private final java.util.concurrent.atomic.AtomicBoolean collectionReady =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /**
     * collection 是否带 user_id/is_public 标量字段（v2 schema）。
     * 存量 collection 是 v1（无标量字段）：探测失败时回退"MySQL 全量 id 列表"过滤，
     * 跑完 migrate_vectors_v2.py 重建后自动切回标量过滤。
     */
    private final java.util.concurrent.atomic.AtomicBoolean scalarSupported =
            new java.util.concurrent.atomic.AtomicBoolean(true);

    private MilvusClientV2 client() {
        return clientProvider.getObject();
    }

    /** 调用前先确保 collection 已建/探测完成：否则首请求会在探测落地前读到默认 true，拼出 v1 集合无法解析的标量过滤表达式（500）。 */
    public boolean isScalarFieldsSupported() {
        ensureCollection();
        return scalarSupported.get();
    }

    public synchronized void ensureCollection() {
        if (collectionReady.get()) {
            return;
        }
        if (client().hasCollection(HasCollectionReq.builder()
                .collectionName(COLLECTION).build())) {
            collectionReady.set(true);
            probeScalarFields();
            return;
        }

        CreateCollectionReq.CollectionSchema schema =
                CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_id").dataType(DataType.Int64)
                .isPrimaryKey(true).autoID(true).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("literature_id").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_index").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_text").dataType(DataType.VarChar)
                .maxLength(2048).build());
        // v2 标量字段：可见性/属主直接在 Milvus 内过滤，替代"全量 id 拼长表达式"
        schema.addField(AddFieldReq.builder()
                .fieldName("user_id").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("is_public").dataType(DataType.Bool).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding").dataType(DataType.FloatVector)
                .dimension(embeddingDim).build());

        List<IndexParam> indexParams = List.of(IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("M", 16, "efConstruction", 200))
                .build());

        client().createCollection(CreateCollectionReq.builder()
                .collectionName(COLLECTION)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .build());
        collectionReady.set(true);
        scalarSupported.set(true);
        log.info("Created Milvus collection {} (dim={}, with scalar fields)", COLLECTION, embeddingDim);
    }

    /** 存量 collection 探测：查一条 is_public == true，表达式非法（无该字段）即视为 v1。 */
    private void probeScalarFields() {
        try {
            client().query(QueryReq.builder()
                    .collectionName(COLLECTION)
                    .filter("is_public == true")
                    .outputFields(List.of("chunk_id"))
                    .limit(1L)
                    .build());
            scalarSupported.set(true);
        } catch (Exception e) {
            scalarSupported.set(false);
            log.warn("Milvus collection {} 无标量字段（v1），检索回退 id 列表过滤；"
                    + "请运行 agent-python/migrate_vectors_v2.py 重建", COLLECTION);
        }
    }

    /** 可见性变更同步：把该文献向量整体取出、按新标量重插（无需重新 embedding）。 */
    public boolean syncVisibility(Long literatureId, Long userId, boolean isPublic) {
        if (!isScalarFieldsSupported()) {
            return false;
        }
        QueryResp resp = client().query(QueryReq.builder()
                .collectionName(COLLECTION)
                .filter("literature_id == " + literatureId)
                .outputFields(List.of("chunk_index", "chunk_text", "embedding"))
                .limit(16384L)
                .build());
        List<QueryResp.QueryResult> rows = resp.getQueryResults();
        if (rows == null || rows.isEmpty()) {
            return false; // 尚未建索引（处理中）：稍后 insertChunks 会带上最新标量
        }
        List<JsonObject> data = new ArrayList<>(rows.size());
        for (QueryResp.QueryResult row : rows) {
            Map<String, Object> entity = row.getEntity();
            JsonObject o = new JsonObject();
            o.addProperty("literature_id", literatureId);
            o.addProperty("chunk_index", ((Number) entity.get("chunk_index")).longValue());
            o.addProperty("chunk_text", (String) entity.get("chunk_text"));
            o.addProperty("user_id", userId == null ? 0L : userId);
            o.addProperty("is_public", isPublic);
            com.google.gson.JsonArray vec = new com.google.gson.JsonArray();
            for (Object f : (List<?>) entity.get("embedding")) {
                vec.add(((Number) f).floatValue());
            }
            o.add("embedding", vec);
            data.add(o);
        }
        client().delete(DeleteReq.builder()
                .collectionName(COLLECTION)
                .filter("literature_id == " + literatureId)
                .build());
        client().insert(InsertReq.builder().collectionName(COLLECTION).data(data).build());
        client().flush(FlushReq.builder().collectionNames(List.of(COLLECTION)).build());
        log.info("Synced visibility to Milvus: literatureId={} isPublic={}", literatureId, isPublic);
        return true;
    }

    public void insertChunks(Long literatureId, List<String> chunks, List<List<Double>> vectors) {
        insertChunks(literatureId, chunks, vectors, null, null);
    }

    /** 带可见性标量插入（v2 schema）；userId 为 null 或 v1 collection 时省略标量字段。 */
    public void insertChunks(Long literatureId, List<String> chunks, List<List<Double>> vectors,
                             Long userId, Boolean isPublic) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        ensureCollection();
        boolean withScalars = isScalarFieldsSupported() && userId != null;

        List<JsonObject> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("literature_id", literatureId);
            row.addProperty("chunk_index", i);
            row.addProperty("chunk_text", chunks.get(i).length() > 2048
                    ? chunks.get(i).substring(0, 2048) : chunks.get(i));
            if (withScalars) {
                row.addProperty("user_id", userId);
                row.addProperty("is_public", Boolean.TRUE.equals(isPublic));
            }
            com.google.gson.JsonArray vec = new com.google.gson.JsonArray(vectors.get(i).size());
            for (Double d : vectors.get(i)) {
                vec.add(d);
            }
            row.add("embedding", vec);
            rows.add(row);
        }

        client().insert(InsertReq.builder().collectionName(COLLECTION).data(rows).build());
        client().flush(FlushReq.builder().collectionNames(List.of(COLLECTION)).build());
        log.info("Inserted {} chunks into Milvus for literature {} (scalars={})",
                chunks.size(), literatureId, withScalars);
    }

    public List<SearchHit> search(List<Double> queryVector, int topK) {
        return search(queryVector, topK, null);
    }

    /**
     * filterExpr 非空时启用标量过滤（如 "literature_id in [4,6]"），
     * 用于"我的知识库"私有范围检索——无需改 collection schema、无需重建向量。
     */
    public List<SearchHit> search(List<Double> queryVector, int topK, String filterExpr) {
        ensureCollection();
        if (getRowCount() == 0) {
            return List.of();
        }

        SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                .collectionName(COLLECTION)
                .annsField("embedding")
                .data(Collections.singletonList(toFloatVec(queryVector)))
                .topK(Math.max(1, Math.min(topK, 50)))
                .outputFields(List.of("literature_id", "chunk_text"))
                // params 必须是 JSON 字符串：传 Map 会被 SDK toString 成 "{ef=64}" 导致 Milvus 表达式解析失败
                .searchParams(Map.of("metric_type", "COSINE", "params", "{\"ef\": 64}"));
        if (filterExpr != null && !filterExpr.isBlank()) {
            builder.filter(filterExpr);
        }
        SearchResp resp = client().search(builder.build());

        List<SearchHit> hits = new ArrayList<>();
        for (List<SearchResp.SearchResult> results : resp.getSearchResults()) {
            for (SearchResp.SearchResult result : results) {
                Map<String, Object> entity = result.getEntity();
                hits.add(new SearchHit(
                        ((Number) entity.get("literature_id")).longValue(),
                        (String) entity.get("chunk_text"),
                        result.getScore()));
            }
        }
        return hits;
    }

    public void deleteByLiteratureId(Long literatureId) {
        if (!client().hasCollection(HasCollectionReq.builder()
                .collectionName(COLLECTION).build())) {
            return;
        }
        client().delete(DeleteReq.builder()
                .collectionName(COLLECTION)
                .filter("literature_id == " + literatureId)
                .build());
        log.info("Deleted Milvus vectors of literature {}", literatureId);
    }

    public long getRowCount() {
        return client().getCollectionStats(GetCollectionStatsReq.builder()
                .collectionName(COLLECTION).build())
                .getNumOfEntities();
    }

    public long countByLiteratureId(Long literatureId) {
        ensureCollection();
        QueryResp resp = client().query(QueryReq.builder()
                .collectionName(COLLECTION)
                .filter("literature_id == " + literatureId)
                .outputFields(List.of("chunk_id"))
                .limit(16384L)
                .build());
        return resp.getQueryResults() == null ? 0 : resp.getQueryResults().size();
    }

    private static FloatVec toFloatVec(List<Double> vector) {
        float[] arr = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            arr[i] = vector.get(i).floatValue();
        }
        return new FloatVec(arr);
    }

    public record SearchHit(long literatureId, String chunkText, double score) {
    }
}
