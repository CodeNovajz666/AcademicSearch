"""Agent 主服务：FastAPI + SSE + MCP Client 生命周期管理。

职责：
- 拉起并持有唯一的 MCP Server 子进程（stdio 传输）
- JWT 鉴权（与 Java 后端共用密钥签发的 token）
- SSE 流式对话端点 + 会话 CRUD
- 中心化编排器注入 MCP 工具

启动：python main.py（默认 8000 端口）
"""
import asyncio
import contextlib
import json
import os
import sys
from contextlib import asynccontextmanager
from pathlib import Path

import jwt
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, StreamingResponse
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

import config
import conversation_store
import db

MCP_SERVER_PATH = Path(__file__).resolve().parent / "mcp_server.py"

mcp_session: ClientSession | None = None
mcp_exit_stack: contextlib.AsyncExitStack | None = None
tool_schemas: list[dict] = []


async def _start_mcp():
    """拉起 MCP Server 子进程并完成初始化握手 + 工具发现。"""
    global mcp_session, mcp_exit_stack, tool_schemas
    # Windows 子进程需要 SYSTEMROOT/PATH；排除干扰变量保持环境干净
    env = {
        k: v
        for k, v in os.environ.items()
        if k in ("SYSTEMROOT", "PATH", "PYTHONIOENCODING", "PYTHONDONTWRITEBYTECODE")
    }
    env["PYTHONIOENCODING"] = "utf-8"
    params = StdioServerParameters(
        command=sys.executable, args=[str(MCP_SERVER_PATH)], env=env
    )
    mcp_exit_stack = contextlib.AsyncExitStack()
    await mcp_exit_stack.__aenter__()
    read, write = await mcp_exit_stack.enter_async_context(stdio_client(params))
    mcp_session = await mcp_exit_stack.enter_async_context(ClientSession(read, write))
    await mcp_session.initialize()
    listed = await mcp_session.list_tools()
    tool_schemas = [
        {
            "type": "function",
            "function": {
                "name": t.name,
                "description": t.description or "",
                "parameters": t.inputSchema
                if isinstance(t.inputSchema, dict)
                else json.loads(t.inputSchema or "{}"),
            },
        }
        for t in listed.tools
    ]
    print(
        f"[MCP] connected, {len(tool_schemas)} tools: "
        f"{[t['function']['name'] for t in tool_schemas]}"
    )


async def _stop_mcp():
    global mcp_session, mcp_exit_stack
    if mcp_exit_stack:
        await mcp_exit_stack.aclose()
    mcp_session = None
    mcp_exit_stack = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    await _start_mcp()
    yield
    await _stop_mcp()


app = FastAPI(title="AcademicSearch Agent", lifespan=lifespan)

# CORS：允许 Vite 开发服务器
from fastapi.middleware.cors import CORSMiddleware  # noqa: E402

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://localhost:4173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def current_user_id(request: Request) -> int:
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        raise ValueError("未登录")
    token = auth[7:]
    try:
        payload = jwt.decode(token, config.JWT_SECRET, algorithms=["HS256"])
    except jwt.PyJWTError as e:
        raise ValueError("登录已过期") from e
    email = payload.get("sub")
    if not email:
        raise ValueError("登录已过期")
    row = db.query_one("SELECT id FROM users WHERE email = %s", (email,))
    if not row:
        raise ValueError("用户不存在")
    return row["id"]


def auth_error_response(e: ValueError):
    return JSONResponse({"code": 401, "message": str(e), "data": None}, status_code=401)


@app.get("/health")
async def health():
    return {"status": "ok", "mcp": mcp_session is not None}


@app.post("/agent/chat")
async def agent_chat(request: Request):
    try:
        user_id = current_user_id(request)
    except ValueError as e:
        return auth_error_response(e)
    body = await request.json()
    question = (body.get("question") or "").strip()
    conversation_id = body.get("conversationId")
    if not question:
        return JSONResponse({"code": 400, "message": "问题不能为空", "data": None}, status_code=400)

    async def event_stream():
        queue: asyncio.Queue = asyncio.Queue()

        def emit(event, data):
            queue.put_nowait((event, data))

        async def run_agent():
            from orchestrator import Orchestrator

            async def call_tool(name, args):
                if mcp_session is None:
                    return json.dumps({"error": "MCP 未连接"}, ensure_ascii=False)
                result = await mcp_session.call_tool(name, arguments=args)
                texts = [
                    c.text for c in (result.content or []) if hasattr(c, "text")
                ]
                return "\n".join(texts)

            orchestrator = Orchestrator(call_tool, tool_schemas)
            await orchestrator.run(user_id, conversation_id, question, emit)
            queue.put_nowait((None, None))

        task = asyncio.create_task(run_agent())
        try:
            while True:
                event, data = await queue.get()
                if event is None:
                    break
                payload = json.dumps(data, ensure_ascii=False, default=str)
                yield f"event: {event}\ndata: {payload}\n\n"
        finally:
            if not task.done():
                task.cancel()

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


@app.get("/agent/conversations")
async def list_conversations(request: Request):
    try:
        user_id = current_user_id(request)
    except ValueError as e:
        return auth_error_response(e)
    rows = conversation_store.list_conversations(user_id)
    return {"code": 200, "message": "success", "data": rows}


@app.get("/agent/conversations/{conversation_id}/messages")
async def conversation_messages(request: Request, conversation_id: int):
    try:
        user_id = current_user_id(request)
    except ValueError as e:
        return auth_error_response(e)
    try:
        details = conversation_store.message_details(user_id, conversation_id)
    except conversation_store.NotFound:
        return JSONResponse(
            {"code": 404, "message": "会话不存在或无权访问", "data": None}, status_code=404
        )
    return {"code": 200, "message": "success", "data": details}


@app.patch("/agent/conversations/{conversation_id}")
async def rename_conversation(request: Request, conversation_id: int):
    try:
        user_id = current_user_id(request)
    except ValueError as e:
        return auth_error_response(e)
    body = await request.json()
    try:
        row = conversation_store.rename(user_id, conversation_id, body.get("title"))
    except conversation_store.NotFound:
        return JSONResponse(
            {"code": 404, "message": "会话不存在或无权访问", "data": None}, status_code=404
        )
    except ValueError as e:
        return JSONResponse({"code": 400, "message": str(e), "data": None}, status_code=400)
    return {"code": 200, "message": "success", "data": row}


@app.delete("/agent/conversations/{conversation_id}")
async def delete_conversation(request: Request, conversation_id: int):
    try:
        user_id = current_user_id(request)
    except ValueError as e:
        return auth_error_response(e)
    try:
        conversation_store.delete(user_id, conversation_id)
    except conversation_store.NotFound:
        return JSONResponse(
            {"code": 404, "message": "会话不存在或无权访问", "data": None}, status_code=404
        )
    return {"code": 200, "message": "删除成功", "data": None}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
