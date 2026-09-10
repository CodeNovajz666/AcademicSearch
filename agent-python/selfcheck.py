"""快速自检：模块可导入、DB 可连、MCP Server 可独立启动并列出工具。"""
import asyncio
import sys

sys.stdout.reconfigure(encoding="utf-8")


def check_modules():
    import config
    import db
    import llm
    import conversation_store
    import orchestrator
    import main  # noqa: F401 - 触发 FastAPI 路由装配
    print("[1/3] modules import OK")
    assert config.DB_NAME, "DB_NAME empty"
    assert config.DASHSCOPE_API_KEY, "DASHSCOPE_API_KEY empty"


def check_db():
    import db

    rows = db.query("SELECT COUNT(*) AS c FROM literature")
    print(f"[2/3] MySQL connected, literature count = {rows[0]['c']}")
    convs = db.query("SELECT COUNT(*) AS c FROM conversation")
    print(f"      conversation count = {convs[0]['c']}")


async def check_mcp():
    from mcp import ClientSession, StdioServerParameters
    from mcp.client.stdio import stdio_client

    import mcp_server

    tools = await mcp_server.mcp.list_tools()
    names = [t.name for t in tools]
    expected = {
        "semantic_search",
        "list_literatures",
        "get_literature",
        "read_section",
        "translate",
        "summarize",
        "finalize",
    }
    assert expected.issubset(set(names)), f"missing tools: {expected - set(names)}"
    print(f"[3/3] MCP Server in-process tools OK: {names}")

    # stdio 子进程冒烟（真实链路）
    import sys as _sys

    params = StdioServerParameters(
        command=_sys.executable, args=["mcp_server.py"], env={"PYTHONIOENCODING": "utf-8"}
    )
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            listed = await session.list_tools()
            result = await session.call_tool(
                "list_literatures", arguments={"keyword": ""}
            )
            text = "".join(c.text for c in result.content if hasattr(c, "text"))
            print(f"      stdio MCP roundtrip OK, {len(listed.tools)} tools, "
                  f"list_literatures -> {text[:80]}")


if __name__ == "__main__":
    check_modules()
    check_db()
    asyncio.run(check_mcp())
    print("ALL CHECKS PASSED")
