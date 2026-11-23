# AcademicSearch 生产部署（Nginx 统一入口）

## 架构

```
浏览器 ──► Nginx :80
            ├── /api/**      → Java 后端 (backend:8080)
            ├── /agent-api/**→ Python Agent (agent:8000，去前缀)
            └── /            → 前端静态文件 (React build，SPA fallback)
```

`/api` 与 `/agent-api` 的前缀分发由本目录的 `nginx.conf` 完成，
替代开发期 Vite dev server 的代理（README「已知限制」中的生产代理缺失问题）。

## 文件

| 文件 | 用途 |
|------|------|
| `nginx.conf` | Nginx 主配置：静态托管 + 双后端反代（含 SSE 长连接参数） |
| `Dockerfile.frontend` | 两阶段构建：node 编译前端 → nginx 托管静态文件 |
| `Dockerfile.backend`  | 两阶段构建：maven 编译 → JRE 21 运行 jar |
| `Dockerfile.agent`    | Python 3.12 + requirements.txt |
| `docker-compose.yml`  | 一键编排 frontend/backend/agent（MySQL/Milvus 用外部实例） |

## 快速开始

```bash
# 0) 前置：外部 MySQL 与 Milvus 已可访问（云服务或宿主机），
#    项目根目录有 .env（DB_*/DASHSCOPE_API_KEY/JWT_SECRET/MILVUS_URI 等）

# 1) 构建并启动三个服务
cd deploy && docker compose up -d --build

# 2) 健康检查
curl http://localhost/actuator/health          # Java（经 nginx → 8080）
curl http://localhost/agent-api/health         # Python（经 nginx → 8000）

# 3) 存量数据迁移（首次/升级时，宿主机执行，见 agent-python/ 对应脚本）
#    python agent-python/migrate_vectors_v2.py --dry-run
```

访问 http://localhost 即生产前端。

## 注意事项

- **JWT_SECRET 与密钥**：容器与外部服务共享同一 .env；密钥轮换后需同步重启 Java/Python。
- **uploads 目录**：Java 容器把上传文件写在容器内 `/app/uploads`，重建容器会丢；
  生产请挂载卷（compose 中已预留注释）。
- **HTTPS**：示例监听 80；公网部署请在 nginx.conf 增加 443/TLS 配置并启用 HSTS。
- **CORS**：统一入口后前后端同源，`app.cors.allowed-origins` 可保持默认（不再跨域）。
- **Milvus/MySQL 连接**：容器内访问宿主机服务用 `host.docker.internal`
  （compose 中 MILVUS_URI 默认已这样设置；Linux 需在 compose 加 extra_hosts）。
