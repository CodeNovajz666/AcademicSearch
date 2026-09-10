"""Milvus 向量库访问层：literature_chunks collection 的建/插/搜/删。

collection 结构：
  chunk_id      Int64  主键 auto_id
  literature_id Int64
  chunk_index   Int64
  chunk_text    VarChar(2048)
  embedding     FloatVector(dim=1536)  HNSW + COSINE
"""
from functools import lru_cache

from pymilvus import DataType, MilvusClient

import config

COLLECTION = "literature_chunks"
EMBEDDING_DIM = 1536

_client: MilvusClient | None = None


def client() -> MilvusClient:
    global _client
    if _client is None:
        _client = MilvusClient(uri=config.MILVUS_URI)
    return _client


def ensure_collection() -> None:
    if client().has_collection(COLLECTION):
        return
    schema = client().create_schema(auto_id=True, enable_dynamic_field=False)
    schema.add_field("chunk_id", DataType.INT64, is_primary=True)
    schema.add_field("literature_id", DataType.INT64)
    schema.add_field("chunk_index", DataType.INT64)
    schema.add_field("chunk_text", DataType.VARCHAR, max_length=2048)
    schema.add_field("embedding", DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM)
    index_params = client().prepare_index_params()
    index_params.add_index(
        field_name="embedding",
        index_type="HNSW",
        metric_type="COSINE",
        params={"M": 16, "efConstruction": 200},
    )
    client().create_collection(
        COLLECTION, schema=schema, index_params=index_params
    )


def insert_chunks(literature_id: int, chunks: list[str], vectors: list[list[float]]) -> int:
    if not chunks:
        return 0
    ensure_collection()
    rows = [
        {
            "literature_id": literature_id,
            "chunk_index": i,
            "chunk_text": text[:2048],
            "embedding": vec,
        }
        for i, (text, vec) in enumerate(zip(chunks, vectors))
    ]
    result = client().insert(COLLECTION, data=rows)
    return result.get("insert_count", len(rows)) if isinstance(result, dict) else len(rows)


def search(query_vector: list[float], top_k: int = 5) -> list[dict]:
    """向量检索：返回 [{literatureId, chunkText, score}]。"""
    ensure_collection()
    if client().get_collection_stats(COLLECTION).get("row_count", 0) == 0:
        return []
    results = client().search(
        collection_name=COLLECTION,
        data=[query_vector],
        limit=max(1, min(top_k, 50)),
        output_fields=["literature_id", "chunk_text"],
        search_params={"metric_type": "COSINE", "params": {"ef": 64}},
    )
    hits = []
    for hit in results[0]:
        entity = hit.get("entity", {})
        hits.append(
            {
                "literatureId": entity.get("literature_id"),
                "chunkText": entity.get("chunk_text", ""),
                "score": float(hit.get("distance", 0.0)),
            }
        )
    return hits


def delete_by_literature(literature_id: int) -> None:
    if not client().has_collection(COLLECTION):
        return
    client().delete(COLLECTION, filter=f"literature_id == {int(literature_id)}")
