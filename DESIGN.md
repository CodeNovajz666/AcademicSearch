# 系统设计文档（DESIGN）

> 项目：基于多模态大模型的学术文献精读与科研创新辅助系统
> 版本：v2.0（2026-09-07 架构重构定稿）
> 关联文档：[ROADMAP.md](./ROADMAP.md)（实施路线图）· [USER_MANUAL.md](./USER_MANUAL.md)（用户手册）

## 1. 系统概述

面向科研人员的学术文献检索、精读与智能问答平台。用户上传 PDF 学术文献后，系统自动解析全文、切分文本块并建立向量索引（Milvus）；用户既可做经典语义检索，也可通过 **AI 研究副驾（Agent）** 用自然语言完成文献调研、精读、翻译与总结——Agent 通过 MCP（Model Context Protocol）工具集自主规划检索路径，实时流式输出执行轨迹与最终回答。

### 1.1 核心能力

| 能力 | 说明 |
|------|------|
| 文献管理 | PDF 上传（50MB 上限）、元数据自动抽取（标题/摘要/作者/期刊/年份/DOI）、文献库增删查 |
| 语义检索 | 向量相似度（HNSW + COSINE）匹配文献，返回 topK 命中与文本片段 |
| Agent 问答 | 中心化编排器（Orchestrator-Centric）驱动 ReAct 循环，MCP 工具集支撑检索/精读/翻译/总结，SSE 流式输出 |
| 会话记忆 | 多轮对话上下文、执行轨迹回放、运行统计（token/步数/耗时）、Markdown 导出 |
| 安全认证 | JWT 无状态认证（Java 签发，Python 共验）、多用户文献与会话数据隔离 |

### 1.2 技术栈

| 层 | 技术 |
|----|------|
| 前端 | React 18 · Vite 5 · React Router 6 · 原生 fetch（SSE 流式解析）· Context 状态管理 · 暗/亮主题 |
| Java 后端 | Java 21 · Spring Boot 3.2 · Spring Security + JWT (jjwt) · Spring Data JPA · Apache PDFBox 3.0.2 · OkHttp |
| Python Agent 服务 | Python 3.12 · FastAPI · uvicorn · openai SDK（DashScope 兼容模式）· mcp 1.x · pyjwt · pymysql |
| 向量数据库 | Milvus 2.5（standalone，Docker Compose：etcd + MinIO + milvus） |
| 业务数据库 | MySQL 8.0（库名 example_db） |
| 大模型 | 阿里云百炼：qwen-plus（编排器）· qwen-turbo（技能）· text-embedding-v1（向量化） |

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
              │  中心化编排器        │  │ MySQL  │   │ Milvus │
              │  JWT 鉴权/会话CRUD  │  │ 业务数据│   │ 向量索引│
              │  SSE 流式端点       │  └────────┘   └────────┘
              └────────┬─────────┘        ▲              ▲
                       │ stdio (MCP协议)  │              │
                       ▼                 │              │
              ┌────────────────────┐     │              │
              │  MCP Server（唯一）  │────┴──────────────┘
              │  七工具：检索/精读/  │  工具实现直连 MySQL/Milvus
              │  翻译/总结/finalize │
              └────────────────────┘
```

**职责边界**：
- **Java 后端（8080）**：用户认证（注册/登录/刷新/me）、文献上传与解析、文本切分、embedding 生成、经典 RAG 降级接口（`/api/rag/query`）
- **Python Agent 服务（8000）**：Agent 编排、MCP 客户端生命周期、会话三表 CRUD、SSE 流式推送；复用 Java 签发的 JWT（共享密钥验签）
- **MCP Server（独立子进程，stdio）**：全部 Agent 工具的唯一载体；由 Python 主服务拉起，通过 MCP 协议交互
- **前端（5173）**：Vite 开发代理 `/api` → 8080、`/agent-api` → 8000（rewrite 去前缀）

## 3. RAG 数据流

```
上传阶段（Java）：
  PDF 文件 → PDFBox 提取全文 → 元数据抽取（标题=首行5-200字；摘要=Abstract/摘要
  到 Keywords/Introduction 之间）→ 文本切分（500字/块，100字重叠，句子边界对齐）
  → DashScope text-embedding-v1 批量向量化（10条/批）→ Milvus literature_chunks

检索阶段（MCP 工具 semantic_search）：
  用户问题 → embedding → Milvus HNSW 检索（COSINE，top_k）
  → 按 literature_id 查 MySQL 补全元数据 → 返回 {文献信息, 匹配片段, 分数}
```

## 4. Agent 架构（中心化编排 + 唯一 MCP）

### 4.1 设计决策

| 决策 | 选型 | 理由 |
|------|------|------|
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

## 5. 数据存储设计

### 5.1 MySQL 表（example_db）

| 表 | 用途 | 关键字段 |
|----|------|---------|
| `users` | 用户 | id, username, email(唯一), password(BCrypt) |
| `literature` | 文献元数据与全文 | id, title, authors, abstract_text, journal, year, doi, file_name, file_path, content(LONGTEXT), user_id, created_at |
| `conversation` | Agent 会话 | id, user_id, title(首问前24字), created_at, updated_at |
| `agent_message` | 会话消息 | id, conversation_id, role(user/assistant), content, references_json, stats_json, created_at |
| `agent_step` | 执行轨迹 | id, message_id, agent_name, step_index, step_type(agent_start/thinking/tool_call/tool_result), tool_name, content, duration_ms, created_at |

> 历史表 `vector_index`（MySQL JSON 向量）保留至 Milvus 迁移验收后删除。

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
| `/api/auth/register` `/login` `/refresh` `/me` | POST/GET | 注册/登录/刷新令牌/当前用户 |
| `/api/literature/upload` | POST | 上传 PDF（multipart，属主绑定） |
| `/api/literature` | GET | 文献列表 |
| `/api/literature/{id}` | GET/DELETE | 详情/删除（属主校验，连带删向量） |
| `/api/literature/search` | GET | 语义检索（q + topK） |
| `/api/rag/query` | GET/POST | 经典 RAG 降级路径（单次检索+生成） |

### 6.2 Python Agent 服务（/agent-api，全部 JWT 保护）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/agent/chat` | POST | SSE：`start → step* → token* → final/error` |
| `/agent/conversations` | GET | 会话列表（按 updated_at 倒序） |
| `/agent/conversations/{id}/messages` | GET | 历史消息（含轨迹与统计，回放用） |
| `/agent/conversations/{id}` | PATCH/DELETE | 重命名（≤60字）/删除（属主校验） |
| `/health` | GET | 健康检查（含 MCP 连接状态） |

统一响应体 `{code, message, data}`；鉴权失败 401，越权/不存在 404。

## 7. 安全设计

- **认证**：JWT（HS256），Java 签发（subject=email），Python 端 pyjwt 共享 `JWT_SECRET` 验签后按 email 查 `users` 表；access token 过期前端自动用 refresh token 续期并重试
- **授权**：文献与会话的增删查全部带属主校验（user_id 匹配），跨用户访问返回 404
- **密码**：BCrypt 加密存储
- **CORS**：白名单（5173/4173）双端一致
- **传输**：MCP 走本机 stdio，不出网络边界

## 8. 部署架构

```
开发机/服务器
├─ frontend-react   (npm run dev / build 后静态托管)   :5173
├─ backend          (mvn spring-boot:run / java -jar)  :8080
├─ agent-python     (python main.py)                   :8000
│    └─ mcp_server.py（子进程，stdio）
├─ milvus/          (docker compose up -d)             :19530 / 9091 / 9000-9001
└─ MySQL80                                             :3306

启动顺序：MySQL → Milvus → Java 后端 → Python Agent → 前端
（Java 端 Milvus 客户端为 @Lazy 惰性连接，Milvus 未启动不阻塞后端启动）
```

环境变量集中于项目根 `.env`：`DASHSCOPE_API_KEY`、`DB_USERNAME/PASSWORD`、`JWT_SECRET`、`MILVUS_URI`。

## 9. 质量保障

- **单元测试**：Java 侧 10 项（文本切分、摘要抽取、JWT、文献属主等）；Python 侧 selfcheck.py（模块导入/DB 连接/MCP stdio 握手与工具调用 roundtrip）
- **工具失败重试**：MCP 工具异常自动重试 1 次，仍失败返回错误文本由编排器决策
- **降级路径**：Agent 服务不可用时经典 RAG（/api/rag/query）仍可独立提供检索问答
- **可观测**：SSE 全事件流 + agent_step 轨迹落库，任意一轮对话可完整回放
