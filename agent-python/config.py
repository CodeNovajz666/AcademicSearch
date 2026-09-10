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

ORCHESTRATOR_MODEL = os.getenv("ORCHESTRATOR_MODEL", "qwen-plus")
ORCHESTRATOR_MAX_STEPS = int(os.getenv("ORCHESTRATOR_MAX_STEPS", "8"))
HISTORY_ROUNDS = int(os.getenv("HISTORY_ROUNDS", "6"))

EMBEDDING_MODEL = "text-embedding-v1"
SKILL_MODEL = os.getenv("SKILL_MODEL", "qwen-turbo")

MILVUS_URI = os.getenv("MILVUS_URI", "http://localhost:19530")
