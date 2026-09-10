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


def embed(texts):
    """同步批量向量化（MCP 工具内使用，单条查询场景）。"""
    resp = sync_client.embeddings.create(
        model=config.EMBEDDING_MODEL, input=texts
    )
    return [item.embedding for item in resp.data]


def skill_chat(prompt):
    """技能类一次性调用（翻译/总结内部使用）。"""
    resp = sync_client.chat.completions.create(
        model=config.SKILL_MODEL,
        messages=[{"role": "user", "content": prompt}],
    )
    return resp.choices[0].message.content or ""
