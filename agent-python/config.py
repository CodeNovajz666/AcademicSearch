"""全局配置：从项目根目录 .env 加载，Agent 服务与 MCP Server 共用。"""
import os
from pathlib import Path

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
load_dotenv(ROOT / ".env")

DASHSCOPE_API_KEY = os.getenv("DASHSCOPE_API_KEY", "")
DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_NAME = os.getenv("DB_NAME", "example_db")
DB_USER = os.getenv("DB_USERNAME", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")

JWT_SECRET = os.getenv("JWT_SECRET", "")

# 对话模型统一为 qwen3.8-flash（思考模型，走 DashScope OpenAI 兼容端点；
# 经典 RAG 与知识抽取在 Java 侧同样使用该模型，见 application.yml）
ORCHESTRATOR_MODEL = os.getenv("ORCHESTRATOR_MODEL", "qwen3.8-flash")
ORCHESTRATOR_MAX_STEPS = int(os.getenv("ORCHESTRATOR_MAX_STEPS", "8"))
HISTORY_ROUNDS = int(os.getenv("HISTORY_ROUNDS", "6"))

# 子 Agent（检索/精读/分析专家）：模型与步数预算独立于协调者
SUBAGENT_MODEL = os.getenv("SUBAGENT_MODEL", "qwen3.8-flash")
SUBAGENT_MAX_STEPS = int(os.getenv("SUBAGENT_MAX_STEPS", "4"))

EMBEDDING_MODEL = "text-embedding-v1"
SKILL_MODEL = os.getenv("SKILL_MODEL", "qwen3.8-flash")

MILVUS_URI = os.getenv("MILVUS_URI", "http://localhost:19530")
# 须与 Java 后端 milvus.embedding-dim 一致，且与嵌入模型匹配（text-embedding-v1/v2 = 1536）
EMBEDDING_DIM = int(os.getenv("MILVUS_EMBEDDING_DIM", "1536"))
