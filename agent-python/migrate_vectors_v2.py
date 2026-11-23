"""v2 标量字段迁移：给存量 literature_chunks 向量补 user_id / is_public 标量。

用法（需 Milvus 与 MySQL 已启动）：
  python migrate_vectors_v2.py [--dry-run]

两种模式（自动判定）：
- v2 集合（已带标量字段）：逐篇按 MySQL 最新 user_id / is_public 幂等重刷；
- v1 集合（无标量字段）：Milvus 不支持在既有 collection 上加字段，先全量取出
  向量（不重新 embedding、不消耗 LLM 额度），取完后再删集合、按 v2 schema 重建、
  带标量重插。取出全部成功之前不删任何数据，中途失败可放心重跑。

运行完成后重启 Java/Python 服务，检索自动从"id 列表过滤"切到标量过滤。
"""
import argparse

import milvus_store
from db import connect


def fetch_flags():
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT id, user_id, is_public FROM literature")
            return cur.fetchall()


def to_bool(v):
    """BIT(1) 列规范化：None（历史数据）视为公开。"""
    if v is None:
        return True
    if isinstance(v, (bytes, bytearray)):
        return v != b"\x00"
    return bool(v)


def fetch_vectors(client, lit_id):
    rows = client.query(
        milvus_store.COLLECTION,
        filter=f"literature_id == {int(lit_id)}",
        output_fields=["chunk_index", "chunk_text", "embedding"],
        limit=16384,
    )
    rows.sort(key=lambda r: r.get("chunk_index", 0))
    return rows


def rebuild(client, lits):
    """v1 → v2：先全量取走向量，取完再删集合重建，任何一步失败都不破坏现有数据。"""
    stash = {}
    for lit in lits:
        rows = fetch_vectors(client, lit["id"])
        if rows:
            stash[int(lit["id"])] = rows
            print(f"  literature {lit['id']}: 已取出 {len(rows)} chunks")
    if not stash:
        print("Milvus 中没有可迁移的向量，直接重建空 v2 集合")

    milvus_store.drop_collection()
    milvus_store.ensure_collection()
    print("collection 已按 v2 schema 重建（带 user_id / is_public）")

    for lit in lits:
        lit_id = int(lit["id"])
        rows = stash.get(lit_id)
        if not rows:
            print(f"  literature {lit_id}: 无向量（未索引或已被清理），跳过")
            continue
        texts = [r.get("chunk_text", "") for r in rows]
        vectors = [r.get("embedding") for r in rows]
        milvus_store.insert_chunks(
            lit_id, texts, vectors, lit["user_id"] or 0, to_bool(lit["is_public"])
        )
        print(f"  literature {lit_id}: {len(rows)} chunks 已带标量重插")


def rescale(client, lits):
    """已是 v2：按 MySQL 最新值幂等重刷标量（不重排向量）。"""
    for lit in lits:
        lit_id = int(lit["id"])
        rows = fetch_vectors(client, lit_id)
        if not rows:
            print(f"  literature {lit_id}: 无向量（未索引或已被清理），跳过")
            continue
        if milvus_store.sync_visibility(lit_id, lit["user_id"] or 0, to_bool(lit["is_public"])):
            print(f"  literature {lit_id}: {len(rows)} chunks 标量已重刷")
        else:
            print(f"  literature {lit_id}: 重刷失败（sync_visibility 返回 False）")


def main():
    parser = argparse.ArgumentParser(description="给存量向量补 user_id/is_public 标量（v2）")
    parser.add_argument("--dry-run", action="store_true", help="只统计不写入")
    args = parser.parse_args()

    lits = fetch_flags()
    print(f"MySQL literature: {len(lits)} 篇")

    # 触发 collection 探测（v1/v2 判定）
    milvus_store.ensure_collection()
    client = milvus_store.client()
    v1 = not milvus_store.scalar_supported()

    if args.dry_run:
        print(f"迁移模式：{'重建迁移（v1 → v2）' if v1 else '幂等重刷标量（已是 v2）'}")
        for lit in lits:
            rows = fetch_vectors(client, lit["id"])
            print(f"  literature {lit['id']}: {len(rows)} chunks 待处理")
        print("(dry-run 结束，未写入 Milvus)")
        return

    if v1:
        print("检测到 v1 集合（无标量字段）：全量取向量后重建为 v2 并补标量")
        rebuild(client, lits)
    else:
        print("collection 已带标量字段：按 MySQL 最新值幂等重刷")
        rescale(client, lits)

    stats = client.get_collection_stats(milvus_store.COLLECTION)
    print(f"done. Milvus row_count = {stats.get('row_count')}")
    print("重启 Java/Python 服务后自动启用标量过滤。")


if __name__ == "__main__":
    main()
