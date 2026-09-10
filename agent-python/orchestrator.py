"""中心化编排器（Orchestrator-Centric）。

单一 Orchestrator Agent 通过 MCP 协议调用全部工具，ReAct 循环 +
finalize 收尾协议 + 流式最终回答。事件流与原 Java 版完全一致：
start → step*(agent_start/thinking/tool_call/tool_result) → token* → final | error
"""
import json
import time
from typing import Awaitable, Callable

import config
import conversation_store
import llm

SYSTEM_PROMPT = """你是一个学术文献研究中心协调者（Orchestrator），通过 MCP 工具集完成用户的研究请求。

## 工作流程
1. 分析用户问题，规划需要收集的信息
2. 按需调用工具收集信息：
   - semantic_search：按语义检索文献（回答"有哪些关于X的文献"的首选）
   - list_literatures：列出文献库清单（用户问"库里有什么"）
   - get_literature：获取某篇文献的元数据与摘要
   - read_section：精读文献正文片段（提取方法/实验/结论细节）
   - translate：中英文互译
   - summarize：对文献做结构化总结
3. 信息足够后调用 finalize 结束研究阶段

## 规则
- 每轮必须调用一个工具（不允许只输出文本不调工具）
- 最多 {max_steps} 步，注意控制节奏，避免重复调用相同工具
- 多轮对话中注意结合上下文理解指代（如"第二篇"指上文列表中的第二篇）
- 若工具返回错误或空结果，尝试换关键词或换工具，不要放弃

## 回答要求（finalize 之后）
- 用中文回答，结构清晰（必要时用小标题/列表）
- 回答中的事实必须来自工具返回的真实文献内容，禁止编造
- 引用文献时标注来源（标题/作者/年份）"""


class RunStats:
    def __init__(self):
        self.steps = 0
        self.tool_calls = 0
        self.duration_ms = 0
        self.usage = None

    def add_usage(self, usage):
        if not usage:
            return
        if self.usage is None:
            self.usage = dict(usage)
            return
        for key in ("input_tokens", "output_tokens", "total_tokens"):
            a = self.usage.get(key) or 0
            b = usage.get(key) or 0
            self.usage[key] = a + b

    def to_dict(self):
        result = {
            "steps": self.steps,
            "toolCalls": self.tool_calls,
            "durationMs": self.duration_ms,
        }
        if self.usage:
            result.update(
                {
                    "inputTokens": self.usage.get("input_tokens"),
                    "outputTokens": self.usage.get("output_tokens"),
                    "totalTokens": self.usage.get("total_tokens"),
                }
            )
        return result


class Orchestrator:
    """call_tool: MCP 工具调用注入（name, args_dict) -> str；
    tool_schemas: OpenAI function calling 格式的工具定义列表。"""

    def __init__(
        self,
        call_tool: Callable[[str, dict], Awaitable[str]],
        tool_schemas: list[dict],
    ):
        self.call_tool = call_tool
        self.tool_schemas = tool_schemas

    async def run(
        self,
        user_id: int,
        conversation_id,
        question: str,
        emit,
    ) -> None:
        """执行一次完整研究任务。emit(event, data) 为 SSE 事件发射器。"""
        start_ms = time.time()
        conversation = conversation_store.get_or_create(
            user_id, conversation_id, question
        )
        conversation_id = conversation["id"]
        emit("start", {"conversationId": conversation_id})

        events = []
        references = []
        stats = RunStats()

        def record_step(event_data):
            events.append(event_data)
            emit("step", event_data)

        record_step(
            {
                "agent": "orchestrator",
                "stepIndex": 0,
                "type": "agent_start",
                "toolName": None,
                "summary": f"开始规划：{question}",
                "durationMs": None,
            }
        )

        system_prompt = SYSTEM_PROMPT.format(max_steps=config.ORCHESTRATOR_MAX_STEPS)
        messages = conversation_store.history_messages(conversation_id)
        messages.append({"role": "user", "content": question})

        try:
            answer = await self._loop(
                system_prompt, messages, record_step, references, stats, emit
            )
            stats.duration_ms = int((time.time() - start_ms) * 1000)
            stats.steps = len(events)
            stats.tool_calls = sum(
                1 for e in events if e.get("type") == "tool_call"
            )
            message_id = conversation_store.save_turn(
                conversation_id, question, answer, events, references, stats.to_dict()
            )
            emit(
                "final",
                {
                    "conversationId": conversation_id,
                    "messageId": message_id,
                    "references": references,
                    "stats": stats.to_dict(),
                },
            )
        except Exception as e:  # noqa: BLE001 - SSE 顶层兜底
            stats.duration_ms = int((time.time() - start_ms) * 1000)
            emit("error", {"message": f"Agent 执行失败：{e}"})

    async def _loop(self, system_prompt, messages, record_step, references, stats, emit):
        for step_index in range(config.ORCHESTRATOR_MAX_STEPS):
            content, tool_calls, usage = await llm.chat_with_tools(
                config.ORCHESTRATOR_MODEL, system_prompt, messages, self.tool_schemas
            )
            stats.add_usage(usage)

            if content and content.strip():
                record_step(
                    {
                        "agent": "orchestrator",
                        "stepIndex": step_index,
                        "type": "thinking",
                        "toolName": None,
                        "summary": content.strip()[:200],
                        "durationMs": None,
                    }
                )

            if not tool_calls:
                # 模型未调工具直接输出：视为收敛，流式补发
                return await self._final_answer(
                    system_prompt, messages, emit, stats, record_step, step_index
                )

            messages.append(
                {
                    "role": "assistant",
                    "content": content or None,
                    "tool_calls": [
                        {
                            "id": tc["id"],
                            "type": "function",
                            "function": {
                                "name": tc["name"],
                                "arguments": tc["arguments"],
                            },
                        }
                        for tc in tool_calls
                    ],
                }
            )

            finalized = False
            for tc in tool_calls:
                name = tc["name"]
                if name == "finalize":
                    finalized = True
                    continue
                try:
                    args = json.loads(tc["arguments"]) if tc["arguments"] else {}
                except json.JSONDecodeError:
                    args = {}
                record_step(
                    {
                        "agent": "orchestrator",
                        "stepIndex": step_index,
                        "type": "tool_call",
                        "toolName": name,
                        "summary": None,
                        "durationMs": None,
                    }
                )
                tool_start = time.time()
                try:
                    result = await self.call_tool(name, args)
                except Exception as e:  # noqa: BLE001 - 工具失败降级为错误文本
                    result = json.dumps(
                        {"error": f"工具执行失败：{e}"}, ensure_ascii=False
                    )
                duration_ms = int((time.time() - tool_start) * 1000)
                self._collect_reference(name, result, references)
                record_step(
                    {
                        "agent": "orchestrator",
                        "stepIndex": step_index,
                        "type": "tool_result",
                        "toolName": name,
                        "summary": None,
                        "durationMs": duration_ms,
                    }
                )
                messages.append(
                    {
                        "role": "tool",
                        "tool_call_id": tc["id"],
                        "content": result,
                    }
                )

            if finalized:
                return await self._final_answer(
                    system_prompt, messages, emit, stats, record_step, step_index
                )

        # 步数耗尽：强制收敛
        return await self._final_answer(
            system_prompt, messages, emit, stats, record_step, config.ORCHESTRATOR_MAX_STEPS
        )

    async def _final_answer(self, system_prompt, messages, emit, stats, record_step, step_index):
        record_step(
            {
                "agent": "orchestrator",
                "stepIndex": step_index,
                "type": "thinking",
                "toolName": "finalize",
                "summary": "信息收集完成，生成最终回答",
                "durationMs": None,
            }
        )
        messages.append(
            {
                "role": "user",
                "content": "信息收集阶段已结束。请基于以上所有已收集的信息，直接给出面向用户的完整最终回答。",
            }
        )

        def on_token(token):
            emit("token", {"content": token})

        answer, usage = await llm.chat_stream(
            config.ORCHESTRATOR_MODEL, system_prompt, messages, on_token
        )
        stats.add_usage(usage)
        return answer or "（未生成回答）"

    @staticmethod
    def _collect_reference(tool_name, result, references):
        """从 semantic_search 结果提取参考文献列表（去重）。"""
        if tool_name != "semantic_search":
            return
        try:
            data = json.loads(result)
        except (json.JSONDecodeError, TypeError):
            return
        for item in data.get("results", []):
            ref = {"id": item.get("literatureId"), "title": item.get("title")}
            if ref["id"] is not None and not any(
                r["id"] == ref["id"] for r in references
            ):
                references.append(ref)
