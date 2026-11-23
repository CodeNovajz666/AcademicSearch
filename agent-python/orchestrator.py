<<<<<<< HEAD
"""多 Agent 编排（Supervisor 模式）。

协调者（Orchestrator）通过 Agent-as-Tool 派发三个专家子 Agent 协同工作：
- search 检索专家：多关键词文献调研与相关性筛选
- reader 精读专家：深入指定文献提取方法/实验/结论与原文证据
- analyst 分析专家：结构化总结、翻译、横向对比

子 Agent 复用同一 ReAct 引擎，但只持有各自领域的原子工具子集；
禁止递归派生（子 Agent 不可见 dispatch 工具，深度=2）。
事件流与单 Agent 版完全兼容：start → step*(含子 Agent 步骤, agent 字段区分) → token* → final | error
"""
import asyncio
=======
"""中心化编排器（Orchestrator-Centric）。

单一 Orchestrator Agent 通过 MCP 协议调用全部工具，ReAct 循环 +
finalize 收尾协议 + 流式最终回答。事件流与原 Java 版完全一致：
start → step*(agent_start/thinking/tool_call/tool_result) → token* → final | error
"""
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
import json
import time
from typing import Awaitable, Callable

import config
import conversation_store
import llm

<<<<<<< HEAD
SYSTEM_PROMPT = """你是一个学术文献研究中心的总协调者（Supervisor），通过工具完成用户的研究请求。

## 团队协作（复杂任务下放给专家）
你管理三个专家子 Agent，通过派发工具调用（派发后专家自主执行并返回报告）：
- dispatch_search：检索专家。擅长多关键词、多轮检索的文献调研与相关性筛选
- dispatch_read：精读专家。深入阅读指定文献，提取方法/实验/结论细节与原文证据
- dispatch_analyze：分析专家。做结构化总结、中英翻译、多篇文献横向对比

使用原则：
- 简单的单步查询（如"库里有什么"）直接用原子工具完成，不必派发
- 需要多关键词调研、深入精读或对比分析时，派发对应专家并把任务目标写清楚
- 专家返回的报告是你的信息来源，可以继续追问其他专家或补充检索

## 原子工具（RAG，简单任务直接用）
- semantic_search：语义检索（scope="mine" 只搜用户自己的文献）
- list_literatures：文献库清单
- get_literature：文献元数据与摘要
- read_section：分段精读正文
- translate：中英互译
- summarize：结构化总结

## 规则
- 每轮必须调用一个工具（不允许只输出文本不调工具）
- 最多 {max_steps} 步，控制节奏，避免重复派发相同任务
- 多轮对话中注意结合上下文理解指代（如"第二篇"指上文列表中的第二篇）
- 若工具返回错误或空结果，尝试换关键词、换工具或换专家，不要放弃

## 回答要求（finalize 之后）
- 用中文回答，结构清晰（必要时用小标题/列表）
- 回答中的事实必须来自工具/专家报告返回的真实文献内容，禁止编造
- 引用文献时标注来源（标题/作者/年份）"""


# ==================== 子 Agent 定义 ====================

SUB_AGENTS = {
    "search": {
        "label": "检索专家",
        "tools": {"semantic_search", "list_literatures"},
        "prompt": (
            "你是文献检索专家，负责知识库文献调研。任务：{goal}\n"
            "用 semantic_search 多换几组中英文关键词提高召回（必要时按用户意图选 scope），"
            "再评估每篇与任务的相关性。\n"
            "完成后直接输出报告：候选文献清单（id / 标题 / 一句话相关性），"
            "以及检索结论（覆盖了什么、缺什么）。\n"
            "硬性规则：必须至少调用一次检索工具获得真实结果后才能输出报告；"
            "没有任何工具依据直接输出视为任务失败。禁止编造。"
        ),
    },
    "reader": {
        "label": "精读专家",
        "tools": {"get_literature", "read_section"},
        "prompt": (
            "你是论文精读专家。任务：深入阅读文献（id={literature_id}），重点：{focus}\n"
            "先用 get_literature 确认身份，再用 read_section 按关键词定位精读（方法/实验/结论分别读）。\n"
            "输出结构化精读报告：研究问题 / 方法步骤 / 实验设置与关键数字 / 结论 / 原文证据"
            "（引用论文关键句子或术语）。\n"
            "硬性规则：必须至少调用一次 read_section 获得原文片段后才能输出报告。禁止编造。"
        ),
    },
    "analyst": {
        "label": "分析专家",
        "tools": {"get_literature", "read_section", "summarize", "translate"},
        "prompt": (
            "你是文献分析专家，擅长总结、翻译与横向对比。任务：{task}\n"
            "可按需用 summarize（结构化总结）、translate（中英互译）、"
            "read_section/get_literature（核实细节）完成分析。\n"
            "输出结构化分析结果，涉及对比时给出对比维度表格或列表，引用文献依据。禁止编造。"
        ),
    },
}

# 派发工具 schema（仅协调者可见；子 Agent 不可见，防止递归派生）
DISPATCH_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "dispatch_search",
            "description": "派发检索专家：在知识库做多关键词文献调研与相关性筛选，返回候选清单。适用于文献调研类任务。",
            "parameters": {
                "type": "object",
                "properties": {
                    "goal": {"type": "string", "description": "检索任务目标，如：找出关于 X 的文献并评估相关性"}
                },
                "required": ["goal"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "dispatch_read",
            "description": "派发精读专家：深入阅读指定文献，提取方法/实验/结论细节与原文证据。",
            "parameters": {
                "type": "object",
                "properties": {
                    "literature_id": {"type": "integer", "description": "要精读的文献 id"},
                    "focus": {"type": "string", "description": "精读重点，如：方法细节与创新点"},
                },
                "required": ["literature_id", "focus"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "dispatch_analyze",
            "description": "派发分析专家：结构化总结、中英翻译或多篇文献横向对比分析。",
            "parameters": {
                "type": "object",
                "properties": {
                    "task": {"type": "string", "description": "分析任务描述，如：对比文献3和5的方法差异"},
                },
                "required": ["task"],
            },
        },
    },
]

DISPATCH_TARGET = {"dispatch_search": "search", "dispatch_read": "reader", "dispatch_analyze": "analyst"}


def _brief_readable(lit: dict, user_id: int) -> bool:
    """工作区文献可见性判定（与 mcp_server._can_read 同一规则）：
    公开（is_public 为 1/True/历史 NULL）或属主本人。"""
    is_public = lit.get("is_public")
    if is_public is None:
        public = True
    elif isinstance(is_public, (bytes, bytearray)):
        public = is_public != b"\x00"
    else:
        public = bool(is_public)
    return lit.get("user_id") == user_id or public


=======
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


>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
class RunStats:
    def __init__(self):
        self.steps = 0
        self.tool_calls = 0
<<<<<<< HEAD
        self.agents = ["orchestrator"]
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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

<<<<<<< HEAD
    def touch_agent(self, name):
        if name not in self.agents:
            self.agents.append(name)

=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    def to_dict(self):
        result = {
            "steps": self.steps,
            "toolCalls": self.tool_calls,
<<<<<<< HEAD
            "agents": self.agents,
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
<<<<<<< HEAD
    atomic_tool_schemas: MCP 发现的原子工具（OpenAI function calling 格式）。"""
=======
    tool_schemas: OpenAI function calling 格式的工具定义列表。"""
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

    def __init__(
        self,
        call_tool: Callable[[str, dict], Awaitable[str]],
<<<<<<< HEAD
        atomic_tool_schemas: list[dict],
        api_key: str | None = None,
        orchestrator_model: str | None = None,
        subagent_model: str | None = None,
    ):
        self.call_tool = call_tool
        self.atomic_tools = atomic_tool_schemas
        # 用户级配置：未设置时回退到服务器默认值
        self.api_key = api_key or None
        self.orchestrator_model = orchestrator_model or config.ORCHESTRATOR_MODEL
        self.subagent_model = subagent_model or config.SUBAGENT_MODEL
        # 协调者可见：全部原子工具 + 派发工具
        self.supervisor_tools = atomic_tool_schemas + DISPATCH_TOOLS
=======
        tool_schemas: list[dict],
    ):
        self.call_tool = call_tool
        self.tool_schemas = tool_schemas
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

    async def run(
        self,
        user_id: int,
        conversation_id,
        question: str,
        emit,
<<<<<<< HEAD
        literature_id=None,
    ) -> None:
        """执行一次完整研究任务。emit(event, data) 为 SSE 事件发射器。

        DB 均为同步 pymysql，统一经 asyncio.to_thread 下沉到线程池执行，
        避免阻塞事件循环（SSE 长流期间还有其他并发请求）。
        全流程包在 try 内：任何环节失败都保证 emit error 事件并落库留档，
        不让 SSE 流静默挂死（前端的兜底也依赖这一点）。
        """
        start_ms = time.time()
        # 纯内存结构先于 DB 初始化：异常兜底路径也要能统计到已发生的事件
        events = []
        references = []
        stats = RunStats()
        step_counter = [0]

        def record_step(event_data):
            step_counter[0] += 1
            event_data.setdefault("stepIndex", step_counter[0])
            events.append(event_data)
            emit("step", event_data)

        try:
            conversation = await asyncio.to_thread(
                conversation_store.get_or_create, user_id, conversation_id, question
            )
            conversation_id = conversation["id"]
            emit("start", {"conversationId": conversation_id})

            record_step(
                {
                    "agent": "orchestrator",
                    "type": "agent_start",
                    "toolName": None,
                    "summary": f"开始规划：{question}",
                    "durationMs": None,
                }
            )

            system_prompt = SYSTEM_PROMPT.format(max_steps=config.ORCHESTRATOR_MAX_STEPS)
            # 研究工作区模式：用户正对照原文精读某篇文献，注入定向指引，
            # 让回答优先引用该论文原文证据（子 Agent 的派发任务同样带上该文献）
            workspace_hint = ""
            if literature_id:
                lit = await asyncio.to_thread(
                    conversation_store.get_literature_brief, int(literature_id)
                )
                # 与 MCP 工具同一套可见性规则：私有文献仅属主可注入上下文，
                # 防止用别人的私有 literature_id 探测标题
                if lit and _brief_readable(lit, user_id) and lit.get("title"):
                    workspace_hint = (
                        f"\n\n## 当前工作区上下文\n"
                        f"用户正在研究工作区精读论文《{lit['title']}》(id={literature_id})。"
                        f"回答与该论文相关的问题时：\n"
                        f"- 优先派发 dispatch_read（literature_id={literature_id}）或直接 read_section 查阅原文，"
                        f"不要凭通用知识回答\n"
                        f"- 解释创新点时必须给出原文证据（引用论文中的关键句子/术语/实验数字）\n"
                        f"- 用户问『这篇论文』时默认指这篇文献"
                    )
                    system_prompt += workspace_hint

            # 先取历史再落用户消息：本轮提问不会重复进入上下文，
            # 且执行失败/中断时提问也已留档，历史会话不丢
            history = await asyncio.to_thread(
                conversation_store.history_messages, conversation_id
            )
            await asyncio.to_thread(
                conversation_store.save_user_message, conversation_id, question
            )
            messages = history + [{"role": "user", "content": question}]

            answer = await self._supervisor_loop(
                system_prompt, messages, record_step, references, stats, emit, user_id
            )
            stats.duration_ms = int((time.time() - start_ms) * 1000)
            stats.steps = len(events)
            stats.tool_calls = sum(1 for e in events if e.get("type") == "tool_call")
            message_id = await asyncio.to_thread(
                conversation_store.save_assistant_message,
                conversation_id, answer, events, references, stats.to_dict(),
=======
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
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
<<<<<<< HEAD
        except asyncio.CancelledError:
            # 客户端断开：用户消息已落库，直接终止，不补写轨迹
            raise
        except Exception as e:  # noqa: BLE001 - SSE 顶层兜底
            stats.duration_ms = int((time.time() - start_ms) * 1000)
            stats.steps = len(events)
            stats.tool_calls = sum(1 for ev in events if ev.get("type") == "tool_call")
            # 会话尚未确定（建会话/校验会话归属失败）时无法留档，跳过落库只发 error
            if isinstance(conversation_id, int):
                try:
                    await asyncio.to_thread(
                        conversation_store.save_assistant_message,
                        conversation_id,
                        f"（本轮执行失败：{e}）",
                        events,
                        references,
                        stats.to_dict(),
                    )
                except Exception:  # noqa: BLE001 - 落库失败时仅保留前端提示
                    pass
            emit("error", {"message": f"Agent 执行失败：{e}"})

    # ==================== 工具执行（协调者与子 Agent 共用） ====================

    # 轨迹展示用的入参键（按信息量排序，最多取 3 个）；user_id 是注入的登录态，不展示
    ARG_DISPLAY_KEYS = (
        "query", "goal", "task", "focus", "keyword", "section",
        "literature_id", "direction", "scope", "top_k", "text",
    )

    @classmethod
    def _args_digest(cls, args):
        """工具入参摘要：让轨迹看得出"这一步到底查了什么/派了什么任务"。"""
        parts = []
        for key in cls.ARG_DISPLAY_KEYS:
            value = args.get(key)
            if value in (None, ""):
                continue
            text = str(value).replace("\n", " ").strip()
            if not text:
                continue
            parts.append(f"{key}={text[:60]}")
            if len(parts) >= 3:
                break
        digest = " ".join(parts)
        return digest[:140] or None

    @staticmethod
    def _emit_thinking(record_step, agent_name, reasoning, content):
        """下发一步"思考"：优先取思考模型的 reasoning_content 思维链原文，
        回退到伴随工具调用的 content 文本；都没有则不出轨迹行。"""
        text = (reasoning or "").strip() or (content or "").strip()
        if not text:
            return
        record_step(
            {
                "agent": agent_name,
                "type": "thinking",
                "toolName": None,
                "summary": text[:1000],
                "durationMs": None,
            }
        )

    async def _execute_tool(self, name, args, user_id, record_step, references, stats, agent_name):
        """原子工具统一入口：安全注入登录态 → 调 MCP → 收集引用 → 记录轨迹。"""
        if name == "semantic_search":
            # 安全区：scope/user_id 以服务端登录态为准，
            # 覆盖 LLM 可能幻觉出来的值，防止越权检索他人私库
            args["user_id"] = int(user_id or 0)
            args["scope"] = args.get("scope") if args.get("scope") in ("all", "mine") else "all"
        elif name in ("list_literatures", "get_literature", "read_section", "summarize", "translate"):
            # 文献读取/技能类工具注入登录态：除可见性校验外，
            # translate/summarize/semantic_search 的模型调用也按发起者的自备 Key 计费
            args["user_id"] = int(user_id or 0)

        record_step(
            {
                "agent": agent_name,
                "type": "tool_call",
                "toolName": name,
                "summary": self._args_digest(args),
                "durationMs": None,
            }
        )
        stats.tool_calls += 1
        tool_start = time.time()
        try:
            result = await self.call_tool(name, args)
        except Exception as e:  # noqa: BLE001 - 工具失败降级为错误文本
            result = json.dumps({"error": f"工具执行失败：{e}"}, ensure_ascii=False)
        duration_ms = int((time.time() - tool_start) * 1000)
        self._collect_reference(name, args, result, references)
        record_step(
            {
                "agent": agent_name,
                "type": "tool_result",
                "toolName": name,
                "summary": None,
                "durationMs": duration_ms,
            }
        )
        return result

    # ==================== 子 Agent ====================

    async def _run_sub_agent(self, agent_key, args, user_id, record_step, references, stats):
        """拉起一个专家子 Agent：受限工具集 + 独立步数预算，返回其最终报告。"""
        definition = SUB_AGENTS[agent_key]
        stats.touch_agent(agent_key)

        if agent_key == "search":
            task = definition["prompt"].format(goal=args.get("goal", ""))
        elif agent_key == "reader":
            task = definition["prompt"].format(
                literature_id=args.get("literature_id", "?"), focus=args.get("focus", "方法与创新点")
            )
        else:
            task = definition["prompt"].format(task=args.get("task", ""))

        record_step(
            {
                "agent": agent_key,
                "type": "agent_start",
                "toolName": None,
                "summary": f"{definition['label']}子任务：{task[:60]}",
                "durationMs": None,
            }
        )

        tools = [t for t in self.atomic_tools if t["function"]["name"] in definition["tools"]]
        messages = [{"role": "user", "content": task}]
        answer = await self._react_loop(
            model=self.subagent_model,
            system_prompt=f"你是学术研究团队中的{definition['label']}。",
            messages=messages,
            tools=tools,
            user_id=user_id,
            record_step=record_step,
            references=references,
            stats=stats,
            agent_name=agent_key,
            max_steps=config.SUBAGENT_MAX_STEPS,
            force_summary_prompt="任务步数已达上限。请立即基于以上已收集的信息，直接输出最终报告。",
        )
        return answer or f"（{definition['label']}未返回报告）"

    # ==================== ReAct 引擎 ====================

    async def _react_loop(
        self, model, system_prompt, messages, tools, user_id,
        record_step, references, stats, agent_name, max_steps,
        force_summary_prompt,
    ):
        """通用 ReAct 循环：模型不再调工具时返回其文本；步数耗尽强制生成总结。
        协调者与子 Agent 共用（协调者的派发/finalize 逻辑在 _supervisor_loop 中）。"""
        for step_index in range(max_steps):
            content, tool_calls, usage, reasoning = await llm.chat_with_tools(
                model, system_prompt, messages, tools, api_key=self.api_key
            )
            stats.add_usage(usage)
            self._emit_thinking(record_step, agent_name, reasoning, content)

            if not tool_calls:
                return content or ""

            messages.append(
                {
                    "role": "assistant",
                    "content": content or None,
                    "tool_calls": [
                        {
                            "id": tc["id"],
                            "type": "function",
                            "function": {"name": tc["name"], "arguments": tc["arguments"]},
                        }
                        for tc in tool_calls
                    ],
                }
            )
            for tc in tool_calls:
                try:
                    args = json.loads(tc["arguments"]) if tc["arguments"] else {}
                except json.JSONDecodeError:
                    args = {}
                result = await self._execute_tool(
                    tc["name"], args, user_id, record_step, references, stats, agent_name
                )
                messages.append(
                    {"role": "tool", "tool_call_id": tc["id"], "content": result}
                )

        # 步数耗尽：摘掉工具强制收敛
        answer, usage = await llm.chat_stream(
            model, system_prompt, messages, lambda _t: None, api_key=self.api_key
        )
        stats.add_usage(usage)
        return answer or ""

    # ==================== 协调者循环 ====================

    async def _supervisor_loop(
        self, system_prompt, messages, record_step, references, stats, emit, user_id
    ):
        for step_index in range(config.ORCHESTRATOR_MAX_STEPS):
            content, tool_calls, usage, reasoning = await llm.chat_with_tools(
                self.orchestrator_model,
                system_prompt,
                messages,
                self.supervisor_tools,
                api_key=self.api_key,
            )
            stats.add_usage(usage)
            self._emit_thinking(record_step, "orchestrator", reasoning, content)

            if not tool_calls:
                # 模型未调工具直接输出：视为收敛，流式补发
                return await self._final_answer(system_prompt, messages, emit, stats, record_step, step_index)
=======
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
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

            messages.append(
                {
                    "role": "assistant",
                    "content": content or None,
                    "tool_calls": [
                        {
                            "id": tc["id"],
                            "type": "function",
<<<<<<< HEAD
                            "function": {"name": tc["name"], "arguments": tc["arguments"]},
=======
                            "function": {
                                "name": tc["name"],
                                "arguments": tc["arguments"],
                            },
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
<<<<<<< HEAD
                    # OpenAI 兼容接口要求 assistant.tool_calls 的每次调用都有对应的 tool 响应消息，
                    # 缺失时下一轮（流式收尾）请求可能被 400 拒绝，这里补齐合法消息序列
                    messages.append(
                        {
                            "role": "tool",
                            "tool_call_id": tc["id"],
                            "content": json.dumps(
                                {"ok": True, "message": "研究阶段结束，请生成最终回答"},
                                ensure_ascii=False,
                            ),
                        }
                    )
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
                    continue
                try:
                    args = json.loads(tc["arguments"]) if tc["arguments"] else {}
                except json.JSONDecodeError:
                    args = {}
<<<<<<< HEAD

                if name in DISPATCH_TARGET:
                    record_step(
                        {
                            "agent": "orchestrator",
                            "type": "tool_call",
                            "toolName": name,
                            "summary": self._args_digest(args),
                            "durationMs": None,
                        }
                    )
                    stats.tool_calls += 1
                    dispatch_start = time.time()
                    try:
                        result = await self._run_sub_agent(
                            DISPATCH_TARGET[name], args, user_id, record_step, references, stats
                        )
                    except Exception as e:  # noqa: BLE001 - 子 Agent 失败降级，协调者可换路径
                        result = json.dumps(
                            {"error": f"子 Agent 执行失败：{e}"}, ensure_ascii=False
                        )
                    record_step(
                        {
                            "agent": "orchestrator",
                            "type": "tool_result",
                            "toolName": name,
                            "summary": None,
                            "durationMs": int((time.time() - dispatch_start) * 1000),
                        }
                    )
                else:
                    result = await self._execute_tool(
                        name, args, user_id, record_step, references, stats, "orchestrator"
                    )
                messages.append({"role": "tool", "tool_call_id": tc["id"], "content": result})

            if finalized:
                return await self._final_answer(system_prompt, messages, emit, stats, record_step, step_index)
=======
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
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

        # 步数耗尽：强制收敛
        return await self._final_answer(
            system_prompt, messages, emit, stats, record_step, config.ORCHESTRATOR_MAX_STEPS
        )

    async def _final_answer(self, system_prompt, messages, emit, stats, record_step, step_index):
        record_step(
            {
                "agent": "orchestrator",
<<<<<<< HEAD
=======
                "stepIndex": step_index,
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
                "type": "thinking",
                "toolName": "finalize",
                "summary": "信息收集完成，生成最终回答",
                "durationMs": None,
            }
        )
        messages.append(
            {
                "role": "user",
<<<<<<< HEAD
                "content": "信息收集阶段已结束。请基于以上所有已收集的信息（含各专家报告），直接给出面向用户的完整最终回答。",
=======
                "content": "信息收集阶段已结束。请基于以上所有已收集的信息，直接给出面向用户的完整最终回答。",
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
            }
        )

        def on_token(token):
            emit("token", {"content": token})

        answer, usage = await llm.chat_stream(
<<<<<<< HEAD
            self.orchestrator_model, system_prompt, messages, on_token, api_key=self.api_key
=======
            config.ORCHESTRATOR_MODEL, system_prompt, messages, on_token
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
        )
        stats.add_usage(usage)
        return answer or "（未生成回答）"

    @staticmethod
<<<<<<< HEAD
    def _collect_reference(tool_name, args, result, references):
        """从工具结果与入参提取参考文献（按 id 去重，标题后到先补）。

        之前只从 semantic_search 提取：某轮若主要使用 read_section / summarize /
        dispatch_read，final 的参考文献会是空的。现在 get_literature 从返回 JSON
        取 id+title；read_section / summarize 返回纯文本，改从入参取 literature_id
        （标题通常已由同 id 的 get_literature / semantic_search 先行补齐）。
        """
        if tool_name == "semantic_search":
            try:
                data = json.loads(result)
            except (json.JSONDecodeError, TypeError):
                return
            for item in data.get("results", []):
                ref_id = item.get("literatureId")
                if ref_id is None:
                    continue
                Orchestrator._upsert_reference(references, ref_id, item.get("title"))
            return

        ref_id, title = None, None
        if tool_name == "get_literature":
            try:
                data = json.loads(result)
            except (json.JSONDecodeError, TypeError):
                return
            if "error" not in data:
                ref_id, title = data.get("id"), data.get("title")
        elif tool_name in ("read_section", "summarize"):
            raw = args.get("literature_id")
            try:
                ref_id = int(raw) if raw is not None else None
            except (TypeError, ValueError):
                ref_id = None
        if ref_id is not None:
            Orchestrator._upsert_reference(references, ref_id, title)

    @staticmethod
    def _upsert_reference(references, ref_id, title):
        for r in references:
            if r["id"] == ref_id:
                if title and not r.get("title"):
                    r["title"] = title
                return
        references.append({"id": ref_id, "title": title})
=======
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
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
