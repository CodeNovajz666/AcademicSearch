# 基于大语言模型的学术文献检索与问答系统（AcademicSearch）

一个采用 RAG（Retrieval-Augmented Generation，检索增强生成）架构的学术文献检索与智能问答系统。用户上传 PDF 学术文献后，系统自动解析全文、切分文本块并生成向量索引（Milvus）；检索时通过向量相似度匹配相关文献，并结合通义千问大模型生成回答。AI 研究副驾（Agent）通过 MCP 工具集自主规划检索路径，并采用 **Supervisor + 3 个专家子 Agent** 协作完成调研、精读与分析。

> **文档状态**：本文件为当前实现基线（2026-10-27 与代码对齐）。[DESIGN.md](./DESIGN.md)、[USER_MANUAL.md](./USER_MANUAL.md)、[ROADMAP.md](./ROADMAP.md) 已同步更新；如文档与代码冲突，以代码为准。

## 1. 系统架构

前后端分离，**两个后端服务 + 两个存储**：

```
┌──────────────────┐  /api        ┌─────────────────────┐
│  前端 React 18    │ ───────────► │  Java 后端 (8080)    │
│  (Vite :5173)    │  /agent-api  │  Spring Boot + JPA   │
│                  │ ───────────► └────────┬────────────┘
│  React Router 6  │               /agent-api 代理 rewrite │
└──────────────────┘                      ▼
                                  ┌─────────────────────┐
                                  │ Python Agent (8000)  │
                                  │ FastAPI + Supervisor │──stdio──► MCP Server 子进程
                                  │ + 3 专家子 Agent      │           (7 原子工具 + 3 派发工具)
                                  └────┬──────────┬─────┘
                                       ▼          ▼
                                   MySQL 8     Milvus 2.5
                                  (example_db)  (Docker Compose)
```

- **Java 后端（8080）**：用户认证（图形验证码/注册/登录/刷新/登录限流）、文献上传与解析、文本切分、embedding 生成、语义检索、双知识库可见性、用户私有状态（收藏/已读/笔记）、经典 RAG 降级接口
- **Python Agent 服务（8000）**：Supervisor 编排器 + 检索/精读/分析三个专家子 Agent、MCP 客户端生命周期、会话三表 CRUD、SSE 流式推送；复用 Java 签发的 JWT（共享密钥验签）
- **MCP Server（独立 stdio 子进程）**：7 个原子工具（检索/清单/详情/精读/翻译/总结/finalize）+ 3 个派发工具（检索/精读/分析专家），直连 MySQL 与 Milvus
- **前端（5173）**：Vite 开发代理 `/api` → 8080、`/agent-api` → 8000（rewrite 去前缀）；游客可浏览首页与检索页，其余功能页需登录

> 注意：前端代理仅存在于 Vite 开发服务器中。生产部署需要自建反向代理（Nginx 等）按前缀分发两个后端，当前仓库未提供该配置。

### RAG 数据流

```
上传阶段（Java，异步）：
  PDF → PDFBox 提取全文 → 元数据抽取（标题/摘要启发式；作者/期刊/年份/DOI 由表单提供）
      → 文本切分（500 字符/块，100 字符重叠，句子边界对齐）
      → DashScope text-embedding-v1 批量生成向量（10 条/批）→ 存入 Milvus
      → LLM 结构化知识抽取（主要内容/研究方法/实验/结论）→ processStatus=READY

检索阶段：
  问题向量化 → Milvus HNSW 检索块级命中（topK × 4）
            → 按文献去重（每篇取最高分块排序）→ topK 篇文献 + 匹配片段
            → 总库按 is_public 过滤；私库按 user_id 过滤

回答阶段：
  经典 RAG：命中分块拼上下文 → qwen3.8-flash 生成回答 + 参考文献
  Agent 链路：Supervisor 调度原子工具/专家子 Agent 收集信息 → finalize → 流式收尾
```

## 2. 技术栈

### Java 后端（backend/）

| 类别 | 技术 |
|------|------|
| 语言与框架 | Java 21 + Spring Boot 3.5.7 |
| 安全认证 | Spring Security + JWT (jjwt 0.12.5)，BCrypt 密码加密，图形验证码（hutool），登录失败限流 |
| 数据访问 | Spring Data JPA + Hibernate（ddl-auto: update） |
| 向量检索 | Milvus Java SDK 2.5.10（HNSW + COSINE） |
| PDF 解析 | Apache PDFBox 3.0.2 |
| 构建 | Maven，产出 `literature-search-1.0.0.jar` |

### Python Agent 服务（agent-python/）

| 类别 | 技术 |
|------|------|
| 框架 | FastAPI + uvicorn（SSE 流式） |
| LLM 调用 | openai SDK（DashScope OpenAI 兼容模式） |
| 工具协议 | mcp 1.x（stdio 子进程，FastMCP） |
| 向量客户端 | pymilvus 2.5 |
| 数据访问 | pymysql（多语句操作走单连接事务；async 侧经 asyncio.to_thread 下沉） |

### 前端（frontend-react/）

| 类别 | 技术 |
|------|------|
| 框架 | React 18 + Vite 8 + React Router 7 |
| 状态 | React Context（Auth / Theme / Toast）+ localStorage |
| HTTP | 原生 fetch 封装（Bearer Token 注入、401 自动刷新重试、SSE 解析） |
| PDF 阅读 | pdf.js（研究工作区长卷懒加载渲染） |

### 外部服务（阿里云 DashScope）

| 模型 | 用途 |
|------|------|
| text-embedding-v1 | 文本块与查询向量（1536 维，批量每次 10 条） |
| qwen3.8-flash | 全部对话链路（思考模型，走 OpenAI 兼容端点）：协调者与子 Agent 工具决策（保留思考）、流式收尾与技能（自动关闭思考）、经典 RAG、知识抽取（可配 `ORCHESTRATOR_MODEL` / `SUBAGENT_MODEL` / `SKILL_MODEL`） |

## 3. 功能模块

### 3.1 用户认证

- 图形验证码（5 分钟有效、一次性使用、连错 5 次作废；仅注册环节强制校验）
- 登录失败限流：同一邮箱 15 分钟窗口内连续失败 5 次锁定 15 分钟（内存实现，仅单实例有效）
- 登录颁发双令牌：access_token（2h）+ refresh_token（7d，登出即吊销），HMAC 签名（jjwt 按密钥长度自动选 HS256/384/512，Python 端验签接受全族），subject=email，令牌携带 jti 唯一标识
- 登出吊销：`POST /api/auth/logout` 将 refresh 令牌的 jti 写入黑名单表（revoked_tokens），刷新端点命中即拒绝；登录失败限流为「邮箱（15 分钟 5 次）+ 来源 IP（15 分钟 30 次）」双维度
- 前端 401 时自动用 refresh_token 刷新并重试，失败则跳转登录页
- `JWT_SECRET` 缺失或不足 32 字节时，Java 与 Python 服务均拒绝启动（不提供默认值）
- **游客模式**：`/`（首页）与 `/search` 可公开浏览；`/library`、`/community`、`/literature/:id`、`/upload`、`/workspace/:id` 显示登录引导卡；游客提交检索或使用 AI 副驾时带 `from` 跳转登录，登录后回原页

### 3.2 文献管理与双知识库

- 上传 PDF（单文件上限 50MB），可携带标题、作者、期刊、年份、DOI、社区板块与公开开关；上传接口**立即返回**（processStatus=PROCESSING）
- PDFBox 提取全文后自动抽取标题（首行 5–200 字符）与摘要（Abstract/摘要 到 Keywords/Introduction 之间）
- **后台处理线程池（2 线程）**依次完成：
  - 向量索引构建（Milvus，已存在则跳过）
  - LLM 结构化知识抽取（主要内容/研究方法/实验/结论）；标题被识别为封面横幅（如 "Published as a conference paper at ICLR 2026"、arXiv 许可声明行）时自动用 LLM 识别的真实标题覆盖
- 前端详情页在 PROCESSING 期间每 3 秒轮询，完成后展示知识分区；文献卡片显示"解析中"徽标
- `POST /api/literature/{id}/reprocess` 可对存量文献重跑知识抽取修复标题（仅属主，向量已存在不重建）
- **双知识库（自愿贡献制）**：文献默认私有，仅属主可访问（详情/原文/状态/Agent 工具全部隔离）；上传时勾选"贡献到社区总知识库"后全员可检索阅读，详情页可随时切换（仅属主）。总库列表/向量检索/Agent 工具均按 `is_public` 过滤；历史 `NULL` 视为公开
- **每用户私有状态**（`user_literature_state` 表）：收藏（星标）、已读标记、阅读笔记（仅本人可见），`PUT /api/literature/{id}/state` 部分更新；文献库支持"只看收藏"筛选与按入库时间/标题/年份排序（服务端分页+排序）；详情页提供 BibTeX / GB/T 7714 / APA 引用格式一键复制
- **我的文献库（`/library`）只展示本人文献**（含私有与已公开），不再混排社区他人文献；社区内容统一在 `/community` 查看
- **社区页（`/community`）**：上半部分「为你推荐」——取当前用户最近 3 篇文献的标题+摘要构造查询文本，批量 embedding 后在**他人公开文献**范围内做 Milvus 相似度召回（排除本人上传），按文献去重取最高分分块，返回 `basedOn`（推荐依据的我的文献标题）+ `items`（相似度/片段/来源标题）；下半部分为社区全部公开文献，支持板块筛选
- 文件存于服务器 `uploads/` 目录（数据库存绝对路径）；删除文献时同步清理文件与向量索引
- 过期/无效 JWT 由过滤器规范返回 401（带 CORS 头），前端自动用 refresh_token 续期重试

### 3.3 研究工作区

精读功能统一收口在 `/workspace/:id` 一个入口（原「阅读模式」已并入，`ReaderView` 删除）：

- **左侧**：pdf.js 逐页平铺渲染 PDF 长卷，可上下滚动翻页；顶栏提供「上一页 / 下一页」按钮、页码指示、跳页输入与下载；页面卡片 `flex-shrink: 0` 保证多页正确溢出滚动
- **目录**：默认收起的左侧抽屉（顶栏「目录」按钮开关），优先 PDF 书签（`getPageIndex` 异步解析页码），缺失时按章节标题规律扫描前 60 页生成；点击条目跳页并自动收起
- **右侧上半**：AI 知识批注卡（摘要/主要内容/方法/实验/结论，可定位起始页一键跳转）
- **右侧下半**：与模型对话（4 个快捷问题，独立滚动）；左下角悬浮「文本批注 / 颜色 / 创新点改进」工具 dock，批注按页保存并显示彩色角标
  - 后端 `/agent/chat` 已支持 `literatureId` 并会注入"优先精读该论文"的上下文；前端 `WorkspaceView` 调用 `agentChat` 时透传当前文献 id，工作区对话自动关联正在精读的论文

### 3.4 语义检索（Java）

- 查询向量化后经 Milvus HNSW 检索块级命中，按文献去重取最高分块排序，返回 topK 文献（含相似度与匹配片段）
- `scope=all` 检索社区总知识库（仅公开文献）；`scope=mine` 检索当前用户自己的私有知识库
- 前端检索页提供"总知识库 / 我的知识库"切换；游客提交检索会引导登录

### 3.5 经典 RAG（Java，降级路径）

- 检索到的每篇文献以「标题 + 作者 + 摘要 + 最高分的两个命中分块」拼上下文（检索依据与模型所见一致）
- 库中无相关文献时直接返回提示，不调用大模型

### 3.6 AI 研究副驾（Python Agent，默认链路）

- **Supervisor 多 Agent**：协调者持有全部原子工具 + 3 个派发工具；检索/精读/分析三个专家子 Agent 各自只持有受限工具子集，独立步数预算 4 步，且不可见派发工具（深度=2，禁止递归派生）
- **MCP 原子工具**（唯一 stdio 子进程）：`semantic_search`、`list_literatures`、`get_literature`、`read_section`（关键词定位精读）、`translate`（中英互译）、`summarize`（结构化/一段话总结）、`finalize`
- **编排器派发工具**（非 MCP）：`dispatch_search`、`dispatch_read`、`dispatch_analyze`，分别拉起检索/精读/分析专家
- **安全注入**：`user_id` 与 `scope` 由服务端登录态强制注入，覆盖模型可能幻觉出的越权参数；文献读取类工具统一做私有可见性校验
- **工具结果**：统一 JSON 序列化并压缩到 ≤4000 字符；`semantic_search` 超长时先压内容字段、再丢尾部条目，始终保持 JSON 合法，保证 references 可解析
- **SSE 事件流**：`start → step*（含子 Agent 步骤，agent 字段区分）→ token*（流式回答）→ final | error`
- **运行统计**：final 事件携带轨迹步数、工具调用数、参与 Agent、耗时与 token 用量
- **会话记忆**：conversation / agent_message / agent_step 三表持久化；用户消息即时落库（执行失败/中断不丢提问），助手回答与轨迹单事务写入；失败轮次也留档
- **上下文**：最近 6 轮、单条截断 2000 字；协调者最多 8 步，步数耗尽强制收敛
- 模型、步数、历史轮数均可在 `.env` 调整（见 `.env.example`）

## 4. 目录结构

```
AcademicSearch/
├── .env.example                    # 环境变量模板（复制为 .env 使用）
├── deploy/                         # 生产部署：nginx.conf + 3 个 Dockerfile + docker-compose
├── scripts/                        # 运维脚本：存量无主文献归属 SQL
├── .github/workflows/ci.yml        # CI：Java 测试 / Python 测试 / 前端构建
├── README.md / DESIGN.md / USER_MANUAL.md / ROADMAP.md
├── backend/                        # Spring Boot 后端（8080）
│   ├── pom.xml
│   └── src/main/java/com/example/literaturesearch/
│       ├── config/                 # SecurityConfig、SecretValidationConfig、MilvusConfig、DashScopeConfig、WebConfig
│       ├── controller/             # AuthController、LiteratureController、RagController
│       ├── service/                # AuthService、CaptchaService、LoginRateLimiter、
│       │                           # LiteratureService、LiteratureProcessingService、KnowledgeExtractionService、
│       │                           # VectorService、MilvusVectorStore、RagService、DashScopeService
│       ├── entity/                 # Literature、User、UserLiteratureState
│       ├── dto/  exception/  filter/  repository/  util/
│   └── src/test/                   # 10 个单元测试类（切分/摘要/验证码/限流/可见性/状态/文件/JWT/知识抽取）
├── agent-python/                   # Python Agent 服务（8000）
│   ├── main.py                     # FastAPI：JWT 鉴权、SSE、会话 CRUD、MCP 生命周期
│   ├── orchestrator.py             # Supervisor 编排器 + 检索/精读/分析子 Agent
│   ├── mcp_server.py               # 唯一 MCP Server：7 原子工具 + finalize
│   ├── conversation_store.py       # 会话三表（用户消息即时落库、助手侧单事务）
│   ├── db.py                       # pymysql（transaction() 上下文管理器）
│   ├── llm.py  milvus_store.py  config.py
│   ├── selfcheck.py                # 手工自检（导入/DB/MCP 握手）
│   ├── migrate_vectors.py          # 历史 MySQL JSON 向量迁移脚本
│   ├── migrate_vectors_v2.py       # v2 标量字段迁移（user_id/is_public，不重新 embedding）
│   └── tests/                      # pytest：MCP 可见性 / 引用收集 / 编排器行为
├── frontend-react/                 # React 18 前端（5173）
│   ├── vite.config.js              # /api → 8080，/agent-api → 8000（rewrite）
│   └── src/
│       ├── lib/api.js              # fetch 封装：Token 注入 / 401 刷新 / SSE 解析
│       ├── lib/citation.js         # BibTeX / GB/T 7714 / APA 生成
│       ├── lib/categories.js       # 社区板块
│       ├── context/                # Auth / Theme / Toast
│       ├── components/             # NavRail、CopilotPanel、LiteratureCard、SearchHero 等
│       └── views/                  # Home / Search / Library / Community / Detail / Reader / Workspace / Upload / Login / Register
└── milvus/docker-compose.yml       # Milvus standalone（etcd + MinIO + milvus）
```

## 5. API 接口

所有接口统一返回 `{ code, message, data }`（文件流与 SSE 除外），前端以 `code === 200` 判断成功。除认证接口外均需 Bearer Token。

### 5.1 认证 `/api/auth`（Java）

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/auth/captcha` | 获取图形验证码（captchaId + base64 图片） |
| POST | `/api/auth/register` | 注册（邮箱、用户名、密码、验证码） |
| POST | `/api/auth/login` | 登录（同邮箱连续失败 5 次锁定 15 分钟） |
| POST | `/api/auth/refresh` | 用 refresh_token 换新令牌 |
| GET | `/api/auth/me` | 当前用户信息 |
| GET / PUT | `/api/settings` | 用户级智能体配置：个人 DashScope API Key（完整 Key 仅存服务端，接口只返回掩码如 sk-38****b767；保存留空=不变更，clearApiKey=true 删除）、协调者模型、子智能体模型；留空回退服务器默认 |

### 5.2 文献 `/api/literature`（Java）

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/literature/upload` | 上传 PDF（multipart：file, title, authors, journal, year, doi, isPublic=false, category） |
| GET | `/api/literature?scope&board&starred&sortBy&order&page&size` | 文献列表（真分页）：`scope=mine` 我的文献（可 `starred=true` 只看收藏）；`scope=all`/省略为社区公开文献；`board` 仅对社区生效；排序 `sortBy=createdAt\|title\|year` + `order=desc\|asc`；`page` 从 0 计，`size` 默认 12（上限 50）；返回 `{items,total,page,size}` |
| GET | `/api/literature/recommendations?topK=6` | 社区推荐：以我的文献内容为画像召回他人公开文献，返回 `{basedOn, items}` |
| GET | `/api/literature/{id}` | 文献详情（私有文献仅属主可读，越权 404） |
| GET | `/api/literature/{id}/file[?download=true]` | 打开原文 PDF（inline 预览；`download=true` 时触发保存） |
| PUT | `/api/literature/{id}/state` | 更新收藏/已读/笔记（body 字段可选，部分更新） |
| PUT | `/api/literature/{id}/visibility` | 公开到社区总知识库 / 设为私有（仅属主） |
| POST | `/api/literature/{id}/reprocess` | 重新解析（重跑 LLM 知识抽取并修复标题，仅属主） |
| DELETE | `/api/literature/{id}` | 删除文献（仅上传者本人） |
| GET | `/api/literature/search?query=xxx&topK=10&scope=all\|mine` | 语义检索 |

### 5.3 经典 RAG `/api/rag`（Java，降级路径）

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/rag/query` | body `{question, topK}`，返回回答与参考文献 |
| GET | `/api/rag/query?question=xxx&topK=5` | 同上 |

### 5.4 Agent `/agent/*`（Python，前端经 `/agent-api` 前缀访问）

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/agent/chat` | SSE 流式对话；body `{question, conversationId?, literatureId?}`；事件 `start/step/token/final/error` |
| GET | `/agent/conversations` | 当前用户会话列表 |
| GET | `/agent/conversations/{id}/messages` | 会话消息（轨迹回放 + 统计） |
| PATCH | `/agent/conversations/{id}` | 重命名（≤60 字） |
| DELETE | `/agent/conversations/{id}` | 删除会话（仅本人，单事务级联删除） |
| GET | `/agent-api/health`（Python `/health`） | 健康检查（含 MCP 连接状态） |

> `literatureId` 用于研究工作区上下文注入；前端已在 `WorkspaceView` 中透传，工作区对话自动关联当前论文。

## 6. 数据库设计

MySQL `example_db`，由 Hibernate 自动建表：

| 表 | 用途 | 关键字段 |
|----|------|----------|
| `literature` | 文献元数据与全文 | title, authors, abstract_text, journal, year, doi, file_name, file_path, content (LONGTEXT), process_status, main_content, methods, experiments, conclusions, user_id, is_public, category, created_at |
| `users` | 用户账户 | email (唯一), username, password (BCrypt) |
| `conversation` | Agent 会话 | user_id, title（首问前 24 字）, created_at, updated_at |
| `agent_message` | 会话消息 | conversation_id, role, content, references_json, stats_json |
| `agent_step` | 执行轨迹 | message_id, agent_name, step_index, step_type, tool_name, content, duration_ms |
| `user_literature_state` | 用户对文献的私有状态 | user_id + literature_id（唯一）, starred, read_status, note, updated_at |

向量存于 Milvus `literature_chunks` collection（HNSW + COSINE，维度见 `MILVUS_EMBEDDING_DIM`，默认 1536；`chunk_text` VarChar(2048)）。

> 历史表 `vector_index`（MySQL JSON 向量）已废弃，确认无数据后可删除。

## 7. 快速开始

### 7.1 环境要求

- JDK 21、Maven 3.8+；Node.js 18+；Python 3.12
- MySQL 8（本地 3306）、Docker（Milvus）
- 有效的 DashScope API Key

### 7.2 准备

```bash
# 1. 数据库
mysql -e "CREATE DATABASE IF NOT EXISTS example_db DEFAULT CHARACTER SET utf8mb4;"

# 2. Milvus
cd milvus && docker compose up -d && cd ..

# 3. 环境变量（JWT_SECRET 必须是 32 字节以上的随机串）
cp .env.example .env
# 编辑 .env 填入 DASHSCOPE_API_KEY / JWT_SECRET / DB_PASSWORD
```

### 7.3 一键启动（推荐）

```powershell
# 在项目根目录（PowerShell）
.\start.ps1          # 单窗口拉起全部服务并统一输出日志，Ctrl+C 停止本次启动的服务
.\start.ps1 -NoTail  # 启动完成即退出，服务留在后台（日志在 logs\ 目录）
.\stop.ps1           # 强制停止前端/Agent/后端（按端口结束整棵进程树）
.\stop.ps1 -All      # 同时停止 Milvus（数据卷保留）
```

脚本行为：前置检查（.env / node_modules / venv / MySQL 端口）→ Milvus 未运行时自动 `docker compose up -d` → 依次拉起 Java(8080)/Agent(8000)/前端(5173) → 健康检查 → 汇总日志到 `logs\`；已占用的端口视为服务在运行，自动跳过。

### 7.4 生产部署（Docker）

```bash
cd deploy && docker compose up -d --build   # 详见 deploy/README.md
```

Nginx 统一入口（:80）按前缀分发 `/api`→Java、`/agent-api`→Python（去前缀），静态托管前端；MySQL/Milvus 使用外部实例。

### 7.5 手动启动三个服务（等价方式）

```bash
# Java 后端（8080）
cd backend && mvn spring-boot:run

# Python Agent（8000）
cd agent-python
pip install -r requirements.txt
python main.py

# 前端（5173）
cd frontend-react
npm install
npm run dev
```

启动顺序建议：MySQL → Milvus → Java → Python → 前端。Java 的 Milvus 客户端为 `@Lazy`，Milvus 未启动不阻塞后端启动；但上传/检索/Agent 检索需要 Milvus 就绪。

访问 http://localhost:5173，注册并登录后上传 PDF，即可检索与提问。

### 7.6 运行测试与自检

```bash
cd backend && mvn test                                   # Java 单测（56 个）
cd agent-python && python -m pytest tests -q             # Python 单测（MCP 可见性/引用收集/编排器）
cd agent-python && python selfcheck.py                   # 手工自检（导入/DB/MCP 握手）
```

CI：`.github/workflows/ci.yml` 在 push/PR 时自动跑三端测试与构建。

## 8. 已知限制与改进方向

- **生产部署配置已入库**：`deploy/` 提供 Nginx 统一入口（/api 与 /agent-api 前缀分发）+ 三个 Dockerfile + docker-compose；本地开发仍用 Vite 代理
- **多模态未落地**：项目名含"多模态"，但当前链路均为纯文本模型，未处理图表等视觉内容；如需名实相符可引入 qwen-vl
- **参考文献收集范围有限**：编排器只从 `semantic_search` 结果提取 references；若某轮主要使用 `read_section` / `summarize` / `dispatch_read`，final 的参考文献可能为空
- **限流/验证码为内存实现**：重启清零、无法多实例；上多实例需迁移 Redis
- ~~检索规模上限~~ **已解决（2026-11-01）**：Milvus collection 升级 v2 schema（`user_id`/`is_public` 标量字段），检索用恒定长度标量表达式（`user_id == ?` / `is_public == true`）；存量 v1 collection 自动探测回退 id 列表过滤，跑 `agent-python/migrate_vectors_v2.py` 重建后自动切换；可见性切换会同步重写向量标量（不重新 embedding）
- **无 RBAC/管理员角色**：当前只有普通用户；文献可见性由"属主 + is_public"控制
- **历史仓库污染（已处理，2026-11-01）**：曾用 `git filter-repo` 重写历史——移除 `.venv`（约 15000 文件）与 `backend/target`，抹除 DashScope Key 与历史 JWT 签名密钥，体积 160MiB→25MiB。被泄露的 Key 仍需在阿里云控制台轮换；GitHub 侧孤儿提交需强推后联系 GitHub Support 清理。本地留存备份 `D:\Projects\AcademicSearch-history-backup.bundle`（含旧密钥），确认无误并轮换密钥后应删除
- **PDF 元数据抽取较简单**：标题取首行、摘要按关键词截取；对双栏排版、扫描件效果差，无 OCR 降级
- **集成测试有限**：Python 有 pytest 29 例（单元级，mock 外部依赖）、Java 56 个单测；端到端集成测试与 CI 内的 Milvus/MySQL 依赖环境仍缺
- **遗留空表**：MySQL `vector_index` 已废弃，确认无数据后可删除
