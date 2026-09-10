# Agent 架构实施路线图（ROADMAP）

> 项目：基于多模态大模型的学术文献精读与科研创新辅助系统
> 关联设计：[DESIGN.md](./DESIGN.md)
> 制定日期：2026-08-29 · 最近更新：2026-09-07（架构重构定稿）
> 状态总览：V1–V5 完成（Java 多 Agent 版）→ **R1 架构重构完成（Python 中心化编排 + 唯一 MCP + Milvus）** → V6 进行中

## 实施历程总览

```
2026-08-28  基础平台修复（配置环境变量化/CORS/属主校验/embedding批量化/向量分页检索）
2026-08-29  设计定稿：Planner-Executor 多 Agent + 自研引擎（不引入 LangChain4j/Spring AI）
2026-09-03  前后端链路打通，启动环境问题清零（JAVA_HOME/npm缓存/MySQL服务）
2026-09-04  V1–V5 一次性实施完成（Java 多 Agent：引擎/协作/SSE/会话/打磨）
2026-09-04  认证增强规划（V6）、游客模式试做后撤销
2026-09-07  R1 架构重构：Agent 层全部改用 Python，中心化编排 + 唯一 MCP Server；
            向量库 MySQL JSON 数组 → Milvus；Java 侧 29 个 Agent 文件删除
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
- [x] 工具失败自动重试 1 次
- [x] 会话导出 Markdown（问题/回答/轨迹/参考文献/统计）
- [x] 登录板块完善：密码可见切换、记住邮箱、密码强度条、确认密码、过期 Toast
- [ ] ⏳ 真实 token 统计验证（被 DashScope Key 封禁阻塞，挪入 V6 验收）

---

## R1 — 架构重构：Python 中心化编排 + 唯一 MCP + Milvus ✅ 2026-09-07

**动因**：Agent 逻辑与 Java Web 层耦合过重；引入 MCP 标准化工具协议；向量规模增长需要专业向量数据库。

### 任务完成情况

- [x] **agent-python 服务**（FastAPI :8000）：SSE 端点 + 会话 CRUD + JWT 共享鉴权（pyjwt 验 Java 签发的 token）+ MCP 子进程生命周期管理
- [x] **唯一 MCP Server**（mcp_server.py，stdio）：semantic_search / list_literatures / get_literature / read_section / translate / summarize / finalize 七工具，直连 MySQL 与 Milvus
- [x] **中心化编排器**（orchestrator.py）：单 Orchestrator ReAct 循环 + finalize 协议 + 流式收尾 + usage 跨轮累计；SSE 事件流与前端完全兼容（前端零感知切换）
- [x] **会话三表复用**：Python 直连 SQL，返回结构与 Java 版一致
- [x] **Java 侧瘦身**：删除 29 个 Agent 文件（引擎/编排/工具/控制器/实体/仓储/测试）；DashScopeService 精简回 chatRaw
- [x] **Milvus 集成**：docker-compose（etcd+MinIO+standalone，数据卷 D 盘）；Java `MilvusVectorStore`（SDK 2.5.10，@Lazy 客户端）+ Python `milvus_store`（pymilvus 2.5）；collection `literature_chunks`（HNSW+COSINE，dim 1536）
- [x] 存量数据迁移脚本 `migrate_vectors.py`（幂等：按文献先删后插）
- [x] 前端接入：Vite 代理 `/agent-api` → 8000；api.js 支持绝对路径路由
- [x] 验证：Java 测试 10/10 绿；Python selfcheck（模块/DB/MCP stdio roundtrip）通过；三服务联调（health mcp:true / 代理转发 / 401 鉴权）通过

### 遗留验收（外部条件阻塞）

- [ ] ⏳ Docker Desktop 就绪后：`milvus/` 目录 `docker compose up -d` → 跑 `migrate_vectors.py` → 上传 PDF 验证向量入库与检索
- [ ] ⏳ DashScope 新 Key：真实对话验证 function calling 全链路 + 真实 token 统计

---

## V6 — 多模态与系统增强（进行中，目标 2026-12 结项）

### M1 多模态图表理解（核心立项目标，10 月）
- [ ] qwen-vl 接入：论文图表截图 → 视觉理解 → 图表问答工具 `analyze_figure`
- [ ] PDF 渲染分页截图管线（Java 侧 PDFBox 配合）
- [ ] 编排器提示词扩展多模态指令

### M2 检索质量评估（10–11 月）
- [ ] 人工标注评估集（≥50 问答对）
- [ ] 指标：召回率@K / 回答忠实度 / 端到端延迟
- [ ] 消融实验：切分粒度（300/500/800）、TopK、HNSW 参数（M/ef）

### M3 认证安全增强（11 月）
- [ ] 图形验证码（hutool-captcha，内存池，5 分钟一次性）
- [ ] 邮箱验证码注册（QQ SMTP + verify_code 表：60s 冷却 / 日限 10 封 / 连错 5 次作废）
- [ ] 忘记密码三步流程（邮箱 → 验证码+新密码 → 重置并吊销旧 refresh token）

### M4 工程完善（11–12 月）
- [ ] 文献列表真分页（后端 Pageable）
- [ ] RBAC 角色体系（普通用户/管理员）
- [ ] 软件著作权申请材料（2 件）
- [ ] 结项报告与演示

---

## 风险与对策

| 风险 | 对策 |
|------|------|
| DashScope API 额度/Key 封禁 | 已列为最高优先级外部依赖；经费预算列支 API 服务费；经典 RAG 降级路径保底 |
| Windows 容器化部署受限 | Milvus 数据卷固定 D 盘；Java 客户端惰性连接，未启动不阻塞后端 |
| 多模态 token 消耗高 | 图表理解单独限流；分层摘要减少无效上下文 |
| 会话数据膨胀 | 轨迹内容截断 2000 字；后续增加定期清理策略（M4） |
