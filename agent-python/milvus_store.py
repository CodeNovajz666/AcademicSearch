"""Milvus 向量库访问层：literature_chunks collection 的建/插/搜/删。

<<<<<<< HEAD
collection 结构（v2，带可见性标量）：
=======
collection 结构：
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
  chunk_id      Int64  主键 auto_id
  literature_id Int64
  chunk_index   Int64
  chunk_text    VarChar(2048)
<<<<<<< HEAD
  user_id       Int64          （属主；历史无属主为 0）
  is_public     Bool           （是否社区公开；历史 NULL 固化为 True）
  embedding     FloatVector(dim=config.EMBEDDING_DIM)  HNSW + COSINE

存量 v1 collection（无标量字段）自动探测并回退 id 列表过滤；
运行 migrate_vectors_v2.py 重建后自动切回标量过滤。
=======
  embedding     FloatVector(dim=1536)  HNSW + COSINE
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
"""
from functools import lru_cache

from pymilvus import DataType, MilvusClient

import config

COLLECTION = "literature_chunks"
<<<<<<< HEAD

_client: MilvusClient | None = None
# None=未探测；True/False=collection 是否带标量字段（v2/v1）
_scalar_supported: bool | None = None
=======
EMBEDDING_DIM = 1536

_client: MilvusClient | None = None
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4


def client() -> MilvusClient:
    global _client
    if _client is None:
        _client = MilvusClient(uri=config.MILVUS_URI)
    return _client


<<<<<<< HEAD
def scalar_supported() -> bool:
    """collection 是否支持 user_id/is_public 标量过滤（惰性探测并缓存）。"""
    ensure_collection()
    return bool(_scalar_supported)


def _probe_scalar() -> None:
    """存量 collection 探测：查一条 is_public == true，表达式非法（无该字段）即视为 v1。"""
    global _scalar_supported
    try:
        client().query(
            COLLECTION, filter="is_public == true", output_fields=["chunk_id"], limit=1
        )
        _scalar_supported = True
    except Exception:
        _scalar_supported = False
        print(
            f"[milvus] collection {COLLECTION} 无标量字段（v1），检索回退 id 列表过滤；"
            "请运行 migrate_vectors_v2.py 重建"
        )


def ensure_collection() -> None:
    global _scalar_supported
    if _scalar_supported is not None:
        return
    if client().has_collection(COLLECTION):
        _probe_scalar()
=======
def ensure_collection() -> None:
    if client().has_collection(COLLECTION):
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        return
    schema = client().create_schema(auto_id=True, enable_dynamic_field=False)
    schema.add_field("chunk_id", DataType.INT64, is_primary=True)
    schema.add_field("literature_id", DataType.INT64)
    schema.add_field("chunk_index", DataType.INT64)
    schema.add_field("chunk_text", DataType.VARCHAR, max_length=2048)
<<<<<<< HEAD
    # v2 标量字段：可见性/属主直接在 Milvus 内过滤，替代"MySQL 全量 id 拼长表达式"
    schema.add_field("user_id", DataType.INT64)
    schema.add_field("is_public", DataType.BOOL)
    schema.add_field("embedding", DataType.FLOAT_VECTOR, dim=config.EMBEDDING_DIM)
=======
    schema.add_field("embedding", DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM)
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
<<<<<<< HEAD
    _scalar_supported = True


def drop_collection() -> None:
    """删除 literature_chunks collection 并复位探测状态（迁移脚本重建前调用）。"""
    global _scalar_supported
    if client().has_collection(COLLECTION):
        client().drop_collection(COLLECTION)
    _scalar_supported = None


def insert_chunks(
    literature_id: int,
    chunks: list[str],
    vectors: list[list[float]],
    user_id: int | None = None,
    is_public: bool | None = None,
) -> int:
    if not chunks:
        return 0
    ensure_collection()
    with_scalars = _scalar_supported and user_id is not None
=======


def insert_chunks(literature_id: int, chunks: list[str], vectors: list[list[float]]) -> int:
    if not chunks:
        return 0
    ensure_collection()
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    rows = [
        {
            "literature_id": literature_id,
            "chunk_index": i,
            "chunk_text": text[:2048],
<<<<<<< HEAD
            **(
                {"user_id": int(user_id), "is_public": bool(is_public)}
                if with_scalars
                else {}
            ),
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
            "embedding": vec,
        }
        for i, (text, vec) in enumerate(zip(chunks, vectors))
    ]
    result = client().insert(COLLECTION, data=rows)
    return result.get("insert_count", len(rows)) if isinstance(result, dict) else len(rows)


<<<<<<< HEAD
def sync_visibility(literature_id: int, user_id: int | None, is_public: bool) -> bool:
    """可见性变更同步：向量整体取出按新标量重插（不重新 embedding）。

    v1 collection 或该文献尚未建索引时返回 False（空操作）。
    """
    ensure_collection()
    if not _scalar_supported:
        return False
    rows = client().query(
        COLLECTION,
        filter=f"literature_id == {int(literature_id)}",
        output_fields=["chunk_index", "chunk_text", "embedding"],
        limit=16384,
    )
    if not rows:
        return False
    rows.sort(key=lambda r: r.get("chunk_index", 0))
    texts = [r.get("chunk_text", "") for r in rows]
    vectors = [r.get("embedding") for r in rows]
    client().delete(COLLECTION, filter=f"literature_id == {int(literature_id)}")
    insert_chunks(literature_id, texts, vectors, user_id, is_public)
    return True


def search(query_vector: list[float], top_k: int = 5, filter_expr: str | None = None) -> list[dict]:
    """向量检索：返回 [{literatureId, chunkText, score}]。

    filter_expr 非空时启用标量过滤（如 "literature_id in [4,6]"），
    用于"我的知识库"私有范围检索——无需改 collection schema、无需重建向量。
    """
    ensure_collection()
    if client().get_collection_stats(COLLECTION).get("row_count", 0) == 0:
        return []
    kwargs = dict(
=======
def search(query_vector: list[float], top_k: int = 5) -> list[dict]:
    """向量检索：返回 [{literatureId, chunkText, score}]。"""
    ensure_collection()
    if client().get_collection_stats(COLLECTION).get("row_count", 0) == 0:
        return []
    results = client().search(
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        collection_name=COLLECTION,
        data=[query_vector],
        limit=max(1, min(top_k, 50)),
        output_fields=["literature_id", "chunk_text"],
        search_params={"metric_type": "COSINE", "params": {"ef": 64}},
    )
<<<<<<< HEAD
    if filter_expr:
        kwargs["filter"] = filter_expr
    results = client().search(**kwargs)
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
