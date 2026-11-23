"""MCP 工具可见性与参数校验：不连 MySQL/Milvus，全部 monkeypatch db 层。"""
import json

import pytest

import mcp_server
from mcp_server import _can_read, _flag


def tool_fn(name):
    """FastMCP 装饰器兼容：直接函数或 FunctionTool 代理都能取到原函数。"""
    attr = getattr(mcp_server, name)
    return getattr(attr, "fn", attr)


# ==================== _flag / _can_read ====================

@pytest.mark.parametrize("value,expected", [
    (None, True),            # 历史 NULL 视为公开
    (b"\x00", False),
    (b"\x01", True),
    (1, True),
    (0, False),
    (True, True),
])
def test_flag_normalizes_bit(value, expected):
    assert _flag(value) is expected


def test_can_read_owner_or_public():
    assert _can_read({"user_id": 7, "is_public": b"\x00"}, 7)
    assert _can_read({"user_id": 7, "is_public": None}, 999)
    assert not _can_read({"user_id": 7, "is_public": b"\x00"}, 999)


# ==================== get_literature 可见性 ====================

def test_get_literature_private_denied_for_others(monkeypatch):
    monkeypatch.setattr(
        mcp_server.db, "query_one",
        lambda sql, args=None: {"id": 5, "title": "私密", "user_id": 7, "is_public": b"\x00"},
    )
    out = json.loads(tool_fn("get_literature")(literature_id=5, user_id=999))
    assert "error" in out


def test_get_literature_private_readable_by_owner(monkeypatch):
    monkeypatch.setattr(
        mcp_server.db, "query_one",
        lambda sql, args=None: {
            "id": 5, "title": "私密", "abstract_text": "摘要", "user_id": 7, "is_public": b"\x00",
        },
    )
    out = json.loads(tool_fn("get_literature")(literature_id=5, user_id=7))
    assert out["title"] == "私密"
    assert "user_id" not in out and "is_public" not in out  # 内部字段不外泄


def test_get_literature_public_readable(monkeypatch):
    monkeypatch.setattr(
        mcp_server.db, "query_one",
        lambda sql, args=None: {"id": 5, "title": "公开", "user_id": 7, "is_public": b"\x01"},
    )
    out = json.loads(tool_fn("get_literature")(literature_id=5, user_id=999))
    assert out["title"] == "公开"


def test_get_literature_missing(monkeypatch):
    monkeypatch.setattr(mcp_server.db, "query_one", lambda sql, args=None: None)
    out = json.loads(tool_fn("get_literature")(literature_id=404, user_id=1))
    assert "error" in out


# ==================== read_section 关键词定位 ====================

def _patch_content(monkeypatch, content):
    monkeypatch.setattr(
        mcp_server.db, "query_one",
        lambda sql, args=None: {"content": content, "user_id": 1, "is_public": None},
    )


def test_read_section_keyword_found(monkeypatch):
    _patch_content(monkeypatch, "开头。" + "方法细节" * 100)
    out = tool_fn("read_section")(literature_id=1, keyword="方法细节", max_chars=300, user_id=1)
    assert "方法细节" in out


def test_read_section_keyword_missing_returns_head(monkeypatch):
    _patch_content(monkeypatch, "正文开头" + "x" * 500)
    out = tool_fn("read_section")(literature_id=1, keyword="不存在的词", max_chars=100, user_id=1)
    assert out.startswith("正文开头")


def test_read_section_empty_content(monkeypatch):
    _patch_content(monkeypatch, "")
    out = json.loads(tool_fn("read_section")(literature_id=1, user_id=1))
    assert "note" in out


# ==================== summarize 可见性 ====================

def test_summarize_private_denied(monkeypatch):
    monkeypatch.setattr(
        mcp_server.db, "query_one",
        lambda sql, args=None: {
            "title": "t", "authors": "a", "abstract_text": "", "content": "c",
            "user_id": 7, "is_public": b"\x00",
        },
    )
    out = json.loads(tool_fn("summarize")(literature_id=1, user_id=999))
    assert "error" in out


# ==================== list_literatures 过滤 ====================

def _patch_rows(monkeypatch, rows):
    monkeypatch.setattr(mcp_server.db, "query", lambda sql, args=None: rows)


def test_list_literatures_marks_own_private(monkeypatch):
    _patch_rows(monkeypatch, [
        {"id": 1, "title": "我的私有", "authors": None, "journal": None,
         "year": None, "category": None, "user_id": 7, "is_public": b"\x00"},
        {"id": 2, "title": "社区公开", "authors": None, "journal": None,
         "year": None, "category": None, "user_id": 9, "is_public": b"\x01"},
    ])
    out = json.loads(tool_fn("list_literatures")(keyword="", user_id=7))
    assert out["total"] == 2
    assert out["literatures"][0].get("private") is True
    assert "private" not in out["literatures"][1]


# ==================== 工具结果压缩 ====================

def test_compact_results_keeps_valid_json_under_limit():
    results = [
        {"literatureId": i, "title": "t" * 50, "abstract": "a" * 400, "matchedText": "m" * 400}
        for i in range(50)
    ]
    out = mcp_server._compact_results(results)
    parsed = json.loads(out)  # 必须仍是合法 JSON
    assert len(parsed["results"]) < 50  # 超长条目被丢弃
    assert parsed.get("truncated") is True
