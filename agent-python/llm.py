<<<<<<< HEAD
"""DashScope LLM 客户端：OpenAI 兼容模式（function calling + 流式 + embedding）。

所有调用带 429 限流指数退避重试：多 Agent 架构下协调者与子 Agent 连续调用，
容易触发 RPM 限制，一次 429 不能让整轮任务失败。
"""
import asyncio
import json

from openai import AsyncOpenAI, BadRequestError, OpenAI

import config

_clients: dict[str, tuple[AsyncOpenAI, OpenAI]] = {}


def _no_thinking() -> dict:
    """qwen3 系列思考模型默认开启思考；流式收尾/技能这类展示型调用关闭思考，
    避免首 token 前出现长时间静默并节省思考 token。
    非思考模型不识别该参数时报 400，由调用方去掉参数回退重试。"""
    return {"extra_body": {"enable_thinking": False}}


def _clients_for(api_key: str | None) -> tuple[AsyncOpenAI, OpenAI]:
    """按 Key 惰性创建并缓存客户端：用户可在「设置」里配置自己的 Key，
    未配置时回退到服务器 .env 中的默认 Key。"""
    key = (api_key or config.DASHSCOPE_API_KEY or "").strip()
    if not key:
        raise RuntimeError("未配置 DashScope API Key：请在左侧「设置」中填写，或检查项目根目录 .env")
    cached = _clients.get(key)
    if cached is None:
        cached = (
            AsyncOpenAI(base_url=config.DASHSCOPE_BASE_URL, api_key=key),
            OpenAI(base_url=config.DASHSCOPE_BASE_URL, api_key=key),
        )
        _clients[key] = cached
    return cached


_RATE_LIMIT_RETRIES = 4


def _is_rate_limited(e: Exception) -> bool:
    return getattr(e, "status_code", None) == 429


def _retry_after_seconds(e: Exception) -> float | None:
    """优先用服务端 Retry-After 头，否则用指数退避。"""
    response = getattr(e, "response", None)
    header = response.headers.get("Retry-After") if response is not None else None
    try:
        if header is not None:
            return min(float(header), 60.0)
    except ValueError:
        pass
    return None


async def _retry_on_rate_limit(factory):
    """factory: 每次重试重新构造协程（openai 协程不可复用）。

    DashScope limit_requests 按分钟窗口计量，短退避无意义：
    默认退避 5s/15s/30s（共 3 次），服务端给出 Retry-After 时优先遵循。
    """
    backoff = 5.0
    for attempt in range(_RATE_LIMIT_RETRIES):
        try:
            return await factory()
        except Exception as e:
            if _is_rate_limited(e) and attempt < _RATE_LIMIT_RETRIES - 1:
                wait = _retry_after_seconds(e) or backoff
                await asyncio.sleep(wait)
                backoff *= 3
                continue
            raise


async def chat_with_tools(model, system_prompt, messages, tools, api_key=None):
    """一轮工具决策（流式）：返回 (content, tool_calls, usage, reasoning)。

    用流式而非非流式：qwen3 等思考模型的 enable_thinking 只在流式下生效，
    思维链以 reasoning_content 增量下发——协调者/子 Agent 的每轮"思考"由此透出。
    tool_calls 为空表示无调用。"""
    async_client, _ = _clients_for(api_key)

    async def once(extra=None):
        return await async_client.chat.completions.create(
            model=model,
            messages=[{"role": "system", "content": system_prompt}, *messages],
            tools=tools,
            tool_choice="auto",
            stream=True,
            stream_options={"include_usage": True},
            **(extra or {}),
        )

    # 个别非思考模型对 stream+tools 组合报 400 时，关闭思考重试一次
    try:
        stream = await _retry_on_rate_limit(once)
    except BadRequestError:
        stream = await _retry_on_rate_limit(lambda: once({"extra_body": {"enable_thinking": False}}))

    content_parts: list[str] = []
    reasoning_parts: list[str] = []
    calls_acc: dict[int, dict] = {}
    usage_raw = None
    async for chunk in stream:
        if chunk.usage:
            usage_raw = chunk.usage
        if not chunk.choices:
            continue
        delta = chunk.choices[0].delta
        # 思维链增量（qwen3/GLM 的非标准字段，SDK 挂在 delta 额外属性上）
        reasoning = getattr(delta, "reasoning_content", None) or (
            (delta.model_extra or {}).get("reasoning_content")
        )
        if reasoning:
            reasoning_parts.append(reasoning)
        if delta.content:
            content_parts.append(delta.content)
        for tc in (delta.tool_calls or []):
            acc = calls_acc.setdefault(tc.index, {"id": "", "name": "", "arguments": ""})
            if tc.id:
                acc["id"] = tc.id
            if tc.function and tc.function.name:
                acc["name"] = tc.function.name
            if tc.function and tc.function.arguments:
                acc["arguments"] += tc.function.arguments

    calls = [
        {
            "id": calls_acc[i]["id"] or f"call_{i}",
            "name": calls_acc[i]["name"],
            "arguments": calls_acc[i]["arguments"],
        }
        for i in sorted(calls_acc)
    ]
    return (
        "".join(content_parts),
        calls,
        {
            "input_tokens": usage_raw.prompt_tokens if usage_raw else None,
            "output_tokens": usage_raw.completion_tokens if usage_raw else None,
            "total_tokens": usage_raw.total_tokens if usage_raw else None,
        },
        "".join(reasoning_parts),
    )


async def chat_stream(model, system_prompt, messages, on_token, api_key=None):
    """流式生成：增量 token 通过回调透出，返回 (完整文本, usage)。"""
    async_client, _ = _clients_for(api_key)

    async def once(extra=None):
        return await async_client.chat.completions.create(
            model=model,
            messages=[{"role": "system", "content": system_prompt}, *messages],
            stream=True,
            stream_options={"include_usage": True},
            **(extra or {}),
        )

    try:
        stream = await _retry_on_rate_limit(lambda: once(_no_thinking()))
    except BadRequestError:
        stream = await _retry_on_rate_limit(once)
=======
"""DashScope LLM 客户端：OpenAI 兼容模式（function calling + 流式 + embedding）。"""
import json

from openai import AsyncOpenAI, OpenAI

import config

if not config.DASHSCOPE_API_KEY:
    raise RuntimeError("DASHSCOPE_API_KEY 未配置，请检查项目根目录 .env")

async_client = AsyncOpenAI(
    base_url=config.DASHSCOPE_BASE_URL, api_key=config.DASHSCOPE_API_KEY
)
sync_client = OpenAI(
    base_url=config.DASHSCOPE_BASE_URL, api_key=config.DASHSCOPE_API_KEY
)


async def chat_with_tools(model, system_prompt, messages, tools):
    """一轮工具决策：返回 (content, tool_calls, usage)。tool_calls 为空表示无调用。"""
    resp = await async_client.chat.completions.create(
        model=model,
        messages=[{"role": "system", "content": system_prompt}, *messages],
        tools=tools,
        tool_choice="auto",
    )
    msg = resp.choices[0].message
    usage = resp.usage
    calls = [
        {
            "id": tc.id,
            "name": tc.function.name,
            "arguments": tc.function.arguments,
        }
        for tc in (msg.tool_calls or [])
    ]
    return (
        msg.content or "",
        calls,
        {
            "input_tokens": usage.prompt_tokens if usage else None,
            "output_tokens": usage.completion_tokens if usage else None,
            "total_tokens": usage.total_tokens if usage else None,
        },
    )


async def chat_stream(model, system_prompt, messages, on_token):
    """流式生成：增量 token 通过回调透出，返回 (完整文本, usage)。"""
    stream = await async_client.chat.completions.create(
        model=model,
        messages=[{"role": "system", "content": system_prompt}, *messages],
        stream=True,
        stream_options={"include_usage": True},
    )
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    full = []
    usage = None
    async for chunk in stream:
        if chunk.usage:
            usage = {
                "input_tokens": chunk.usage.prompt_tokens,
                "output_tokens": chunk.usage.completion_tokens,
                "total_tokens": chunk.usage.total_tokens,
            }
        if chunk.choices and chunk.choices[0].delta.content:
            token = chunk.choices[0].delta.content
            full.append(token)
            on_token(token)
    return "".join(full), usage


<<<<<<< HEAD
def embed(texts, api_key=None):
    """同步批量向量化（MCP 工具内使用，单条查询场景）。"""
    _, sync_client = _clients_for(api_key)
=======
def embed(texts):
    """同步批量向量化（MCP 工具内使用，单条查询场景）。"""
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    resp = sync_client.embeddings.create(
        model=config.EMBEDDING_MODEL, input=texts
    )
    return [item.embedding for item in resp.data]


<<<<<<< HEAD
def skill_chat(prompt, api_key=None):
    """技能类一次性调用（翻译/总结内部使用）。"""
    _, sync_client = _clients_for(api_key)

    def once(extra=None):
        return sync_client.chat.completions.create(
            model=config.SKILL_MODEL,
            messages=[{"role": "user", "content": prompt}],
            **(extra or {}),
        )

    try:
        resp = once(_no_thinking())
    except BadRequestError:
        resp = once()
=======
def skill_chat(prompt):
    """技能类一次性调用（翻译/总结内部使用）。"""
    resp = sync_client.chat.completions.create(
        model=config.SKILL_MODEL,
        messages=[{"role": "user", "content": prompt}],
    )
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    return resp.choices[0].message.content or ""
