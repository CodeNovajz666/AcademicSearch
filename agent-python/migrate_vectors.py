"""存量数据迁移：MySQL vector_index 表 → Milvus literature_chunks。

用法（需 Milvus 已启动）：
  python migrate_vectors.py [--dry-run]

幂等：按 literature_id 分组，先删 Milvus 中该文献的旧向量再插入。
"""
import argparse
import json

import milvus_store
from db import connect


def fetch_chunks():
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT literature_id, chunk_index, chunk_text, vector_data FROM vector_index ORDER BY literature_id, chunk_index"
            )
            return cur.fetchall()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="只统计不写入")
    args = parser.parse_args()

    rows = fetch_chunks()
    if not rows:
        print("vector_index 表为空，无需迁移")
        return

    by_lit = {}
    for row in rows:
        by_lit.setdefault(row["literature_id"], []).append(row)

    print(f"MySQL vector_index: {len(rows)} chunks, {len(by_lit)} literatures")

    if args.dry_run:
        for lit_id, chunks in sorted(by_lit.items()):
            print(f"  literature {lit_id}: {len(chunks)} chunks")
        print("(dry-run 结束，未写入 Milvus)")
        return

    milvus_store.ensure_collection()
    for lit_id, chunks in sorted(by_lit.items()):
        milvus_store.delete_by_literature(lit_id)
        inserted = milvus_store.insert_chunks(
            lit_id,
            [c["chunk_text"] for c in chunks],
            [json.loads(c["vector_data"]) for c in chunks],
        )
        print(f"  literature {lit_id}: {inserted}/{len(chunks)} chunks migrated")

    stats = milvus_store.client().get_collection_stats(milvus_store.COLLECTION)
    print(f"done. Milvus row_count = {stats.get('row_count')}")


if __name__ == "__main__":
    main()
