# 基于大语言模型的学术文献检索与问答系统（AcademicSearchBasedOnMLLM）

一个采用 RAG（Retrieval-Augmented Generation，检索增强生成）架构的学术文献检索与智能问答系统。用户上传 PDF 学术文献后，系统自动解析全文、切分文本块并生成向量索引；检索时通过向量相似度匹配相关文献，并结合通义千问大模型生成带有参考文献的中文回答。

## 1. 系统架构

整体为前后端分离的单体应用：

```
┌─────────────────────┐         ┌──────────────────────────────┐
│   前端 React 18 SPA  │  /api   │      后端 Spring Boot         │
│  (Vite :5173)       │ ──────► │      (Tomcat :8080)           │
│                     │  代理    │                              │
│  React Router 6     │         │  Controller → Service → JPA   │
└─────────────────────┘         │        │            │         │
                                │        ▼            ▼         │
                                │   MySQL example_db  DashScope │
                                │   (业务数据+向量)    (嵌入+对话) │
                                └──────────────────────────────┘
```

RAG 核心数据流：

```
上传阶段：PDF 文件 → PDFBox 提取全文 → 元数据抽取（标题/摘要/作者等）
        → 文本切分（500 字符/块，100 字符重叠，句子边界对齐）
        → DashScope text-embedding-v1 批量生成向量 → 存入 vector_index 表

问答阶段：用户提问 → 问题向量化 → 分页扫描文本块计算余弦相似度
        → 每篇文献取最高分块，按分数排序取 topK 文献
        → 拼接上下文 → qwen-turbo 生成回答 → 返回回答 + 参考文献列表
```

## 2. 技术栈

### 后端

| 类别 | 技术 | 说明 |
|------|------|------|
| 语言与框架 | Java 21 + Spring Boot 3.2.0 | Spring MVC 提供 REST API |
| 安全认证 | Spring Security + JWT (jjwt 0.12.5) | 无状态认证，BCrypt 密码加密 |
| 数据访问 | Spring Data JPA + Hibernate | DDL 自动更新模式 |
| 数据库 | MySQL | 库名 `example_db` |
| PDF 解析 | Apache PDFBox 3.0.2 | 提取 PDF 全文文本 |
| HTTP 客户端 | OkHttp 4.12.0 | 调用 DashScope API |
| 测试 | JUnit 5 | 文本切分与摘要抽取的单元测试 |
| 构建工具 | Maven | 产出 `literature-search-1.0.0.jar` |

### 前端（frontend-react）

| 类别 | 技术 | 说明 |
|------|------|------|
| 框架 | React 18 (^18.3.1) | 函数组件 + Hooks |
| 路由 | React Router (^6.26.2) | BrowserRouter + 路由守卫 |
| 图标 | lucide-react (^0.441.0) | 轻量图标库 |
| HTTP 客户端 | 原生 fetch 封装 | 统一 Bearer Token、401 自动刷新重试 |
| 状态管理 | React Context + localStorage | 认证 / 主题 / Toast 三类上下文 |
| 构建工具 | Vite (^5.4.8) | 开发服务器含 `/api` 代理 |

### 外部服务

| 服务 | 模型 | 用途 |
|------|------|------|
| 阿里云 DashScope | text-embedding-v1 | 文本块与查询的向量嵌入（批量，每次最多 10 条） |
| 阿里云 DashScope | qwen-turbo | 基于检索上下文生成回答（temperature=0.7, max_tokens=2048） |

## 3. 功能模块

### 3.1 用户认证

- 邮箱 + 用户名 + 密码注册，密码 BCrypt 加密存储
- 登录颁发双令牌：access_token（24 小时）与 refresh_token（7 天），HMAC-SHA256 签名
- `JwtAuthenticationFilter` 拦截请求，从 `Authorization: Bearer <token>` 头解析身份
- 前端 401 时自动用 refresh_token 刷新并重试请求，刷新失败则跳转登录页
- 认证接口公开，其余接口均需有效令牌

### 3.2 文献管理

- 上传 PDF（单文件上限 50MB，前后端均校验），可携带标题、作者、期刊、年份、DOI 等元数据
- PDFBox 提取全文后自动抽取元数据：标题取首行长度 5–200 的文本；摘要取 "Abstract"/“摘要” 到 "Keywords"/"Introduction" 之间内容（支持中英文标记及 “：—-” 引导符剥离）
- 上传时记录属主（user_id），**删除仅限上传者本人**（历史无属主数据除外）
- 文件本体保存在服务器本地 `uploads/` 目录，删除文献时同步清理 PDF 文件与向量索引

### 3.3 语义检索

- 查询文本经 DashScope 向量化后，分页扫描全部文本块计算余弦相似度（避免全量向量载入内存）
- 每篇文献取相似度最高的文本块参与排序，返回 topK 篇去重文献（含文献信息、相似度分数与匹配片段）
- 前端以相似度百分比直观展示相关度

### 3.4 智能问答（RAG，经典链路）

- 右侧 AI 副驾面板聊天式交互，问题默认检索 topK=5 篇文献
- 检索到的文献标题、摘要与部分正文拼接为上下文，交由 qwen-turbo 生成中文回答
- 回答附带参考文献列表，可点击跳转文献详情页
- 库中无相关文献时返回提示，不调用大模型

### 3.5 多 Agent 研究副驾（V2 架构，当前默认）

经典 RAG 已升级为 Planner-Executor 多 Agent 架构，前端副驾面板默认走此链路：

- **四个 Agent**：协调者（qwen-plus，规划调度）+ 检索专家 / 精读专家 / 分析专家（qwen-turbo，执行）
- **Agent-as-Tool**：协调者通过 `dispatch_search / dispatch_read / dispatch_analyze` 三个调度工具指挥专家 Agent，子 Agent 复用同一个 ReAct 循环引擎（`AgentEngine`）
- **原子工具（RAG）**：`semantic_search`（语义检索）、`list_literatures`、`get_literature`（元数据）、`read_section`（3000 字分段精读全文）
- **LLM 技能**：`translate_text`（中英互译）、`summarize_literature`（结构化论文总结，长文走 map-reduce 分段汇总）
- **finalize 协议**：协调者每轮必须调用工具，信息足够后调用 `finalize` 结束研究阶段，随后摘除工具、由 LLM 流式生成最终回答
- **SSE 事件流**：`start → step*（执行轨迹）→ token*（流式回答）→ final | error`，前端实时渲染 Agent 轨迹时间线
- **运行统计**：`final` 事件携带每次回答的轨迹步数、工具调用数、耗时与 token 用量（DashScope usage 字段跨轮累计），前端以统计条展示并随会话持久化
- **会话记忆**：conversation / agent_message / agent_step 三张表持久化多轮对话与完整执行轨迹，支持历史会话回放、重命名与删除；支持一键导出会话为 Markdown（含轨迹、参考文献与统计）
- **工具容错**：工具执行异常自动重试 1 次，仍失败时返回结构化错误 JSON 交由 LLM 降级处理
- 模型、步数上限、历史轮数均可在 `application.yml` 的 `agent:` 块调整

## 4. 目录结构

```
AcademicSearch/
├── .env.example                      # 环境变量模板（复制为 .env 使用）
├── backend/                          # Spring Boot 后端
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/literaturesearch/
│       │   ├── LiteratureSearchApplication.java   # 启动类
│       │   ├── config/
│       │   │   ├── DashScopeConfig.java           # DashScope API 配置
│       │   │   ├── SecurityConfig.java            # Spring Security 配置
│       │   │   └── WebConfig.java                 # CORS 白名单配置
│       │   ├── controller/
│       │   │   ├── AgentChatController.java         # Agent SSE 对话 + 会话接口
│       │   │   ├── AuthController.java              # 认证接口
│       │   │   ├── LiteratureController.java        # 文献管理接口
│       │   │   └── RagController.java               # RAG 问答接口
│       │   ├── agent/                               # 多 Agent 架构
│       │   │   ├── AgentDefs.java                   # 四个 Agent 的定义与提示词
│       │   │   ├── AgentOrchestrator.java           # 编排器（协调者循环 + 流式收尾）
│       │   │   ├── engine/                          # AgentEngine（ReAct 循环）等
│       │   │   └── tools/                           # 工具注册中心 + 文献工具/技能/调度工具
│       │   ├── dto/                               # 请求/响应对象
│       │   ├── entity/
│       │   │   ├── Literature.java                # 文献实体（含属主 user_id）
│       │   │   ├── User.java                      # 用户实体
│       │   │   ├── Conversation.java              # Agent 会话
│       │   │   ├── AgentMessage.java              # 会话消息
│       │   │   ├── AgentStep.java                 # Agent 执行轨迹
│       │   │   └── VectorIndex.java               # 向量索引实体
│       │   ├── exception/                         # 全局异常处理
│       │   ├── filter/JwtAuthenticationFilter.java
│       │   ├── repository/                        # JPA 仓库层
│       │   ├── service/
│       │   │   ├── AuthService.java               # 认证服务
│       │   │   ├── ConversationService.java        # 会话记忆持久化
│       │   │   ├── DashScopeService.java          # DashScope 调用（嵌入/对话/function calling/SSE 流式）
│       │   │   ├── LiteratureService.java         # 文献管理服务
│       │   │   ├── RagService.java                # RAG 问答服务
│       │   │   └── VectorService.java             # 向量检索服务
│       │   └── util/JwtUtil.java
│       ├── main/resources/application.yml         # 全部敏感项经环境变量注入；agent: 块可调模型/步数/历史轮数
│       └── test/java/.../                          # 单元测试（引擎/技能/会话/文献/向量）
└── frontend-react/                   # React 18 前端（实际使用的前端）
    ├── index.html                    # 标题：Scholar · 学术文献智能检索
    ├── vite.config.js                # 端口 5173，/api 代理到 8080
    └── src/
        ├── main.jsx                  # 入口，挂载 Router + Context Providers
        ├── App.jsx                   # 路由表 + RequireAuth 认证守卫
        ├── lib/api.js                # fetch 封装：Token 注入 / 401 自动刷新
        ├── context/                  # Auth / Theme / Toast 上下文
        ├── components/               # NavRail、CopilotPanel、LiteratureCard 等
        └── views/                    # Home / Search / Library / Detail / Upload / Login / Register
```

> 注：根目录下的 `frontend/` 为早期 Vue 版前端的残留（仅剩空壳与 node_modules），已废弃，实际前端为 `frontend-react/`，可在确认后删除。

## 5. API 接口

所有接口统一返回 `ApiResponse<T>` 格式：`{ code, message, data }`，前端以 `code === 200` 判断成功。除认证接口外均需携带 Bearer Token。

### 5.1 认证接口 `/api/auth`

| 方法 | 路径 | 功能 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 注册（邮箱、用户名、密码） | 否 |
| POST | `/api/auth/login` | 登录，返回 access_token 与 refresh_token | 否 |
| POST | `/api/auth/refresh` | 用 refresh_token 换取新令牌 | 否 |
| GET | `/api/auth/me` | 获取当前用户信息 | 是 |

### 5.2 文献接口 `/api/literature`

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/literature/upload` | 上传 PDF（multipart/form-data），自动解析并建立向量索引 |
| GET | `/api/literature` | 获取全部文献列表 |
| GET | `/api/literature/{id}` | 获取单篇文献详情 |
| DELETE | `/api/literature/{id}` | 删除文献及其文件与向量索引（仅上传者本人） |
| GET | `/api/literature/search?query=xxx&topK=10` | 语义检索，返回相似度评分与文本片段 |

### 5.3 问答接口 `/api/rag`（经典 RAG，降级路径）

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/rag/query` | 提交问题（body: `{question, topK}`），返回回答与参考文献 |
| GET | `/api/rag/query?question=xxx&topK=5` | GET 形式的同等功能 |

### 5.4 Agent 接口 `/api/agent`（当前默认链路）

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/agent/chat` | SSE 流式对话（body: `{conversationId?, question}`），事件流 `start/step/token/final/error`，`final` 携带参考文献与运行统计 |
| GET | `/api/agent/conversations` | 当前用户的历史会话列表 |
| GET | `/api/agent/conversations/{id}/messages` | 会话消息（含轨迹回放数据与统计） |
| PATCH | `/api/agent/conversations/{id}` | 重命名会话（body: `{title}`，上限 60 字） |
| DELETE | `/api/agent/conversations/{id}` | 删除会话及其消息与轨迹（仅本人） |

## 6. 数据库设计

数据库 `example_db`，共六张表，由 Hibernate 自动建表维护：

| 表 | 用途 | 关键字段 |
|----|------|----------|
| `literature` | 文献元数据与全文 | title, authors, abstract_text, journal, year, doi, file_name, file_path, content (LONGTEXT), user_id, created_at |
| `users` | 用户账户 | email (唯一), username, password (BCrypt) |
| `vector_index` | 向量索引 | literature_id, vector_data (JSON 数组文本), chunk_text, chunk_index, created_at |
| `conversation` | Agent 会话 | user_id, title（首问前 24 字，可重命名）, created_at, updated_at |
| `agent_message` | 会话消息 | conversation_id, role (user/assistant), content, references_json, stats_json |
| `agent_step` | Agent 执行轨迹 | message_id, agent_name, step_type (agent_start/thinking/tool_call/tool_result), tool_name, content, duration_ms |

向量不依赖专用向量数据库，以 JSON 数组存于 `vector_data` 字段，检索时在应用层分页扫描计算余弦相似度。这一实现简单直接，但文献量增大后线性比对仍会成为性能瓶颈。

## 7. 快速开始

### 7.1 环境要求

- JDK 21、Maven 3.8+
- Node.js 18+、npm
- MySQL 8（本地 3306 端口）
- 有效的 DashScope API Key

### 7.2 数据库准备

```sql
CREATE DATABASE example_db DEFAULT CHARACTER SET utf8mb4;
```

### 7.3 配置环境变量

后端的敏感配置（API Key、数据库凭据、JWT 密钥）全部通过环境变量注入，不再硬编码：

```bash
# 方式一：复制模板为 .env（放在项目根目录或 backend/ 目录下均可，已被 .gitignore 忽略）
cp .env.example .env
# 编辑 .env，填入 DASHSCOPE_API_KEY / DB_USERNAME / DB_PASSWORD / JWT_SECRET

# 方式二：直接设置系统环境变量（PowerShell 示例）
$env:DASHSCOPE_API_KEY = "sk-xxxxxxxx"
```

> 安全提示：历史提交中曾包含明文 API Key，建议在阿里云百炼控制台轮换密钥后再继续使用。

### 7.4 启动后端

```bash
cd backend
mvn spring-boot:run
```

服务运行在 `http://localhost:8080`。CORS 白名单（默认 `http://localhost:5173` 与 `http://localhost:4173`）可在 `application.yml` 的 `app.cors.allowed-origins` 中调整。

### 7.5 启动前端

```bash
cd frontend-react
npm install
npm run dev
```

开发服务器运行在 `http://localhost:5173`，`/api` 请求自动代理到后端 8080 端口。

### 7.6 使用流程

1. 访问 `http://localhost:5173`，注册并登录
2. 进入“上传”页面上传 PDF 学术文献，等待解析与向量索引完成
3. 在首页或“检索”页输入自然语言关键词进行语义搜索
4. 在右侧 AI 研究副驾提问（如“库里有哪些机器学习论文？”、“总结第 1 篇论文的方法”、“把这段摘要翻译成英文”），实时查看 Agent 执行轨迹与流式回答；右上角可切换历史会话或开启新会话

### 7.7 运行测试

```bash
cd backend
mvn test
```

覆盖 Agent 引擎循环（工具调用/异常恢复/步数收敛）、技能（翻译/总结 map-reduce）、会话记忆（轮数截断/轨迹持久化）、文本切分与摘要抽取。

## 8. 已知限制与改进方向

- **DashScope API Key 需更换**：`.env` 中的旧 Key 已在百炼侧失效（`API-key is blocked`），Agent 与 RAG 功能需要有效 Key 才能真实运行
- **Agent function calling 格式未经线上实测**：Key 失效导致无法端到端验证 DashScope 工具调用报文，当前按官方文档格式实现并以单测 mock 验证；换 Key 后建议先跑一轮真实对话确认
- **多模态尚未落地**：项目名称含“多模态”，但当前 DashScope 调用（text-embedding-v1 与 qwen-turbo）均为纯文本模型，未处理图像、图表等视觉内容。如需名实相符，可引入 qwen-vl 系列模型解析文献中的图表
- **向量检索仍为线性比对**：已改为分页扫描 + 每文献最优块，峰值内存大幅下降，但计算复杂度仍随文本块总数线性增长；文献规模上千后建议迁移至 Milvus、Elasticsearch 或 MySQL 9 向量类型
- **文献列表无分页**：`GET /api/literature` 一次性返回全部文献，前端以无限滚动模拟分页
- **无 RBAC 权限体系**：所有登录用户共享同一文献库，仅删除操作有属主校验
- **旧令牌不兼容**：2026-09 修复了 JWT 主题用 username 而非 email 的存量 bug（用户名≠邮箱的账号此前无法通过认证），修复前的旧令牌需重新登录获取
- **测试覆盖有限**：Agent Controller / Service 集成测试缺失，LLM 调用均以单测 mock 覆盖
