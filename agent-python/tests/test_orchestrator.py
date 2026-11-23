"""编排器行为：登录态强制注入、收敛路径与事件统计（llm 全 mock，不出网）。"""
import asyncio
import json

import orchestrator
from orchestrator import Orchestrator, RunStats


def _stats_recorder():
    events = []
    return events, lambda data: events.append(data)


def test_execute_tool_overrides_hallucinated_login_state():
    """LLM 幻觉出的 user_id/scope 必须被服务端登录态覆盖。"""
    captured = {}

    async def call_tool(name, args):
        captured.update(args)
        return json.dumps({"results": []})

    orch = Orchestrator(call_tool, [], api_key="k")
    events, record_step = _stats_recorder()
    asyncio.run(orch._execute_tool(
        "semantic_search",
        {"query": "x", "scope": "mine", "user_id": 999},
        7, record_step, [], RunStats(), "orchestrator",
    ))
    assert captured["user_id"] == 7
    assert captured["scope"] == "mine"


def test_execute_tool_invalid_scope_falls_back_to_all():
    captured = {}

    async def call_tool(name, args):
        captured.update(args)
        return json.dumps({"results": []})

    orch = Orchestrator(call_tool, [], api_key="k")
    events, record_step = _stats_recorder()
    asyncio.run(orch._execute_tool(
        "semantic_search", {"query": "x", "scope": "他人私库"},
        7, record_step, [], RunStats(), "orchestrator",
    ))
    assert captured["scope"] == "all"


def test_supervisor_no_tool_calls_streams_final_answer(monkeypatch):
    async def fake_chat_with_tools(model, system_prompt, messages, tools, api_key=None):
        return "先想想", [], {"input_tokens": 1, "output_tokens": 2, "total_tokens": 3}

    async def fake_chat_stream(model, system_prompt, messages, on_token, api_key=None):
        on_token("你好")
        return "你好，回答完成", {"input_tokens": 1, "output_tokens": 1, "total_tokens": 2}

    monkeypatch.setattr(orchestrator.llm, "chat_with_tools", fake_chat_with_tools)
    monkeypatch.setattr(orchestrator.llm, "chat_stream", fake_chat_stream)

    orch = Orchestrator(lambda n, a: (_ for _ in ()).throw(AssertionError("不应调工具")), [], api_key="k")
    events, record_step = _stats_recorder()
    tokens = []
    answer = asyncio.run(orch._supervisor_loop(
        "sys", [{"role": "user", "content": "q"}], record_step,
        [], RunStats(), lambda e, d: tokens.append((e, d)), 7,
    ))
    assert answer == "你好，回答完成"
    assert ("token", {"content": "你好"}) in tokens


def test_run_stats_accumulates_usage():
    stats = RunStats()
    stats.add_usage({"input_tokens": 10, "output_tokens": 5, "total_tokens": 15})
    stats.add_usage({"input_tokens": 1, "output_tokens": None, "total_tokens": None})
    d = stats.to_dict()
    assert d["inputTokens"] == 11
    assert d["outputTokens"] == 5
    assert d["totalTokens"] == 15
