# 系统设计文档（DESIGN）

<<<<<<< HEAD
> 项目：基于多模态大模型的学术文献精读与科研创新辅助系统（AcademicSearch）
> 版本：v2.1（2026-10-27 与当前代码对齐）
> 关联文档：[README.md](./README.md)（当前实现基线）· [ROADMAP.md](./ROADMAP.md)（实施路线）· [USER_MANUAL.md](./USER_MANUAL.md)（用户手册）
>
> **定位说明**：项目立项名含"多模态"，但当前实现为**纯文本链路**（PDF 文本抽取 + 文本大模型），图表/扫描件视觉理解尚未落地，属于后续规划。

## 1. 系统概述

面向科研人员的学术文献检索、精读与智能问答平台。用户上传 PDF 学术文献后，系统自动解析全文、切分文本块并建立向量索引（Milvus），同时用大模型抽取结构化知识要点；用户既可做经典语义检索，也可通过 **AI 研究副驾（Agent）** 用自然语言完成文献调研、精读、翻译与总结。

Agent 采用 **Supervisor + 3 个专家子 Agent** 的协作模式，通过 MCP（Model Context Protocol）工具集访问真实文献，实时流式输出执行轨迹与最终回答。文献默认私有，用户可自愿贡献到社区总知识库，形成"私有库 + 社区库"双知识库。
=======
> 项目：基于多模态大模型的学术文献精读与科研创新辅助系统
> 版本：v2.0（2026-09-07 架构重构定稿）
> 关联文档：[ROADMAP.md](./ROADMAP.md)（实施路线图）· [USER_MANUAL.md](./USER_MANUAL.md)（用户手册）

## 1. 系统概述

面向科研人员的学术文献检索、精读与智能问答平台。用户上传 PDF 学术文献后，系统自动解析全文、切分文本块并建立向量索引（Milvus）；用户既可做经典语义检索，也可通过 **AI 研究副驾（Agent）** 用自然语言完成文献调研、精读、翻译与总结——Agent 通过 MCP（Model Context Protocol）工具集自主规划检索路径，实时流式输出执行轨迹与最终回答。
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

### 1.1 核心能力

| 能力 | 说明 |
|------|------|
<<<<<<< HEAD
| 文献管理 | PDF 上传（50MB 上限）、标题/摘要启发式抽取、后台向量索引、LLM 知识抽取、重新解析、删除 |
| 双知识库 | 默认私有；自愿公开到社区总知识库；总库按 `is_public`、私库按 `user_id` 隔离；社区板块筛选 |
| 用户私有状态 | 收藏、已读、阅读笔记（`user_literature_state`），详情页引用格式复制 |
| 语义检索 | 向量相似度（HNSW + COSINE）匹配文献，返回 topK 命中与文本片段；支持总库/私库 |
| 研究工作区 | 全屏精读界面（/workspace/:id，阅读模式已并入）：左侧 pdf.js 长卷（懒加载/上下翻页/跳页/下载）、目录为默认收起的左侧抽屉（书签优先）、右上 AI 批注卡、右下模型对话、左下批注工具 dock（文本批注/颜色/创新点） |
| Agent 问答 | Supervisor 调度原子工具与检索/精读/分析专家，MCP 工具集支撑，SSE 流式输出 |
| 会话记忆 | 多轮上下文、执行轨迹回放、运行统计（token/步数/耗时）、Markdown 导出 |
| 安全认证 | JWT 无状态认证（Java 签发，Python 共验）、图形验证码、登录限流、会话与私有文献按用户隔离 |
=======
| 文献管理 | PDF 上传（50MB 上限）、元数据自动抽取（标题/摘要/作者/期刊/年份/DOI）、文献库增删查 |
| 语义检索 | 向量相似度（HNSW + COSINE）匹配文献，返回 topK 命中与文本片段 |
| Agent 问答 | 中心化编排器（Orchestrator-Centric）驱动 ReAct 循环，MCP 工具集支撑检索/精读/翻译/总结，SSE 流式输出 |
| 会话记忆 | 多轮对话上下文、执行轨迹回放、运行统计（token/步数/耗时）、Markdown 导出 |
| 安全认证 | JWT 无状态认证（Java 签发，Python 共验）、多用户文献与会话数据隔离 |
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

### 1.2 技术栈

| 层 | 技术 |
|----|------|
<<<<<<< HEAD
| 前端 | React 18 · Vite 5 · React Router 6 · 原生 fetch（SSE 流式解析）· Context 状态管理 · 深/浅/护眼三主题 · pdf.js |
| Java 后端 | Java 21 · Spring Boot 3.2 · Spring Security + JWT (jjwt) · Spring Data JPA · Apache PDFBox 3.0.2 · OkHttp · hutool-captcha |
| Python Agent 服务 | Python 3.12 · FastAPI · uvicorn · openai SDK（DashScope 兼容模式）· mcp 1.x · pyjwt · pymysql · pymilvus 2.5 |
| 向量数据库 | Milvus 2.5（standalone，Docker Compose：etcd + MinIO + milvus） |
| 业务数据库 | MySQL 8.0（库名 example_db） |
| 大模型 | 阿里云百炼：qwen3.8-flash（思考模型，全部对话链路——协调者/子 Agent/技能/经典 RAG/知识抽取，走 OpenAI 兼容端点；流式收尾与技能自动关闭思考）· text-embedding-v1（向量化） |
=======
| 前端 | React 18 · Vite 5 · React Router 6 · 原生 fetch（SSE 流式解析）· Context 状态管理 · 暗/亮主题 |
| Java 后端 | Java 21 · Spring Boot 3.2 · Spring Security + JWT (jjwt) · Spring Data JPA · Apache PDFBox 3.0.2 · OkHttp |
| Python Agent 服务 | Python 3.12 · FastAPI · uvicorn · openai SDK（DashScope 兼容模式）· mcp 1.x · pyjwt · pymysql |
| 向量数据库 | Milvus 2.5（standalone，Docker Compose：etcd + MinIO + milvus） |
| 业务数据库 | MySQL 8.0（库名 example_db） |
| 大模型 | 阿里云百炼：qwen-plus（编排器）· qwen-turbo（技能）· text-embedding-v1（向量化） |
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

## 2. 总体架构

系统由三个独立服务 + 两个存储组成，前后端分离、Agent 服务独立进程：

```
┌──────────────────┐  /api       ┌──────────────────────┐
│  React SPA        │ ──────────► │  Java Spring Boot    │
│  (Vite :5173)     │  /agent-api │  (Tomcat :8080)      │
│                   │ ──────┐    │  认证/文献/PDF解析/   │
│  CopilotPanel     │       │    │  切分/embedding/经典RAG│
└──────────────────┘       │    └──────────┬───────────┘
                           ▼               │ 写入向量/查询元数据
              ┌────────────────────┐       │
              │  Python FastAPI     │       ▼
              │  (uvicorn :8000)    │  ┌────────┐   ┌────────┐
<<<<<<< HEAD
              │  Supervisor 编排器  │  │ MySQL  │   │ Milvus │
              │  + 3 专家子 Agent   │  │ 业务数据│   │ 向量索引│
              │  JWT 鉴权/会话CRUD  │  └────────┘   └────────┘
              │  SSE 流式端点       │        ▲              ▲
              └────────┬─────────┘        │              │
=======
              │  中心化编排器        │  │ MySQL  │   │ Milvus │
              │  JWT 鉴权/会话CRUD  │  │ 业务数据│   │ 向量索引│
              │  SSE 流式端点       │  └────────┘   └────────┘
              └────────┬─────────┘        ▲              ▲
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
                       │ stdio (MCP协议)  │              │
                       ▼                 │              │
              ┌────────────────────┐     │              │
              │  MCP Server（唯一）  │────┴──────────────┘
<<<<<<< HEAD
              │  7 原子工具 +       │  工具实现直连 MySQL/Milvus
              │  finalize           │
=======
              │  七工具：检索/精读/  │  工具实现直连 MySQL/Milvus
              │  翻译/总结/finalize │
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
              └────────────────────┘
```

**职责边界**：
<<<<<<< HEAD

- **Java 后端（8080）**：用户认证（验证码/注册/登录/刷新/me/登录限流）、文献上传与解析、文本切分、embedding 生成、语义检索、双知识库可见性、用户私有状态、经典 RAG 降级接口（`/api/rag/query`）
- **Python Agent 服务（8000）**：Supervisor 编排、3 个专家子 Agent、MCP 客户端生命周期、会话三表 CRUD、SSE 流式推送；复用 Java 签发的 JWT（共享密钥验签）
- **MCP Server（独立子进程，stdio）**：7 个原子工具的唯一载体；由 Python 主服务拉起，通过 MCP 协议交互
- **前端（5173）**：Vite 开发代理 `/api` → 8080、`/agent-api` → 8000（rewrite 去前缀）；游客可浏览首页/检索页，其余功能页登录守卫

> Java 与 Python 之间**没有 HTTP 互调**：两者各自直连 MySQL 与 Milvus，仅通过共享 `JWT_SECRET` 与数据库结构协作。
=======
- **Java 后端（8080）**：用户认证（注册/登录/刷新/me）、文献上传与解析、文本切分、embedding 生成、经典 RAG 降级接口（`/api/rag/query`）
- **Python Agent 服务（8000）**：Agent 编排、MCP 客户端生命周期、会话三表 CRUD、SSE 流式推送；复用 Java 签发的 JWT（共享密钥验签）
- **MCP Server（独立子进程，stdio）**：全部 Agent 工具的唯一载体；由 Python 主服务拉起，通过 MCP 协议交互
- **前端（5173）**：Vite 开发代理 `/api` → 8080、`/agent-api` → 8000（rewrite 去前缀）
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

## 3. RAG 数据流

```
<<<<<<< HEAD
上传阶段（Java，异步线程池 2 线程）：
  PDF → PDFBox 提取全文
      → 元数据：标题=首行 5-200 字；摘要=Abstract/摘要 到 Keywords/Introduction 之间
                  作者/期刊/年份/DOI 由上传表单提供
      → 文本切分（500 字/块，100 字重叠，句子边界对齐）
      → DashScope text-embedding-v1 批量向量化（10 条/批）→ Milvus literature_chunks
      → LLM 结构化知识抽取（头 8000 字 + 尾 4000 字）→ main_content/methods/experiments/conclusions
      → 封面横幅标题修复 → process_status=READY

检索阶段（Java / MCP semantic_search）：
  用户问题 → embedding → Milvus HNSW 检索（COSINE）
  → 按 scope 过滤：all=仅 is_public；mine=当前 user_id
  → 按文献去重（每篇取最高分块）→ 返回文献信息 + 匹配片段 + 分数

回答阶段：
  经典 RAG：每篇文献「标题+作者+摘要+最高分两块」拼上下文 → qwen3.8-flash 生成
  Agent：Supervisor 调用原子工具/专家子 Agent 收集证据 → finalize → qwen3.8-flash 流式收尾
```

**可见性规则**：

- `is_public = true`：社区总知识库，所有登录用户可检索/阅读
- `is_public = false`：仅上传者本人可见；他人访问详情/原文/Agent 工具统一返回"不存在或未公开"（404 语义）
- 历史数据 `is_public IS NULL`：视为公开（字段引入前即为共享）

## 4. Agent 架构（Supervisor + 专家子 Agent + 唯一 MCP）
=======
上传阶段（Java）：
  PDF 文件 → PDFBox 提取全文 → 元数据抽取（标题=首行5-200字；摘要=Abstract/摘要
  到 Keywords/Introduction 之间）→ 文本切分（500字/块，100字重叠，句子边界对齐）
  → DashScope text-embedding-v1 批量向量化（10条/批）→ Milvus literature_chunks

检索阶段（MCP 工具 semantic_search）：
  用户问题 → embedding → Milvus HNSW 检索（COSINE，top_k）
  → 按 literature_id 查 MySQL 补全元数据 → 返回 {文献信息, 匹配片段, 分数}
```

## 4. Agent 架构（中心化编排 + 唯一 MCP）
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

### 4.1 设计决策

| 决策 | 选型 | 理由 |
|------|------|------|
<<<<<<< HEAD
| 编排模式 | **Supervisor + 3 专家子 Agent** | 协调者负责规划与整合；检索/精读/分析各自使用受限工具集与独立提示词，降低单 Agent 的上下文负担与工具误用 |
| 递归控制 | **深度=2，禁止递归派生** | 子 Agent 不可见 dispatch 工具，避免无限套娃与 token 爆炸 |
| 工具协议 | **MCP（stdio 传输）** | 工具定义与调用标准化；只维护一个 MCP Server，Java/Python 不各自散落工具 |
| 循环范式 | **ReAct + finalize 协议** | 协调者每轮必须调工具；`finalize` 结束信号；协调者 8 步、子 Agent 4 步强制兜底 |
| 流式协议 | **SSE**（start → step* → token* → final/error） | 前端逐事件渲染轨迹与打字机回答；断连静默、后台仍持久化 |
| 身份注入 | **服务端强制覆盖 user_id/scope** | 防止模型幻觉出越权参数，读取私有文献前统一做可见性校验 |

### 4.2 Agent 拓扑

```
Supervisor（qwen3.8-flash，≤8 步）
├─ 原子工具：semantic_search / list_literatures / get_literature / read_section
│            / translate / summarize / finalize
└─ 派发工具：
   ├─ dispatch_search  → 检索专家（≤4 步）：semantic_search、list_literatures
   ├─ dispatch_read    → 精读专家（≤4 步）：get_literature、read_section
   └─ dispatch_analyze → 分析专家（≤4 步）：get_literature、read_section、summarize、translate
```

- 简单单步查询由协调者直接调用原子工具；多关键词调研、深入精读、横向对比才派发专家
- 子 Agent 复用同一 ReAct 引擎，但只看到自己的工具子集与精简系统提示
- 子 Agent 报告作为 `tool` 消息回填协调者，协调者可继续追问其他专家或补充检索

### 4.3 MCP Server 工具集（mcp_server.py）

| 工具 | 签名 | 功能 | 数据源 |
|------|------|------|--------|
| `semantic_search` | `query, top_k=5, scope="all", user_id=0` | 语义检索文献（含匹配片段与分数）；`top_k` 上限 10；`scope=mine` 搜当前用户私库 | Milvus + MySQL |
| `list_literatures` | `keyword="", user_id=0` | 文献库清单（标题/作者过滤，最多 50 条；含社区公开 + 自己的私有） | MySQL |
| `get_literature` | `literature_id, user_id=0` | 单篇元数据与摘要全文（私有校验） | MySQL |
| `read_section` | `literature_id, keyword="", max_chars=1500, user_id=0` | 按关键词定位正文片段（200–4000 字；无关键词返回开头） | MySQL |
| `translate` | `text, target_language="中文"` | 中英互译（学术术语保持） | qwen3.8-flash |
| `summarize` | `literature_id, style="structured", user_id=0` | 结构化总结（问题/方法/实验/结论/局限）或 `plain` 一段话；取正文前 6000 字 | qwen3.8-flash + MySQL |
| `finalize` | — | 研究阶段结束信号，触发流式最终回答 | — |

**工具结果约束**：统一 JSON 序列化，压缩到 `MAX_TOOL_RESULT_CHARS = 4000`；`semantic_search` 超长时先压缩 `abstract`/`matchedText`，仍超限则丢弃尾部条目，**始终保持 JSON 合法**，避免编排器解析 references 失败。

### 4.4 编排循环（orchestrator.py）

```
输入: question + conversationId + 可选 literatureId
历史: 最近 6 轮，单条截断 2000 字
工作区: literatureId 存在时，向 system prompt 注入“优先精读该论文”的定向指引
配置: 每次会话按 user_id 读取 users 表的个人 API Key / 协调者模型 / 子 Agent 模型（缺省回退服务器 .env）

循环 (协调者 ≤ 8 步):
  1. 调 qwen3.8-flash（携带原子工具 + 派发工具 schema）
  2. 有 thinking 文本 → 发 step(thinking) 事件
  3. 解析 tool_calls → 逐个执行:
       - finalize → 标记收敛，跳出
       - dispatch_* → 发 step(tool_call) → 拉起子 Agent（≤4 步）→ 发 step(tool_result)
       - 原子工具 → 注入 user_id/scope → MCP 调用 → 发 step(tool_result, 耗时)
       - semantic_search 结果同时提取参考文献
  4. 工具结果以 role=tool 回填消息，进入下一轮
收敛后:
  追加收尾指令 → qwen3.8-flash 流式生成最终回答
  token 逐个经 SSE 透传 → 持久化完整一轮（消息+轨迹+统计）
```

**统计口径**：`final` 事件携带 stats（轨迹步数/工具调用数/参与 Agent/总耗时/input+output+total tokens，DashScope usage 跨轮累计，含流式收尾），持久化至 `agent_message.stats_json`。

### 4.5 提示词约束要点

- 协调者每轮必须调用一个工具，信息足够后调 `finalize`
- 子 Agent 必须至少调用一次领域工具获得真实结果后才能输出报告，禁止无依据直接输出
- 回答事实必须来自工具/专家报告返回的真实文献内容，禁止编造；引用标注来源
- 多轮对话结合上下文消解指代（如"第二篇"）
- 工具失败时换关键词/换工具/换专家重试，不直接放弃
=======
| 编排模式 | **Orchestrator-Centric（中心化编排）** | 单一编排器直接持有全部工具，控制流集中、调试直观；相比多 Agent 分派减少了递归调度复杂度与 token 开销 |
| 工具协议 | **MCP（stdio 传输）** | 工具定义与调用标准化，工具服务可独立演进；仅开发一个 MCP Server，避免工具散落 |
| 循环范式 | **ReAct + finalize 协议** | 模型每轮必须调工具（含 `finalize` 结束信号）；步数上限 8 强制兜底收敛 |
| 流式协议 | **SSE**（start → step* → token* → final/error） | 前端逐事件渲染轨迹与打字机回答；断连静默、后台仍持久化 |

### 4.2 MCP Server 工具集（mcp_server.py）

| 工具 | 功能 | 数据源 |
|------|------|--------|
| `semantic_search(query, top_k)` | 语义检索文献（含匹配片段与分数） | Milvus + MySQL |
| `list_literatures(keyword)` | 文献库清单（可按标题/作者过滤） | MySQL |
| `get_literature(literature_id)` | 单篇元数据与摘要全文 | MySQL |
| `read_section(literature_id, keyword, max_chars)` | 按关键词定位阅读正文片段（≤4000字） | MySQL |
| `translate(text, target_language)` | 中英互译（学术术语保持） | qwen-turbo |
| `summarize(literature_id, style)` | 结构化总结（问题/方法/实验/结论/局限）或一段话总结 | qwen-turbo + MySQL |
| `finalize()` | 研究阶段结束信号，触发流式最终回答 | — |

工具结果统一 JSON 序列化并截断（≤4000 字符），防止超长上下文。

### 4.3 编排器循环（orchestrator.py）

```
输入: question + 会话历史（最近6轮，单条截断2000字）
循环 (≤ 8 步):
  1. 调 qwen-plus（携带全部工具 schema）
  2. 有 thinking 文本 → 发 step(thinking) 事件
  3. 解析 tool_calls → 逐个执行:
       - finalize → 标记收敛，跳出
       - 其他 → 发 step(tool_call) → MCP 调用 → 发 step(tool_result, 耗时)
       - semantic_search 结果同时提取参考文献列表
  4. 工具结果以 role=tool 回填消息，进入下一轮
收敛后:
  追加收尾指令 → qwen-plus 流式生成最终回答
  token 逐个经 SSE 透传 → 持久化完整一轮（消息+轨迹+统计）
```

**统计口径**：`final` 事件携带 stats（轨迹步数/工具调用数/总耗时/input+output+total tokens，DashScope usage 跨轮累计，含流式收尾），持久化至 `agent_message.stats_json`。

### 4.4 提示词约束要点（编排器 system prompt）

- 每轮必须调用一个工具，信息足够后调 `finalize`
- 回答事实必须来自工具返回的真实文献内容，禁止编造；引用标注来源
- 多轮对话结合上下文消解指代（如"第二篇"）
- 工具失败时换关键词/换工具重试，不直接放弃
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

## 5. 数据存储设计

### 5.1 MySQL 表（example_db）

| 表 | 用途 | 关键字段 |
|----|------|---------|
<<<<<<< HEAD
| `users` | 用户 + 智能体配置 | id, username, email(唯一), password(BCrypt), dashscope_api_key(明文), chat_model, subagent_model |
| `literature` | 文献元数据与全文 | id, title, authors, abstract_text, journal, year, doi, file_name, file_path, content(LONGTEXT), process_status, main_content, methods, experiments, conclusions, user_id, is_public, category, created_at |
| `conversation` | Agent 会话 | id, user_id, title(首问前24字), created_at, updated_at |
| `agent_message` | 会话消息 | id, conversation_id, role(user/assistant), content, references_json, stats_json, created_at |
| `agent_step` | 执行轨迹 | id, message_id, agent_name, step_index, step_type(agent_start/thinking/tool_call/tool_result), tool_name, content, duration_ms, created_at |
| `user_literature_state` | 用户对文献的私有状态 | id, user_id + literature_id(唯一), starred, read_status, note, updated_at |

> 历史表 `vector_index`（MySQL JSON 向量）已废弃，确认无数据后可删除。
=======
| `users` | 用户 | id, username, email(唯一), password(BCrypt) |
| `literature` | 文献元数据与全文 | id, title, authors, abstract_text, journal, year, doi, file_name, file_path, content(LONGTEXT), user_id, created_at |
| `conversation` | Agent 会话 | id, user_id, title(首问前24字), created_at, updated_at |
| `agent_message` | 会话消息 | id, conversation_id, role(user/assistant), content, references_json, stats_json, created_at |
| `agent_step` | 执行轨迹 | id, message_id, agent_name, step_index, step_type(agent_start/thinking/tool_call/tool_result), tool_name, content, duration_ms, created_at |

> 历史表 `vector_index`（MySQL JSON 向量）保留至 Milvus 迁移验收后删除。
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

### 5.2 Milvus Collection：literature_chunks

| 字段 | 类型 | 说明 |
|------|------|------|
| chunk_id | Int64 | 主键，auto_id |
| literature_id | Int64 | 关联文献 |
| chunk_index | Int64 | 块序号 |
| chunk_text | VarChar(2048) | 文本块内容 |
| embedding | FloatVector(1536) | HNSW 索引（M=16, efConstruction=200），COSINE 度量，检索 ef=64 |

数据卷持久化于 `milvus/volumes/`（D 盘）；Java 与 Python 双端结构定义保持一致（`MilvusVectorStore.java` ↔ `milvus_store.py`）。

## 6. 接口设计

### 6.1 Java 后端（/api，JWT 保护除 /api/auth/**）

| 接口 | 方法 | 说明 |
|------|------|------|
<<<<<<< HEAD
| `/api/auth/captcha` | GET | 图形验证码（captchaId + base64 图片） |
| `/api/auth/register` `/login` `/refresh` `/me` | POST/GET | 注册/登录/刷新令牌/当前用户 |
| `/api/literature/upload` | POST | 上传 PDF（multipart，属主绑定，默认私有） |
| `/api/literature?scope=all\|mine&board=` | GET | 我的文献（mine）/社区公开文献（all）列表；文献库只用 mine，社区页用 all（无分页） |
| `/api/literature/recommendations?topK=` | GET | 社区推荐：我的最近 3 篇文献构造查询 → embedding → 在他人公开文献中向量召回 |
| `/api/literature/{id}` | GET/DELETE | 详情/删除（属主校验，连带删向量与文件） |
| `/api/literature/{id}/file?download=` | GET | 原文 PDF inline/attachment |
| `/api/literature/{id}/state` | PUT | 收藏/已读/笔记部分更新 |
| `/api/literature/{id}/visibility` | PUT | 公开到社区/设为私有（仅属主） |
| `/api/literature/{id}/reprocess` | POST | 重跑知识抽取并修复标题（仅属主） |
| `/api/literature/search?query=&topK=&scope=` | GET | 语义检索 |
| `/api/rag/query` | GET/POST | 经典 RAG 降级路径（单次检索+生成） |
| `/api/settings` | GET/PUT | 用户级智能体配置：个人 DashScope API Key（明文存储/返回）、协调者模型、子智能体模型；留空回退服务器默认 |
=======
| `/api/auth/register` `/login` `/refresh` `/me` | POST/GET | 注册/登录/刷新令牌/当前用户 |
| `/api/literature/upload` | POST | 上传 PDF（multipart，属主绑定） |
| `/api/literature` | GET | 文献列表 |
| `/api/literature/{id}` | GET/DELETE | 详情/删除（属主校验，连带删向量） |
| `/api/literature/search` | GET | 语义检索（q + topK） |
| `/api/rag/query` | GET/POST | 经典 RAG 降级路径（单次检索+生成） |
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

### 6.2 Python Agent 服务（/agent-api，全部 JWT 保护）

| 接口 | 方法 | 说明 |
|------|------|------|
<<<<<<< HEAD
| `/agent/chat` | POST | SSE：`start → step* → token* → final/error`；body `{question, conversationId?, literatureId?}` |
=======
| `/agent/chat` | POST | SSE：`start → step* → token* → final/error` |
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
| `/agent/conversations` | GET | 会话列表（按 updated_at 倒序） |
| `/agent/conversations/{id}/messages` | GET | 历史消息（含轨迹与统计，回放用） |
| `/agent/conversations/{id}` | PATCH/DELETE | 重命名（≤60字）/删除（属主校验） |
| `/health` | GET | 健康检查（含 MCP 连接状态） |

统一响应体 `{code, message, data}`；鉴权失败 401，越权/不存在 404。

## 7. 安全设计

<<<<<<< HEAD
- **认证**：JWT（HMAC——Java 端 jjwt 按密钥长度自动选 HS256/384/512），Java 签发（subject=email，access 24h / refresh 7d），Python 端 pyjwt 接受 HMAC 全族验签后按 email 查 `users` 表；`JWT_SECRET` 不足 32 字节时两端拒绝启动
- **注册防刷**：图形验证码 5 分钟有效、一次性、连错 5 次作废（仅注册强制）
- **登录防爆破**：同一邮箱 15 分钟窗口内连续失败 5 次锁定 15 分钟（内存实现，单实例有效）
- **密码**：BCrypt 加密存储
- **授权**：
  - 会话（conversation/agent_message/agent_step）全链路按 `user_id` 隔离，跨用户访问返回 404
  - 文献：`is_public=true` 全员可读；私有文献仅属主可读/删/切换可见性；越权统一 404，不泄露存在性
  - 用户状态：`user_literature_state` 按 `(user_id, literature_id)` 隔离
  - Agent：`user_id/scope` 由服务端登录态注入；MCP 工具读取私有文献前统一校验
- **用户自备 Key（明文）**：`users.dashscope_api_key` 按产品要求**明文存储、明文回显**（不做掩码/加密），Python Agent 每次会话按 `user_id` 读取并仅为该用户构建 LLM 客户端；未配置时回退服务器 `.env` 默认 Key
  - 风险提示：明文意味着可直接从页面/数据库读出完整 Key，仅适合本人可信设备使用；泄露后应立即在百炼控制台轮换
=======
- **认证**：JWT（HS256），Java 签发（subject=email），Python 端 pyjwt 共享 `JWT_SECRET` 验签后按 email 查 `users` 表；access token 过期前端自动用 refresh token 续期并重试
- **授权**：文献与会话的增删查全部带属主校验（user_id 匹配），跨用户访问返回 404
- **密码**：BCrypt 加密存储
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
- **CORS**：白名单（5173/4173）双端一致
- **传输**：MCP 走本机 stdio，不出网络边界

## 8. 部署架构

<<<<<<< HEAD
### 8.1 开发环境

=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
```
开发机/服务器
├─ frontend-react   (npm run dev / build 后静态托管)   :5173
├─ backend          (mvn spring-boot:run / java -jar)  :8080
├─ agent-python     (python main.py)                   :8000
│    └─ mcp_server.py（子进程，stdio）
├─ milvus/          (docker compose up -d)             :19530 / 9091 / 9000-9001
└─ MySQL80                                             :3306
<<<<<<< HEAD
```

启动顺序：MySQL → Milvus → Java 后端 → Python Agent → 前端。
Java 端 Milvus 客户端为 `@Lazy` 惰性连接，Milvus 未启动不阻塞后端启动，但上传/检索/Agent 检索需要 Milvus 就绪。

### 8.2 生产部署（建议，当前仓库未提供）

- Nginx 统一入口：`/` 托管 `frontend-react/dist` 并 SPA fallback；`/api` → `127.0.0.1:8080`；`/agent-api` → `127.0.0.1:8000`（去前缀）
- Java fat jar 与 Python Agent 使用 systemd 管理，实现开机自启与崩溃重启
- Milvus 继续用 docker compose；16G / 4 核服务器可承载 Milvus（3–4G）+ Java（1G）+ Python/MySQL/系统（约 2G）
- 国内服务器需配置 Docker Hub / Maven / pip / npm 镜像加速

环境变量集中于项目根 `.env`：`DASHSCOPE_API_KEY`、`DB_USERNAME/PASSWORD`、`JWT_SECRET`、`MILVUS_URI`、`MILVUS_EMBEDDING_DIM`、`ORCHESTRATOR_MODEL/MAX_STEPS`、`HISTORY_ROUNDS`、`SUBAGENT_MODEL/MAX_STEPS`、`SKILL_MODEL`。

## 9. 质量保障

- **单元测试**：Java 侧 10 个测试类，覆盖文本切分、摘要抽取、验证码、登录限流、文献可见性、用户状态、文件读取、JWT 过滤器、知识抽取与应用；Python 侧 `selfcheck.py` 手工自检（模块导入/DB 连接/MCP stdio 握手与工具调用 roundtrip），无自动化测试
- **工具失败降级**：MCP 工具异常不中断对话，返回错误 JSON 由协调者换工具/换关键词/换专家重试；子 Agent 失败不炸整体。LLM 调用遇 429 按 5s/15s/30s 指数退避重试（优先遵循服务端 `Retry-After`）
- **失败留档**：用户消息即时落库，执行失败/中断的轮次在历史会话中可见
- **降级路径**：Agent 服务不可用时经典 RAG（`/api/rag/query`）仍可独立提供检索问答
- **可观测**：SSE 全事件流 + `agent_step` 轨迹落库，任意一轮对话可完整回放

## 10. 已知限制（与代码现状一致）

1. **研究工作区上下文未接通**：后端 `/agent/chat` 与编排器支持 `literatureId`，但前端 `WorkspaceView`/`api.agentChat` 尚未透传，工作区对话暂按普通会话处理
2. **references 仅来自 `semantic_search`**：使用 `read_section`/`summarize`/`dispatch_read` 为主的轮次可能没有参考文献
3. **无分页**：文献列表一次返回全部，前端客户端排序/筛选 + 无限滚动模拟
4. **检索过滤表达式随文献量增长**：当前按可见文献 id 列表拼 `literature_id in [...]`，大规模库需改为 Milvus 标量字段过滤
5. **多模态未落地**：无 OCR、图表理解；扫描版 PDF 无法抽取文本
6. **认证状态为单实例内存**：验证码池与登录限流重启清零、无法多实例
7. **无 RBAC/管理员**：仅普通用户；无管理后台
8. **Python 无自动化测试、无 CI**：仅手工 selfcheck
9. **生产反代与进程管理未入库**：Nginx/systemd 配置尚未提供
=======

启动顺序：MySQL → Milvus → Java 后端 → Python Agent → 前端
（Java 端 Milvus 客户端为 @Lazy 惰性连接，Milvus 未启动不阻塞后端启动）
```

环境变量集中于项目根 `.env`：`DASHSCOPE_API_KEY`、`DB_USERNAME/PASSWORD`、`JWT_SECRET`、`MILVUS_URI`。

## 9. 质量保障

- **单元测试**：Java 侧 10 项（文本切分、摘要抽取、JWT、文献属主等）；Python 侧 selfcheck.py（模块导入/DB 连接/MCP stdio 握手与工具调用 roundtrip）
- **工具失败重试**：MCP 工具异常自动重试 1 次，仍失败返回错误文本由编排器决策
- **降级路径**：Agent 服务不可用时经典 RAG（/api/rag/query）仍可独立提供检索问答
- **可观测**：SSE 全事件流 + agent_step 轨迹落库，任意一轮对话可完整回放
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
