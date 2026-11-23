<<<<<<< HEAD
# 实施路线图（ROADMAP）

> 项目：基于多模态大模型的学术文献精读与科研创新辅助系统（AcademicSearch）
> 当前基线：**2026-10-27 与代码对齐**；当前实现说明以 [README.md](./README.md) 为准，系统设计见 [DESIGN.md](./DESIGN.md)。
> 本文件同时记录"已完成历程"与"下一步计划"，不再作为历史存档。
=======
# Agent 架构实施路线图（ROADMAP）

> 项目：基于多模态大模型的学术文献精读与科研创新辅助系统
> 关联设计：[DESIGN.md](./DESIGN.md)
> 制定日期：2026-08-29 · 最近更新：2026-09-07（架构重构定稿）
> 状态总览：V1–V5 完成（Java 多 Agent 版）→ **R1 架构重构完成（Python 中心化编排 + 唯一 MCP + Milvus）** → V6 进行中
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

## 实施历程总览

```
<<<<<<< HEAD
2026-08-28  基础平台修复（配置环境变量化/CORS/属主校验/embedding 批量化/向量分页检索）
2026-08-29  设计定稿：Planner-Executor 多 Agent + 自研引擎（不引入 LangChain4j/Spring AI）
2026-09-03  前后端链路打通，启动环境问题清零（JAVA_HOME/npm 缓存/MySQL 服务）
2026-09-04  V1–V5 实施完成（Java 多 Agent：引擎/协作/SSE/会话/打磨）
2026-09-07  R1 架构重构：Agent 层改用 Python，中心化编排 + 唯一 MCP Server
            （R2 后编排器演进为 Supervisor + 3 专家子 Agent）；
            向量库 MySQL JSON 数组 → Milvus；Java 侧 29 个 Agent 文件删除
2026-09-11  Milvus（etcd + MinIO + standalone）在 Docker Desktop 上线运行
2026-09-16  V6-M1 图形验证码完成并端到端验证（注册强制校验）
2026-09-17  游客模式重新启用；修复 api.js rawRequest 路径拼接 bug（UI 全链路恢复）
2026-09-18  阶段全面测试：验证码/游客/注册/登录 E2E 全绿；CaptchaService 并发加锁
2026-09-29  上传 500 五连修（上传路径/.env 加载/embedding 请求体/Milvus 参数/references 截断）；
            DashScope Key 恢复，PDF→Milvus→Agent 全链路首次真实跑通，真实 token 统计产出
2026-09 下旬至 10 月  计划外增量：双知识库、LLM 知识抽取、阅读模式、研究工作区、
                    用户私有状态与引用格式、登录限流、Supervisor + 3 专家子 Agent
2026-10-27  文档全面对齐当前代码（README/DESIGN/ROADMAP/USER_MANUAL/.env.example）
2026-11-01  安全加固：git filter-repo 重写历史（移除泄露密钥/.venv，160MiB→25MiB）；
            API Key 掩码化；logout 吊销 refresh token（jti 黑名单）；access 2h；
            登录限流加 IP 维度；/api/auth/me 匿名 401；MCP 崩溃自动重启；
            前端请求超时/刷新失败语义/断流提示/StrictMode 纯函数更新/ErrorBoundary
2026-11-01  工程化：文献列表真分页；Milvus v2 标量字段（user_id/is_public）+ 可见性同步；
            deploy/（Nginx+Dockerfile+compose）入库；存量无主文献治理 SQL；
            pytest 29 例 + GitHub Actions CI；Spring Boot 3.2.0→3.5.7；Vite 5→8；react-router 6→7
=======
2026-08-28  基础平台修复（配置环境变量化/CORS/属主校验/embedding批量化/向量分页检索）
2026-08-29  设计定稿：Planner-Executor 多 Agent + 自研引擎（不引入 LangChain4j/Spring AI）
2026-09-03  前后端链路打通，启动环境问题清零（JAVA_HOME/npm缓存/MySQL服务）
2026-09-04  V1–V5 一次性实施完成（Java 多 Agent：引擎/协作/SSE/会话/打磨）
2026-09-04  认证增强规划（V6）、游客模式试做后撤销
2026-09-07  R1 架构重构：Agent 层全部改用 Python，中心化编排 + 唯一 MCP Server；
            向量库 MySQL JSON 数组 → Milvus；Java 侧 29 个 Agent 文件删除
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
```

---

## V1 — Agent 引擎与工具层（地基）✅ 2026-09-04

- [x] `AgentEngine` ReAct 核心循环 + 事件体系（agent_start/thinking/tool_call/tool_result/final）
- [x] `ToolRegistry` + JSON Schema 工具定义
- [x] 文献四原子工具：semantic_search / list_literatures / get_literature / read_section
- [x] DashScope function calling 报文（assistant.tool_calls / role=tool 回填）
- [x] 单元测试：引擎循环 / 工具结果截断 / 步数耗尽强制收敛
- [x] 偏差记录：同步端点未单独实现，直接落地 V3 的 SSE 端点

## V2 — 多 Agent 协作 ✅ 2026-09-04

- [x] DispatchTools：Agent-as-Tool 递归调度（dispatch_search/read/analyze），ObjectProvider 解循环依赖
- [x] 检索/精读/分析三专家 Agent 提示词；协调者 finalize 协议
- [x] 子 Agent 步数隔离与失败降级（错误文本不炸整体）

<<<<<<< HEAD
> 注：V2 最初为 Java 版实现；2026-09-07 R1 重构后在 Python `orchestrator.py` 中重建并沿用。

=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
## V3 — SSE 流式 + 过程可视化 ✅ 2026-09-04

- [x] DashScope 流式（增量输出 + usage 携带）
- [x] SSE 端点五类事件（start/step/token/final/error）；断连静默、后台持久化
- [x] 前端 agentChat 流式客户端（fetch + ReadableStream 解析 + 401 刷新重试）
- [x] CopilotPanel：轨迹时间线、打字机渲染、停止按钮（AbortController）

## V4 — 多轮对话记忆与会话管理 ✅ 2026-09-04

- [x] conversation / agent_message / agent_step 三表
- [x] 上下文裁剪（最近 6 轮、单条截断 2000 字）；标题=首问前 24 字
- [x] 会话列表/切换/删除/轨迹回放；属主校验

## V5 — 打磨与增强 ✅ 2026-09-04

- [x] 会话重命名（PATCH + 行内编辑）
- [x] 运行统计仪表：final 事件携带 stats（步数/工具调用数/耗时/tokens），持久化 + 前端统计条
<<<<<<< HEAD
- [x] 工具失败自动重试 1 次（Java 版 V5 实现；R1 重构后改为"工具错误 JSON 降级 + LLM 429 指数退避"，不再对工具本身重试）
- [x] 会话导出 Markdown（问题/回答/轨迹/参考文献/统计）
- [x] 登录板块完善：密码可见切换、记住邮箱、密码强度条、确认密码、过期 Toast
- [x] 真实 token 统计验证（2026-09-29 随 DashScope Key 恢复完成）

---

## R1 — 架构重构：Python 编排 + 唯一 MCP + Milvus ✅ 2026-09-07
=======
- [x] 工具失败自动重试 1 次
- [x] 会话导出 Markdown（问题/回答/轨迹/参考文献/统计）
- [x] 登录板块完善：密码可见切换、记住邮箱、密码强度条、确认密码、过期 Toast
- [ ] ⏳ 真实 token 统计验证（被 DashScope Key 封禁阻塞，挪入 V6 验收）

---

## R1 — 架构重构：Python 中心化编排 + 唯一 MCP + Milvus ✅ 2026-09-07
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

**动因**：Agent 逻辑与 Java Web 层耦合过重；引入 MCP 标准化工具协议；向量规模增长需要专业向量数据库。

### 任务完成情况

- [x] **agent-python 服务**（FastAPI :8000）：SSE 端点 + 会话 CRUD + JWT 共享鉴权（pyjwt 验 Java 签发的 token）+ MCP 子进程生命周期管理
- [x] **唯一 MCP Server**（mcp_server.py，stdio）：semantic_search / list_literatures / get_literature / read_section / translate / summarize / finalize 七工具，直连 MySQL 与 Milvus
<<<<<<< HEAD
- [x] **编排器**（orchestrator.py）：ReAct 循环 + finalize 协议 + 流式收尾 + usage 跨轮累计；SSE 事件流与前端完全兼容（R2 后扩展为 Supervisor + 检索/精读/分析 3 专家）
=======
- [x] **中心化编排器**（orchestrator.py）：单 Orchestrator ReAct 循环 + finalize 协议 + 流式收尾 + usage 跨轮累计；SSE 事件流与前端完全兼容（前端零感知切换）
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
- [x] **会话三表复用**：Python 直连 SQL，返回结构与 Java 版一致
- [x] **Java 侧瘦身**：删除 29 个 Agent 文件（引擎/编排/工具/控制器/实体/仓储/测试）；DashScopeService 精简回 chatRaw
- [x] **Milvus 集成**：docker-compose（etcd+MinIO+standalone，数据卷 D 盘）；Java `MilvusVectorStore`（SDK 2.5.10，@Lazy 客户端）+ Python `milvus_store`（pymilvus 2.5）；collection `literature_chunks`（HNSW+COSINE，dim 1536）
- [x] 存量数据迁移脚本 `migrate_vectors.py`（幂等：按文献先删后插）
- [x] 前端接入：Vite 代理 `/agent-api` → 8000；api.js 支持绝对路径路由
<<<<<<< HEAD
- [x] 验证：Java 测试通过；Python selfcheck（模块/DB/MCP stdio roundtrip）通过；三服务联调（health mcp:true / 代理转发 / 401 鉴权）通过

### 遗留验收（已关闭）

- [x] Milvus 启动 + `migrate_vectors.py` + 上传 PDF 验证向量入库与检索（2026-09-29）
- [x] DashScope Key 恢复后真实对话验证 function calling 全链路 + 真实 token 统计（2026-09-29）

---

## R2 — 计划外增量（2026-09 下旬至 10 月，已完成）

这些能力未在原 V6 计划中单列，但已进入当前实现：

- [x] **双知识库**：文献默认私有；自愿公开到社区总知识库；总库/私库列表、语义检索、Agent 工具三层按 `is_public`/`user_id` 隔离
- [x] **社区板块**：计算机/工程/理学/医学/社科 5 个板块；总库可按板块筛选
- [x] **社区页与内容推荐**（2026-10-27）：文献库收窄为"仅本人文献"，新增 `/community` 社区页；`/api/literature/recommendations` 以我的最近 3 篇文献（标题+摘要）为画像，在他人公开文献中做向量召回，返回推荐依据、相似度与片段
- [x] **LLM 知识抽取**：上传后异步抽取主要内容/方法/实验/结论；封面横幅标题修复；`/reprocess` 可重跑
- [x] **阅读模式**（`/read/:id`）：pdf.js 长卷懒加载、页码跳转、下载、目录（书签优先/文本扫描兜底）、AI 批注跳页
- [x] **研究工作区**（`/workspace/:id`）：左 PDF + 右 AI 对话 + 快捷问题 + 批注卡
- [x] **用户私有状态**：收藏/已读/笔记（`user_literature_state`）；文献库"只看收藏"与客户端排序
- [x] **引用格式**：BibTeX / GB/T 7714 / APA 一键复制
- [x] **登录限流**：同邮箱 15 分钟 5 次失败锁定 15 分钟
- [x] **游客模式**：首页/检索可浏览；功能页登录引导卡；提交检索/AI 对话引导登录并回跳
- [x] **Agent 演进**：Supervisor + 检索/精读/分析 3 专家子 Agent（子 Agent 受限工具集、4 步预算、禁止递归）
- [x] **上传链路修复**：绝对路径落盘、`.env` 加载、embedding 请求体、Milvus searchParams、references JSON 压缩
- [x] **api.js 路径修复**：仅 `/agent-api` 直连，其余统一补 `/api` 前缀
- [x] **文档对齐**：README/DESIGN/ROADMAP/USER_MANUAL/.env.example 与代码一致（2026-10-27）

---

## V6 — 原计划中的未完成项

### M1 多模态图表理解（未开始）

=======
- [x] 验证：Java 测试 10/10 绿；Python selfcheck（模块/DB/MCP stdio roundtrip）通过；三服务联调（health mcp:true / 代理转发 / 401 鉴权）通过

### 遗留验收（外部条件阻塞）

- [ ] ⏳ Docker Desktop 就绪后：`milvus/` 目录 `docker compose up -d` → 跑 `migrate_vectors.py` → 上传 PDF 验证向量入库与检索
- [ ] ⏳ DashScope 新 Key：真实对话验证 function calling 全链路 + 真实 token 统计

---

## V6 — 多模态与系统增强（进行中，目标 2026-12 结项）

### M1 多模态图表理解（核心立项目标，10 月）
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
- [ ] qwen-vl 接入：论文图表截图 → 视觉理解 → 图表问答工具 `analyze_figure`
- [ ] PDF 渲染分页截图管线（Java 侧 PDFBox 配合）
- [ ] 编排器提示词扩展多模态指令

<<<<<<< HEAD
### M2 检索质量评估（未开始）

=======
### M2 检索质量评估（10–11 月）
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
- [ ] 人工标注评估集（≥50 问答对）
- [ ] 指标：召回率@K / 回答忠实度 / 端到端延迟
- [ ] 消融实验：切分粒度（300/500/800）、TopK、HNSW 参数（M/ef）

<<<<<<< HEAD
### M3 认证安全增强

- [x] 图形验证码（hutool-captcha，内存池，5 分钟一次性，连错 5 次作废）— 2026-09-16
- [x] 登录失败限流（内存实现，单实例）— 2026-09
- [ ] 邮箱验证码注册（QQ SMTP + verify_code 表：60s 冷却 / 日限 10 封 / 连错 5 次作废）— 需用户提供 SMTP 授权码
- [ ] 忘记密码三步流程（邮箱 → 验证码+新密码 → 重置并吊销旧 refresh token）

### M4 工程完善（未开始/部分）

- [ ] 文献列表真分页（后端 Pageable）
- [ ] RBAC 角色体系（普通用户/管理员）
- [ ] 生产 Nginx 反代 + systemd 进程管理配置入库
=======
### M3 认证安全增强（11 月）
- [ ] 图形验证码（hutool-captcha，内存池，5 分钟一次性）
- [ ] 邮箱验证码注册（QQ SMTP + verify_code 表：60s 冷却 / 日限 10 封 / 连错 5 次作废）
- [ ] 忘记密码三步流程（邮箱 → 验证码+新密码 → 重置并吊销旧 refresh token）

### M4 工程完善（11–12 月）
- [ ] 文献列表真分页（后端 Pageable）
- [ ] RBAC 角色体系（普通用户/管理员）
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
- [ ] 软件著作权申请材料（2 件）
- [ ] 结项报告与演示

---

<<<<<<< HEAD
## 当前技术债与建议优先级

| 优先级 | 事项 | 说明 |
|--------|------|------|
| ✅ 已完成 | 工作区 `literatureId` 断链 | 2026-11-01 核实：前端 `WorkspaceView` 已透传 `literatureId`（此前文档过时），工作区对话已自动关联当前论文 |
| P1 | references 收集范围 | 目前只从 `semantic_search` 提取；建议从 `get_literature`/`read_section`/`summarize` 的 `literature_id` 一并收集 |
| ✅ 已完成 | 文献列表分页 | 2026-11-01：`GET /api/literature` 服务端 Pageable 真分页（page/size/sortBy/order/starred），前端两视图改为无限滚动加载下一页 |
| ✅ 已完成 | Milvus 过滤表达式 | 2026-11-01：collection 升级 v2（user_id/is_public 标量），标量过滤 + 可见性同步重插；v1 自动探测回退，`migrate_vectors_v2.py` 迁移 |
| P2 | 认证状态外置 | 验证码池/登录限流为 JVM 内存，多实例需迁移 Redis |
| 🔶 大部分完成 | 生产化 | 2026-11-01：`deploy/` 入库（Nginx 统一入口 + Dockerfile×3 + compose）；systemd 仍缺（容器化后非必需） |
| ✅ 已完成 | Python 测试与 CI | 2026-11-01：pytest 29 例（MCP 可见性/引用收集/编排器/结果压缩）；`.github/workflows/ci.yml` 三端流水线 |
| P3 | 多模态/OCR | qwen-vl + 扫描件 OCR，兑现项目名 |
| P3 | RBAC/管理后台 | 管理员审核社区文献、清理违规内容 |
| P3 | 遗留表清理 | MySQL `vector_index` 已废弃，确认无数据后删除 |

=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
## 风险与对策

| 风险 | 对策 |
|------|------|
<<<<<<< HEAD
| DashScope API 额度/Key 封禁 | Key 已于 2026-09-29 恢复可用；保留经典 RAG 降级；预算列支 API 服务费 |
| Windows 容器化部署受限 | Milvus 数据卷固定 D 盘；Java 客户端惰性连接，未启动不阻塞后端；Docker Desktop 启动后需手动 `docker compose up -d` |
| 16G 内存压力 | Milvus 约 3–4G + Java 约 1G + Python/MySQL/系统约 2G；不用时停止 Milvus 容器 |
| 多模态 token 消耗高 | 图表理解单独限流；分层摘要减少无效上下文 |
| 会话数据膨胀 | 轨迹内容截断 2000 字；后续增加定期清理策略 |
| 文献量增长 | 先做分页与 Milvus 标量过滤，再考虑分 collection/索引重建 |
=======
| DashScope API 额度/Key 封禁 | 已列为最高优先级外部依赖；经费预算列支 API 服务费；经典 RAG 降级路径保底 |
| Windows 容器化部署受限 | Milvus 数据卷固定 D 盘；Java 客户端惰性连接，未启动不阻塞后端 |
| 多模态 token 消耗高 | 图表理解单独限流；分层摘要减少无效上下文 |
| 会话数据膨胀 | 轨迹内容截断 2000 字；后续增加定期清理策略（M4） |
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
