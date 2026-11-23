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
import traceback
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
    global mcp_session, mcp_exit_stack, tool_schemas
    if mcp_exit_stack:
        try:
            await mcp_exit_stack.aclose()
        except Exception:  # noqa: BLE001 - 子进程已死时清理可能失败，不能阻断重启
            pass
    mcp_session = None
    mcp_exit_stack = None
    tool_schemas = []


# 重启互斥：并发请求同时发现 MCP 断连时只拉起一次（Python 3.12 中 Lock 不再绑定事件循环）
mcp_lock = asyncio.Lock()


async def ensure_mcp() -> ClientSession:
    """获取可用 MCP 会话；子进程崩溃后自动重新拉起（并发请求下单飞）。"""
    if mcp_session is not None:
        return mcp_session
    async with mcp_lock:
        if mcp_session is None:
            print("[MCP] session unavailable, restarting subprocess...")
            await _stop_mcp()
            await _start_mcp()
    return mcp_session


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not config.JWT_SECRET or len(config.JWT_SECRET.encode("utf-8")) < 32:
        raise RuntimeError(
            "JWT_SECRET 未配置或不足 32 字节：请检查项目根目录 .env（参考 .env.example）。"
            "本服务与 Java 后端共用该密钥签发/校验 JWT，缺失时拒绝启动。"
        )
    # 会话三表自检自建（幂等）；失败不阻断启动，请求期会得到明确报错
    try:
        await asyncio.to_thread(db.ensure_schema)
    except Exception as e:  # noqa: BLE001 - DB 暂不可用时保持可启动，失败留待请求期暴露
        print(f"[DB] 会话表初始化失败（若表已存在可忽略）: {e}")
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
        # Java 端 jjwt 的 signWith(SecretKey) 按密钥长度自动选 HMAC 算法
        # （密钥 ≥64 字节即签 HS512），这里必须接受 HMAC 全族而非只认 HS256，
        # 否则 Java 签发的所有令牌都会在 Python 侧验签失败（表现为 AI 副驾 401）
        payload = jwt.decode(token, config.JWT_SECRET, algorithms=["HS256", "HS384", "HS512"])
    except jwt.PyJWTError as e:
        raise ValueError("登录已过期") from e
    # refresh 令牌只用于刷新换新，不能当访问令牌（与 Java 过滤器同一规则）
    if payload.get("type") == "refresh":
        raise ValueError("无效的访问令牌")
    email = payload.get("sub")
    if not email:
        raise ValueError("登录已过期")
    row = db.query_one("SELECT id FROM users WHERE email = %s", (email,))
    if not row:
        raise ValueError("用户不存在")
    return row["id"]


def auth_error_response(e: ValueError):
    return JSONResponse({"code": 401, "message": str(e), "data": None}, status_code=401)


def _conversation_dto(row: dict) -> dict:
    """会话对外字段统一 camelCase（与原 Java 版一致，前端读 createdAt/updatedAt）。"""
    return {
        "id": row.get("id"),
        "title": row.get("title"),
        "createdAt": row.get("created_at"),
        "updatedAt": row.get("updated_at"),
    }


@app.get("/health")
async def health():
    return {"status": "ok", "mcp": mcp_session is not None}


async def _user_llm_options(user_id: int) -> dict:
    """读取用户自备 Key 与模型覆盖；模型等配置缺失时回退服务器默认。"""
    try:
        row = await asyncio.to_thread(db.get_user_llm_settings, user_id)
    except Exception:
        row = None
    row = row or {}
    return {
        "api_key": row.get("dashscope_api_key") or None,
        "orchestrator_model": row.get("chat_model") or None,
        "subagent_model": row.get("subagent_model") or None,
    }


def _user_api_key(user_id: int) -> str | None:
    """用户自备 DashScope Key（已去除空白）。模型交互强制 BYOK：没有则返回 None。"""
    try:
        row = db.get_user_llm_settings(user_id) or {}
    except Exception:
        return None
    return (row.get("dashscope_api_key") or "").strip() or None


@app.post("/agent/chat")
async def agent_chat(request: Request):
    try:
        # pymysql 同步调用下沉线程池，避免阻塞事件循环
        user_id = await asyncio.to_thread(current_user_id, request)
    except ValueError as e:
        return auth_error_response(e)
    # 模型交互必须使用用户自备 Key：未配置时直接拒绝，不启动 SSE 流，
    # 也不静默回退服务器默认 Key（Key 由用户在「设置」页自行配置）
    api_key = await asyncio.to_thread(_user_api_key, user_id)
    if not api_key:
        return JSONResponse(
            {
                "code": 400,
                "message": "尚未配置 DashScope API Key：请先在左侧「设置」中填写你自己的 Key，再使用 AI 研究副驾",
                "data": None,
            },
            status_code=400,
        )
    try:
        body = await request.json()
    except Exception:
        return JSONResponse(
            {"code": 400, "message": "请求体必须是合法 JSON", "data": None}, status_code=400
        )
    question = (body.get("question") or "").strip()
    conversation_id = body.get("conversationId")
    # 前端可能传字符串 id：统一收敛为 int，非法值显式 400
    #（否则进入 DB 比较报错，走"Agent 执行失败"兜底且难以定位）
    if conversation_id is not None:
        try:
            conversation_id = int(conversation_id)
        except (TypeError, ValueError):
            return JSONResponse(
                {"code": 400, "message": "conversationId 必须是整数", "data": None},
                status_code=400,
            )
    # 研究工作区：携带正在精读的文献 id，编排器会注入"优先引用该论文原文"的上下文
    literature_id = body.get("literatureId")
    if literature_id is not None:
        try:
            literature_id = int(literature_id)
        except (TypeError, ValueError):
            return JSONResponse(
                {"code": 400, "message": "literatureId 必须是整数", "data": None}, status_code=400
            )
    if not question:
        return JSONResponse({"code": 400, "message": "问题不能为空", "data": None}, status_code=400)

    async def event_stream():
        queue: asyncio.Queue = asyncio.Queue()

        def emit(event, data):
            queue.put_nowait((event, data))

        async def run_agent():
            try:
                from orchestrator import Orchestrator

                async def call_tool(name, args):
                    try:
                        session = await ensure_mcp()
                        result = await session.call_tool(name, arguments=args)
                    except asyncio.CancelledError:
                        raise
                    except Exception:
                        # 子进程可能已崩溃（管道断裂/进程退出）：标记断连，
                        # 重新拉起后重试一次；再失败则向上抛给编排器降级为错误文本
                        await _stop_mcp()
                        session = await ensure_mcp()
                        result = await session.call_tool(name, arguments=args)
                    texts = [
                        c.text for c in (result.content or []) if hasattr(c, "text")
                    ]
                    return "\n".join(texts)

                llm_opts = await _user_llm_options(user_id)
                # 模型交互强制使用已通过前置校验的用户自备 Key（不回退服务器默认）
                llm_opts["api_key"] = api_key
                orchestrator = Orchestrator(call_tool, tool_schemas, **llm_opts)
                await orchestrator.run(
                    user_id, conversation_id, question, emit, literature_id
                )
            except asyncio.CancelledError:
                raise
            except Exception:  # noqa: BLE001 - 编排器之外的异常也必须走 SSE error 事件，否则前端会无限等待
                # 完整堆栈留服务端日志；对客户端只给通用文案，避免泄露内部实现细节
                traceback.print_exc()
                emit("error", {"message": "Agent 服务内部异常，请稍后重试"})
            finally:
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
        # pymysql 同步调用下沉线程池，避免阻塞事件循环
        user_id = await asyncio.to_thread(current_user_id, request)
    except ValueError as e:
        return auth_error_response(e)
    rows = conversation_store.list_conversations(user_id)
    return {"code": 200, "message": "success", "data": [_conversation_dto(r) for r in rows]}


@app.get("/agent/conversations/{conversation_id}/messages")
async def conversation_messages(request: Request, conversation_id: int):
    try:
        # pymysql 同步调用下沉线程池，避免阻塞事件循环
        user_id = await asyncio.to_thread(current_user_id, request)
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
        # pymysql 同步调用下沉线程池，避免阻塞事件循环
        user_id = await asyncio.to_thread(current_user_id, request)
    except ValueError as e:
        return auth_error_response(e)
    body: dict = {}
    try:
        parsed = await request.json()
        if isinstance(parsed, dict):
            body = parsed
    except Exception:
        pass  # 空/非法请求体按无字段处理，rename 对缺失 title 给出明确 400
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
        # pymysql 同步调用下沉线程池，避免阻塞事件循环
        user_id = await asyncio.to_thread(current_user_id, request)
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

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("AGENT_PORT", "8000")))
