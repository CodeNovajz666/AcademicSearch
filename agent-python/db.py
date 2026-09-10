"""MySQL 访问层：pymysql 同步连接，每次操作独立连接（毕设量级足够）。"""
import json

import pymysql

import config


def connect():
    return pymysql.connect(
        host=config.DB_HOST,
        port=config.DB_PORT,
        user=config.DB_USER,
        password=config.DB_PASSWORD,
        database=config.DB_NAME,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=True,
    )


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


def rows_to_json_str(obj):
    return json.dumps(obj, ensure_ascii=False, default=str)
