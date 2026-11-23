<<<<<<< HEAD
"""MySQL 访问层：pymysql 同步连接。

约定：
- 单条语句走 query/query_one/execute（每次独立连接，autocommit，够用且无状态）。
- 多语句写操作（一轮对话落库、级联删除）必须用 transaction()：
  单连接单事务，要么全部提交、要么全部回滚，且把原先“每条语句一次建连”
  压缩为整批一次建连。
- 在 async（FastAPI/Orchestrator）上下文中调用任何本模块函数时，
  请用 asyncio.to_thread 包裹，避免同步 IO 阻塞事件循环。
"""
import json
from contextlib import contextmanager
=======
"""MySQL 访问层：pymysql 同步连接，每次操作独立连接（毕设量级足够）。"""
import json
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

import pymysql

import config


<<<<<<< HEAD
def _connect(autocommit=True):
=======
def connect():
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    return pymysql.connect(
        host=config.DB_HOST,
        port=config.DB_PORT,
        user=config.DB_USER,
        password=config.DB_PASSWORD,
        database=config.DB_NAME,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
<<<<<<< HEAD
        autocommit=autocommit,
    )


def connect():
    return _connect(autocommit=True)


=======
        autocommit=True,
    )


>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
def query(sql, args=None):
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, args or ())
            return cur.fetchall()


def query_one(sql, args=None):
    rows = query(sql, args)
    return rows[0] if rows else None


def execute(sql, args=None):
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, args or ())
            return cur.lastrowid


<<<<<<< HEAD
@contextmanager
def transaction():
    """单连接单事务：yield 一个 cursor，块正常结束则 commit，异常则 rollback。"""
    conn = _connect(autocommit=False)
    try:
        with conn.cursor() as cur:
            yield cur
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def rows_to_json_str(obj):
    return json.dumps(obj, ensure_ascii=False, default=str)


def get_user_llm_settings(user_id):
    """读取用户级智能体配置（自备 Key / 模型覆盖）。

    返回 dict 或 None；列不存在或查询异常时返回 None，
    调用方据此回退到服务器 .env 默认配置。
    """
    try:
        return query_one(
            "SELECT dashscope_api_key, chat_model, subagent_model FROM users WHERE id=%s",
            (user_id,),
        )
    except Exception:
        return None


# 会话三表由本服务自建：Java 侧没有对应 JPA 实体（Hibernate 不会创建），
# 仓库也没有 SQL 迁移脚本，全新环境缺表会导致所有 /agent/* 接口报表不存在。
# 全部 CREATE TABLE IF NOT EXISTS，幂等；已有表（含历史列差异）不受影响。
_SCHEMA_STATEMENTS = (
    """
    CREATE TABLE IF NOT EXISTS conversation (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        user_id BIGINT NOT NULL,
        title VARCHAR(120) NOT NULL,
        created_at DATETIME NOT NULL,
        updated_at DATETIME NOT NULL,
        INDEX idx_conversation_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS agent_message (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        conversation_id BIGINT NOT NULL,
        role VARCHAR(20) NOT NULL,
        content LONGTEXT,
        references_json LONGTEXT NULL,
        stats_json LONGTEXT NULL,
        created_at DATETIME NOT NULL,
        INDEX idx_message_conversation (conversation_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS agent_step (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        message_id BIGINT NOT NULL,
        agent_name VARCHAR(50) NOT NULL,
        step_index INT NOT NULL DEFAULT 0,
        step_type VARCHAR(30),
        tool_name VARCHAR(80) NULL,
        content TEXT,
        duration_ms BIGINT NULL,
        created_at DATETIME NOT NULL,
        INDEX idx_step_message (message_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
)


def ensure_schema():
    """启动时确保会话三表存在（幂等）。"""
    conn = _connect(autocommit=True)
    try:
        with conn.cursor() as cur:
            for stmt in _SCHEMA_STATEMENTS:
                cur.execute(stmt)
    finally:
        conn.close()
=======
def rows_to_json_str(obj):
    return json.dumps(obj, ensure_ascii=False, default=str)
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
