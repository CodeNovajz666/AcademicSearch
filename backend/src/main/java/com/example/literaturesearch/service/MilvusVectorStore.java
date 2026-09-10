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
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量库访问层：literature_chunks collection 的建/插/搜/删。
 * 结构与 agent-python/milvus_store.py 保持一致（embedding 维度 1536）。
 * 通过 ObjectProvider 惰性获取客户端：Milvus 未启动时后端仍可正常启动，
 * 仅向量相关操作会返回明确错误。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MilvusVectorStore {

    public static final String COLLECTION = "literature_chunks";
    private static final int EMBEDDING_DIM = 1536;

    private final ObjectProvider<MilvusClientV2> clientProvider;

    private MilvusClientV2 client() {
        return clientProvider.getObject();
    }

    public synchronized void ensureCollection() {
        if (client().hasCollection(HasCollectionReq.builder()
                .collectionName(COLLECTION).build())) {
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
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding").dataType(DataType.FloatVector)
                .dimension(EMBEDDING_DIM).build());

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
        log.info("Created Milvus collection {} (dim={})", COLLECTION, EMBEDDING_DIM);
    }

    public void insertChunks(Long literatureId, List<String> chunks, List<List<Double>> vectors) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        ensureCollection();

        List<JsonObject> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("literature_id", literatureId);
            row.addProperty("chunk_index", i);
            row.addProperty("chunk_text", chunks.get(i).length() > 2048
                    ? chunks.get(i).substring(0, 2048) : chunks.get(i));
            com.google.gson.JsonArray vec = new com.google.gson.JsonArray(vectors.get(i).size());
            for (Double d : vectors.get(i)) {
                vec.add(d);
            }
            row.add("embedding", vec);
            rows.add(row);
        }

        client().insert(InsertReq.builder().collectionName(COLLECTION).data(rows).build());
        client().flush(FlushReq.builder().collectionNames(List.of(COLLECTION)).build());
        log.info("Inserted {} chunks into Milvus for literature {}", chunks.size(), literatureId);
    }

    public List<SearchHit> search(List<Double> queryVector, int topK) {
        ensureCollection();
        if (getRowCount() == 0) {
            return List.of();
        }

        SearchResp resp = client().search(SearchReq.builder()
                .collectionName(COLLECTION)
                .annsField("embedding")
                .data(Collections.singletonList(toFloatVec(queryVector)))
                .topK(Math.max(1, Math.min(topK, 50)))
                .outputFields(List.of("literature_id", "chunk_text"))
                .searchParams(Map.of("metric_type", "COSINE", "params", Map.of("ef", 64)))
                .build());

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
