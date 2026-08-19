# 基于大语言模型的学术文献检索与问答系统（AcademicSearchBasedOnMLLM）

一个采用 RAG（Retrieval-Augmented Generation，检索增强生成）架构的学术文献检索与智能问答系统。用户上传 PDF 学术文献后，系统自动解析全文、切分文本块并生成向量索引；检索时通过向量相似度匹配相关文献，并结合通义千问大模型生成带有参考文献的中文回答。

## 1. 系统架构

整体为前后端分离的单体应用：

```
┌─────────────────────┐         ┌──────────────────────────────┐
│   前端 Vue 3 SPA     │  /api   │      后端 Spring Boot         │
│  (Vite :5173)       │ ──────► │      (Tomcat :8080)           │
│                     │  代理    │                              │
│  Element Plus UI    │         │  Controller → Service → JPA   │
└─────────────────────┘         │        │            │         │
                                │        ▼            ▼         │
                                │   MySQL example_db  DashScope │
                                │   (业务数据+向量)    (嵌入+对话) │
                                └──────────────────────────────┘
```

RAG 核心数据流：

```
上传阶段：PDF 文件 → PDFBox 提取全文 → 元数据抽取（标题/摘要/作者等）
        → 文本切分（500 字符/块，100 字符重叠）
        → DashScope text-embedding-v1 生成向量 → 存入 vector_index 表

问答阶段：用户提问 → 问题向量化 → 与全部文本块计算余弦相似度
        → 取 topK 去重后的文献 → 拼接上下文 → qwen-turbo 生成回答
        → 返回回答 + 参考文献列表
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
| 构建工具 | Maven | 产出 `literature-search-1.0.0.jar` |

### 前端

| 类别 | 技术 | 说明 |
|------|------|------|
| 框架 | Vue 3 (^3.4.21) | Composition API + `<script setup>` |
| 路由 | Vue Router (^4.3.0) | History 模式 |
| UI 组件库 | Element Plus (^2.6.1) | 表单、对话框、输入框等 |
| HTTP 客户端 | Axios (^1.6.7) | 统一调用后端 API |
| 构建工具 | Vite (^5.1.6) | 开发服务器含 `/api` 代理 |

### 外部服务

| 服务 | 模型 | 用途 |
|------|------|------|
| 阿里云 DashScope | text-embedding-v1 | 文本块与查询的向量嵌入 |
| 阿里云 DashScope | qwen-turbo | 基于检索上下文生成回答（temperature=0.7, max_tokens=2048） |

## 3. 功能模块

### 3.1 用户认证

- 邮箱 + 用户名 + 密码注册，密码 BCrypt 加密存储
- 登录颁发双令牌：access_token（24 小时）与 refresh_token（7 天），HMAC-SHA256 签名
- `JwtAuthenticationFilter` 拦截请求，从 `Authorization: Bearer <token>` 头解析身份
- 认证接口公开，其余接口均需有效令牌

### 3.2 文献管理

- 上传 PDF（单文件上限 50MB），同时可携带标题、作者、期刊、年份、DOI 等元数据
- PDFBox 提取全文后，自动从文本中抽取元数据：标题取第一行长度在 5–200 之间的文本；摘要取 "Abstract"/"摘要" 到 "Keywords"/"Introduction" 之间的内容
- 文件本体保存在服务器本地 `uploads/` 目录，数据库记录路径
- 删除文献时同步清理 PDF 文件与向量索引

### 3.3 语义检索

- 查询文本经 DashScope 向量化后，与库内全部文本块计算余弦相似度
- 返回 topK 篇去重文献，每条结果包含文献信息、相似度分数与匹配文本片段
- 前端以相似度百分比直观展示相关度

### 3.4 智能问答（RAG）

- 聊天式交互界面，问题默认检索 topK=5 篇文献
- 将检索到的文献标题、摘要与部分正文拼接为上下文，交由 qwen-turbo 生成中文回答
- 回答附带参考文献列表，可点击跳转文献详情页
- 库中无相关文献时返回提示，不调用大模型

## 4. 目录结构

```
AcademicSearchBasedOnMLLM/
├── backend/                          # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/example/literaturesearch/
│       ├── LiteratureSearchApplication.java   # 启动类
│       ├── config/
│       │   ├── DashScopeConfig.java           # DashScope API 配置
│       │   ├── SecurityConfig.java            # Spring Security 配置
│       │   └── WebConfig.java                 # CORS 跨域配置
│       ├── controller/
│       │   ├── AuthController.java            # 认证接口
│       │   ├── LiteratureController.java      # 文献管理接口
│       │   └── RagController.java             # RAG 问答接口
│       ├── dto/
│       │   ├── request/                       # LoginRequest、RegisterRequest 等
│       │   └── response/                      # ApiResponse、SearchResult 等
│       ├── entity/
│       │   ├── Literature.java                # 文献实体
│       │   ├── User.java                      # 用户实体
│       │   └── VectorIndex.java               # 向量索引实体
│       ├── exception/                         # 全局异常处理
│       ├── filter/
│       │   └── JwtAuthenticationFilter.java   # JWT 过滤器
│       ├── repository/                        # JPA 仓库层
│       ├── service/
│       │   ├── AuthService.java               # 认证服务
│       │   ├── DashScopeService.java          # DashScope 调用封装
│       │   ├── LiteratureService.java         # 文献管理服务
│       │   ├── RagService.java                # RAG 问答服务
│       │   └── VectorService.java             # 向量检索服务
│       └── util/
│           └── JwtUtil.java                   # JWT 工具类
└── frontend/                         # Vue 3 前端
    ├── index.html                    # 标题：学术科研文献检索系统
    ├── vite.config.js                # 端口 5173，/api 代理到 8080
    └── src/
        ├── main.js                   # 入口，挂载 Router + ElementPlus
        ├── App.vue                   # 根布局：Header / Main / Footer
        ├── router/index.js           # 路由定义
        └── views/
            ├── Home.vue              # 首页：统计卡片 + 最近上传
            ├── Upload.vue            # 文献上传页
            ├── Search.vue            # 语义检索页
            ├── LiteratureDetail.vue  # 文献详情页
            └── Rag.vue               # 智能问答页
```

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
| POST | `/api/literature/upload` | 上传 PDF（multipart/form-data），自动解析并生成向量索引 |
| GET | `/api/literature` | 获取全部文献列表 |
| GET | `/api/literature/{id}` | 获取单篇文献详情 |
| DELETE | `/api/literature/{id}` | 删除文献及其文件与向量索引 |
| GET | `/api/literature/search?query=xxx&topK=10` | 语义检索，返回相似度评分与文本片段 |

### 5.3 问答接口 `/api/rag`

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/rag/query` | 提交问题（body: `{question, topK}`），返回回答与参考文献 |
| GET | `/api/rag/query?question=xxx&topK=5` | GET 形式的同等功能 |

## 6. 数据库设计

数据库 `example_db`，共三张表，由 Hibernate 自动建表维护：

| 表 | 用途 | 关键字段 |
|----|------|----------|
| `literature` | 文献元数据与全文 | title, authors, abstract_text, journal, year, doi, file_name, file_path, content (LONGTEXT), created_at |
| `users` | 用户账户 | email (唯一), username, password (BCrypt) |
| `vector_index` | 向量索引 | literature_id, vector_data (JSON 数组文本), chunk_text, chunk_index, created_at |

向量不依赖专用向量数据库，以 JSON 数组存于 `vector_data` 字段，检索时在应用层计算余弦相似度。这一实现简单直接，但文献量增大后全量比对会成为性能瓶颈。

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

账号密码默认为 `root` / `123456`，可在 `backend/src/main/resources/application.yml` 中修改。

### 7.3 配置 DashScope API Key

在 `backend/src/main/resources/application.yml` 中填入自己的 Key：

```yaml
dashscope:
  api-key: sk-xxxxxxxxxxxxxxxx
```

### 7.4 启动后端

```bash
cd backend
mvn spring-boot:run
```

服务运行在 `http://localhost:8080`。

### 7.5 启动前端

```bash
cd frontend
npm install
npm run dev
```

开发服务器运行在 `http://localhost:5173`，`/api` 请求自动代理到后端 8080 端口。

### 7.6 使用流程

1. 访问 `http://localhost:5173`，注册并登录
2. 进入"上传"页面上传 PDF 学术文献，等待解析与向量索引完成
3. 在"检索"页输入自然语言关键词进行语义搜索
4. 在"问答"页用自然语言提问，获得带参考文献的 AI 回答

## 8. 已知限制与改进方向

- **多模态尚未落地**：项目名称含"多模态"，但当前 DashScope 调用（text-embedding-v1 与 qwen-turbo）均为纯文本模型，未处理图像、图表等视觉内容。如需名实相符，可引入 qwen-vl 系列模型解析文献中的图表
- **向量检索为全量线性比对**：文献规模上千后建议迁移至 Milvus、Elasticsearch 或 MySQL 9 向量类型
- **API Key 明文存放**：`application.yml` 中的密钥应改用环境变量注入，且不应提交到版本库
- **数据库凭据为默认弱口令**：部署前需修改 root 密码
- **CORS 全开放**：`WebConfig` 允许所有来源，生产环境应限定白名单
- **前端错误提示使用 alert**：可替换为 Element Plus 的 ElMessage 组件，体验更佳
