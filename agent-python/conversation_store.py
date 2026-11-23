"""会话记忆：conversation / agent_message / agent_step 三表持久化。

返回结构与原 Java 版保持一致，前端无需感知后端语言切换。
"""
import json
from datetime import datetime

import config
import db


class NotFound(Exception):
    pass


def get_literature_brief(literature_id):
    """文献简要信息（工作区上下文注入用）：标题 + 可见性，不存在返回 None。"""
    return db.query_one(
        "SELECT id, title, user_id, is_public FROM literature WHERE id = %s",
        (int(literature_id),),
    )


def _now():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _truncate(text, max_len):
    text = text or ""
    return text if len(text) <= max_len else text[:max_len] + "..."


def get_or_create(user_id, conversation_id, first_question):
    if conversation_id is not None:
        row = db.query_one(
            "SELECT * FROM conversation WHERE id = %s AND user_id = %s",
            (conversation_id, user_id),
        )
        if not row:
            raise NotFound("会话不存在或无权访问")
        return row
    return {
        "id": db.execute(
            "INSERT INTO conversation (user_id, title, created_at, updated_at) VALUES (%s, %s, %s, %s)",
            (user_id, _truncate(first_question, 24), _now(), _now()),
        )
    }


def list_conversations(user_id):
    return db.query(
        "SELECT * FROM conversation WHERE user_id = %s ORDER BY updated_at DESC",
        (user_id,),
    )


def rename(user_id, conversation_id, title):
    row = db.query_one(
        "SELECT * FROM conversation WHERE id = %s AND user_id = %s",
        (conversation_id, user_id),
    )
    if not row:
        raise NotFound("会话不存在或无权访问")
    title = (title or "").strip()
    if not title:
        raise ValueError("会话标题不能为空")
    db.execute(
        "UPDATE conversation SET title = %s WHERE id = %s",
        (_truncate(title, 60), conversation_id),
    )
    return db.query_one("SELECT * FROM conversation WHERE id = %s", (conversation_id,))


def delete(user_id, conversation_id):
    row = db.query_one(
        "SELECT * FROM conversation WHERE id = %s AND user_id = %s",
        (conversation_id, user_id),
    )
    if not row:
        raise NotFound("会话不存在或无权访问")
    # 级联删除放同一事务：避免中途失败留下孤儿消息/轨迹
    with db.transaction() as cur:
        cur.execute(
            "DELETE FROM agent_step WHERE message_id IN "
            "(SELECT id FROM (SELECT id FROM agent_message WHERE conversation_id = %s) t)",
            (conversation_id,),
        )
        cur.execute(
            "DELETE FROM agent_message WHERE conversation_id = %s", (conversation_id,)
        )
        cur.execute("DELETE FROM conversation WHERE id = %s", (conversation_id,))


def _step_map(step):
    return {
        "agent": step["agent_name"],
        "stepIndex": step["step_index"],
        "type": step["step_type"],
        "toolName": step["tool_name"],
        "summary": step["content"],
        "content": step["content"],
        "durationMs": step["duration_ms"],
    }


def _parse_json(json_text, default):
    if not json_text:
        return default
    try:
        return json.loads(json_text)
    except json.JSONDecodeError:
        return default


def message_details(user_id, conversation_id):
    row = db.query_one(
        "SELECT * FROM conversation WHERE id = %s AND user_id = %s",
        (conversation_id, user_id),
    )
    if not row:
        raise NotFound("会话不存在或无权访问")
    messages = db.query(
        "SELECT * FROM agent_message WHERE conversation_id = %s ORDER BY id ASC",
        (conversation_id,),
    )
    result = []
    for message in messages:
        steps = db.query(
            "SELECT * FROM agent_step WHERE message_id = %s ORDER BY id ASC",
            (message["id"],),
        )
        result.append(
            {
                "id": message["id"],
                "role": message["role"],
                "content": message["content"],
                "references": _parse_json(message["references_json"], []),
                "stats": _parse_json(message["stats_json"], {}),
                "steps": [_step_map(s) for s in steps],
            }
        )
    return result


def history_messages(conversation_id):
    """多轮上下文：最近 N*2 条消息，单条截断 2000 字。"""
    messages = db.query(
        "SELECT role, content FROM agent_message WHERE conversation_id = %s ORDER BY id ASC",
        (conversation_id,),
    )
    keep = config.HISTORY_ROUNDS * 2
    recent = messages[-keep:] if len(messages) > keep else messages
    return [
        {"role": m["role"], "content": _truncate(m["content"], 2000)} for m in recent
    ]


def save_user_message(conversation_id, question):
    """用户消息即时落库：即使本轮执行失败/被中断，历史会话也看得到提问。"""
    return db.execute(
        "INSERT INTO agent_message (conversation_id, role, content, created_at) VALUES (%s, %s, %s, %s)",
        (conversation_id, "user", question, _now()),
    )


def save_assistant_message(conversation_id, answer, events, references, stats):
    """助手回答 + 执行轨迹 + 会话时间戳，单事务写入，返回助手消息 id。"""
    references_json = json.dumps(references, ensure_ascii=False) if references else None
    stats_json = json.dumps(stats, ensure_ascii=False) if stats else None
    now = _now()
    with db.transaction() as cur:
        cur.execute(
            "INSERT INTO agent_message (conversation_id, role, content, references_json, stats_json, created_at) "
            "VALUES (%s, %s, %s, %s, %s, %s)",
            (conversation_id, "assistant", answer, references_json, stats_json, now),
        )
        assistant_id = cur.lastrowid
        for event in events:
            content = event.get("content") or event.get("summary") or ""
            cur.execute(
                "INSERT INTO agent_step (message_id, agent_name, step_index, step_type, tool_name, content, duration_ms, created_at) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s)",
                (
                    assistant_id,
                    event.get("agent") or "orchestrator",
                    event.get("stepIndex") or 0,
                    event.get("type"),
                    event.get("toolName"),
                    _truncate(content, 2000),
                    event.get("durationMs"),
                    now,
                ),
            )
        cur.execute(
            "UPDATE conversation SET updated_at = %s WHERE id = %s", (now, conversation_id)
        )
    return assistant_id
