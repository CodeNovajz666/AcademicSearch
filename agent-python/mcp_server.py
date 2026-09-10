"""唯一的 MCP Server：文献检索/精读工具 + 翻译/总结技能 + finalize 信号。

以 stdio 方式运行，由 FastAPI 主服务作为 MCP Client 拉起并通过 MCP 协议消费。
工具实现直连 MySQL（example_db）与 DashScope。

启动：python mcp_server.py（通常由 main.py 自动拉起，无需手动运行）
"""
import json

from mcp.server.fastmcp import FastMCP

import db
import llm
import milvus_store

mcp = FastMCP("literature-agent-tools")

MAX_TOOL_RESULT_CHARS = 4000


def _truncate(text, limit=MAX_TOOL_RESULT_CHARS):
    text = text or ""
    if len(text) <= limit:
        return text
    return text[:limit] + f"\n...(结果过长，已截断，原始长度 {len(text)} 字符)"


@mcp.tool()
def semantic_search(query: str, top_k: int = 5) -> str:
    """语义检索文献库：根据研究问题/关键词做向量相似度检索（Milvus HNSW），返回最相关的文献（含标题、作者、摘要、匹配文本片段与相似度分数）。这是回答文献类问题的首选工具。"""
    if not query or not query.strip():
        return json.dumps({"error": "query 不能为空"}, ensure_ascii=False)
    top_k = max(1, min(int(top_k), 10))
    query_vec = llm.embed([query.strip()])[0]
    hits = milvus_store.search(query_vec, top_k)
    results = []
    for hit in hits:
        lit = db.query_one(
            "SELECT id, title, authors, abstract_text, journal, year FROM literature WHERE id = %s",
            (hit["literatureId"],),
        )
        if not lit:
            continue
        results.append(
            {
                "literatureId": lit["id"],
                "title": lit["title"],
                "authors": lit["authors"],
                "abstract": _truncate(lit["abstract_text"] or "", 300),
                "journal": lit["journal"],
                "year": lit["year"],
                "score": round(hit["score"], 4),
                "matchedText": _truncate(hit["chunkText"], 600),
            }
        )
    return _truncate(json.dumps({"results": results}, ensure_ascii=False, default=str))


@mcp.tool()
def list_literatures(keyword: str = "") -> str:
    """列出文献库中的文献（可按标题/作者关键词过滤），返回 id、标题、作者、年份、期刊。用于了解库里有什么、或确定要精读哪篇。"""
    if keyword and keyword.strip():
        pattern = f"%{keyword.strip()}%"
        rows = db.query(
            "SELECT id, title, authors, journal, year, created_at FROM literature "
            "WHERE title LIKE %s OR authors LIKE %s ORDER BY created_at DESC LIMIT 50",
            (pattern, pattern),
        )
    else:
        rows = db.query(
            "SELECT id, title, authors, journal, year, created_at FROM literature "
            "ORDER BY created_at DESC LIMIT 50"
        )
    items = [
        {
            "id": r["id"],
            "title": r["title"],
            "authors": r["authors"],
            "journal": r["journal"],
            "year": r["year"],
        }
        for r in rows
    ]
    return _truncate(
        json.dumps({"total": len(items), "literatures": items}, ensure_ascii=False, default=str)
    )


@mcp.tool()
def get_literature(literature_id: int) -> str:
    """获取一篇文献的完整元数据与摘要：标题、作者、期刊、年份、DOI、摘要全文。适合在检索后确认文献身份、引用其摘要内容。"""
    lit = db.query_one(
        "SELECT id, title, authors, abstract_text, journal, year, doi, created_at "
        "FROM literature WHERE id = %s",
        (int(literature_id),),
    )
    if not lit:
        return json.dumps({"error": f"文献不存在: {literature_id}"}, ensure_ascii=False)
    return _truncate(json.dumps(lit, ensure_ascii=False, default=str))


@mcp.tool()
def read_section(literature_id: int, keyword: str = "", max_chars: int = 1500) -> str:
    """阅读文献正文的指定片段：按关键词定位最相关的段落并返回其上下文；不给关键词则返回开头部分。用于精读论文细节（方法、实验、结论等）。"""
    lit = db.query_one(
        "SELECT content FROM literature WHERE id = %s", (int(literature_id),)
    )
    if not lit:
        return json.dumps({"error": f"文献不存在: {literature_id}"}, ensure_ascii=False)
    content = lit["content"] or ""
    if not content:
        return json.dumps({"note": "该文献没有可读的正文文本"}, ensure_ascii=False)
    max_chars = max(200, min(int(max_chars), 4000))
    keyword = (keyword or "").strip()
    if keyword:
        pos = content.find(keyword)
        if pos < 0:
            pos = 0
        start = max(0, pos - max_chars // 3)
    else:
        start = 0
    excerpt = content[start : start + max_chars]
    return _truncate(excerpt)


@mcp.tool()
def translate(text: str, target_language: str = "中文") -> str:
    """翻译技能：将文本在中文与英文之间互译。target_language 为目标语言（中文 或 English）。适合翻译论文标题、摘要或段落。"""
    text = (text or "").strip()
    if not text:
        return json.dumps({"error": "text 不能为空"}, ensure_ascii=False)
    if target_language not in ("中文", "English"):
        return json.dumps({"error": "target_language 仅支持 中文 或 English"}, ensure_ascii=False)
    prompt = (
        f"你是专业的学术翻译。请将下面的文本翻译为{target_language}，"
        "保持学术术语准确，只输出译文本身，不要任何解释或前后缀。\n\n" + text
    )
    return _truncate(llm.skill_chat(prompt))


@mcp.tool()
def summarize(literature_id: int, style: str = "structured") -> str:
    """总结技能：对一篇文献的全文做结构化总结（研究问题、方法、实验、结论、局限）。style 可选 structured（结构化）或 plain（一段话）。"""
    lit = db.query_one(
        "SELECT title, authors, abstract_text, content FROM literature WHERE id = %s",
        (int(literature_id),),
    )
    if not lit:
        return json.dumps({"error": f"文献不存在: {literature_id}"}, ensure_ascii=False)
    body = (lit["content"] or lit["abstract_text"] or "")[:6000]
    if not body:
        return json.dumps({"note": "该文献没有可总结的文本"}, ensure_ascii=False)
    if style == "plain":
        instruction = "请用一段流畅的中文概括这篇论文的核心内容（150 字以内）。"
    else:
        instruction = (
            "请用中文按以下结构总结这篇论文：\n"
            "1. 研究问题\n2. 方法\n3. 实验与结果\n4. 结论\n5. 局限性\n"
            "每点 1-2 句话。"
        )
    prompt = (
        f"论文标题：{lit['title']}\n作者：{lit['authors']}\n\n正文内容：\n{body}\n\n{instruction}"
    )
    return _truncate(llm.skill_chat(prompt))


@mcp.tool()
def finalize() -> str:
    """研究阶段结束信号：当你已收集到足够信息可以回答用户问题时调用此工具。调用后系统将基于已收集的信息生成最终回答。"""
    return json.dumps({"ok": True, "message": "研究阶段结束，请生成最终回答"}, ensure_ascii=False)


if __name__ == "__main__":
    mcp.run(transport="stdio")
