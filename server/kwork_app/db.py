"""SQLite: пользователи, их ключи, заказы из ленты и вердикты под каждого.

Ключ приложения в базе не хранится — только SHA-256 от него. Ключ видит один
человек один раз, в своём чате с ботом; утёкшая база ничего не открывает.

Соединение открывается на каждую операцию: базу одновременно трогают поток
бота, поток API, цикл оценки и воркеры черновиков. WAL даёт им читать, пока
кто-то пишет.
"""

import hashlib
import json
import secrets
import sqlite3
import time
from contextlib import contextmanager

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id     INTEGER UNIQUE NOT NULL,
    username    TEXT NOT NULL DEFAULT '',
    key_hash    TEXT UNIQUE NOT NULL,
    profile     TEXT NOT NULL DEFAULT '',
    threshold   INTEGER NOT NULL DEFAULT 60,
    paused      INTEGER NOT NULL DEFAULT 0,
    awaiting    TEXT NOT NULL DEFAULT '',      -- '' | 'profile' — бот ждёт текст профиля
    last_seq    INTEGER NOT NULL DEFAULT 0,    -- до какого заказа ленты уже судили
    created_at  REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS orders (
    key         TEXT PRIMARY KEY,               -- "kwork:12345"
    seq         INTEGER UNIQUE NOT NULL,        -- порядковый номер в ленте парсера
    order_json  TEXT NOT NULL,
    first_seen  REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS matches (
    user_id     INTEGER NOT NULL,
    key         TEXT NOT NULL,
    stage       TEXT NOT NULL,                  -- judged | sent | error | skipped
    score       INTEGER,
    reason      TEXT NOT NULL DEFAULT '',
    verdict_json TEXT NOT NULL DEFAULT '{}',
    message_id  INTEGER,
    draft       TEXT NOT NULL DEFAULT '',
    draft_state TEXT NOT NULL DEFAULT '',       -- '' | pending | done | failed | skipped
    action      TEXT,                           -- responded | dismissed
    action_at   REAL,
    seen_at     REAL NOT NULL,
    sent_at     REAL,
    PRIMARY KEY (user_id, key)
);
CREATE INDEX IF NOT EXISTS matches_user_seen ON matches (user_id, seen_at);
CREATE TABLE IF NOT EXISTS usage (
    user_id     INTEGER NOT NULL,
    day         TEXT NOT NULL,
    judge_calls INTEGER NOT NULL DEFAULT 0,
    drafts      INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);
CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT
);
"""


def hash_key(key: str) -> str:
    return hashlib.sha256(key.strip().encode("utf-8")).hexdigest()


def new_key() -> str:
    # 32 байта случайности: перебрать такое через API нереально, отдельный
    # счётчик неудачных попыток не нужен.
    return "kt_" + secrets.token_urlsafe(32)


def today() -> str:
    return time.strftime("%Y-%m-%d")


class DB:
    def __init__(self, path):
        self.path = str(path)
        with self._conn() as c:
            c.execute("PRAGMA journal_mode=WAL")
            c.executescript(SCHEMA)

    @contextmanager
    def _conn(self):
        """Транзакция и закрытие: `with sqlite3.connect()` только коммитит, соединение висит до GC."""
        c = sqlite3.connect(self.path, timeout=10)
        c.row_factory = sqlite3.Row
        try:
            with c:
                yield c
        finally:
            c.close()

    # --- пользователи --------------------------------------------------------

    def user_by_chat(self, chat_id: int):
        with self._conn() as c:
            row = c.execute("SELECT * FROM users WHERE chat_id = ?", (chat_id,)).fetchone()
        return dict(row) if row else None

    def user_by_key(self, key: str):
        if not key:
            return None
        with self._conn() as c:
            row = c.execute("SELECT * FROM users WHERE key_hash = ?", (hash_key(key),)).fetchone()
        return dict(row) if row else None

    def user(self, user_id: int):
        with self._conn() as c:
            row = c.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
        return dict(row) if row else None

    def count_users(self) -> int:
        with self._conn() as c:
            return c.execute("SELECT COUNT(*) FROM users").fetchone()[0]

    def create_user(self, chat_id: int, username: str) -> tuple:
        """Возвращает (user, key). Ключ в открытом виде существует только в этом ответе."""
        key = new_key()
        with self._conn() as c:
            c.execute(
                "INSERT INTO users (chat_id, username, key_hash, awaiting, last_seq, created_at) "
                "VALUES (?, ?, ?, 'profile', ?, ?)",
                (chat_id, username or "", hash_key(key), self.max_seq(c), time.time()))
        return self.user_by_chat(chat_id), key

    def rotate_key(self, user_id: int) -> str:
        key = new_key()
        with self._conn() as c:
            c.execute("UPDATE users SET key_hash = ? WHERE id = ?", (hash_key(key), user_id))
        return key

    def update_user(self, user_id: int, **fields):
        allowed = {"profile", "threshold", "paused", "awaiting", "last_seq", "username"}
        fields = {k: v for k, v in fields.items() if k in allowed}
        if not fields:
            return
        sets = ", ".join(f"{k} = ?" for k in fields)
        with self._conn() as c:
            c.execute(f"UPDATE users SET {sets} WHERE id = ?", (*fields.values(), user_id))

    def set_profile(self, user_id: int, profile: str):
        """Новый профиль судит только новые заказы: прошлые вердикты были под старый."""
        with self._conn() as c:
            c.execute("UPDATE users SET profile = ?, awaiting = '', last_seq = ? WHERE id = ?",
                      (profile, self.max_seq(c), user_id))

    def active_users(self) -> list:
        with self._conn() as c:
            rows = c.execute(
                "SELECT * FROM users WHERE paused = 0 AND profile != '' ORDER BY id").fetchall()
        return [dict(r) for r in rows]

    # --- лента ---------------------------------------------------------------

    @staticmethod
    def max_seq(c) -> int:
        return c.execute("SELECT COALESCE(MAX(seq), 0) FROM orders").fetchone()[0]

    def add_orders(self, rows: list) -> int:
        """rows: [(seq, key, order_json, first_seen)] из feed.db парсера."""
        with self._conn() as c:
            before = c.total_changes
            c.executemany(
                "INSERT OR IGNORE INTO orders (seq, key, order_json, first_seen) VALUES (?, ?, ?, ?)",
                rows)
            return c.total_changes - before

    def orders_after(self, seq: int, limit: int) -> list:
        """Самые свежие заказы после seq — не больше limit, старые сверх лимита пропускаются."""
        with self._conn() as c:
            rows = c.execute(
                "SELECT seq, key, order_json FROM orders WHERE seq > ? ORDER BY seq DESC LIMIT ?",
                (seq, limit)).fetchall()
        return [(r["seq"], r["key"], json.loads(r["order_json"])) for r in reversed(rows)]

    def latest_seq(self) -> int:
        with self._conn() as c:
            return self.max_seq(c)

    def order(self, key: str):
        with self._conn() as c:
            row = c.execute("SELECT order_json FROM orders WHERE key = ?", (key,)).fetchone()
        return json.loads(row[0]) if row else None

    def prune(self, keep_days: int = 30):
        cutoff = time.time() - keep_days * 86400
        with self._conn() as c:
            c.execute("DELETE FROM matches WHERE seen_at < ? AND action IS NULL", (cutoff,))
            c.execute("DELETE FROM orders WHERE first_seen < ? AND key NOT IN (SELECT key FROM matches)",
                      (cutoff,))

    # --- вердикты ------------------------------------------------------------

    def save_match(self, user_id: int, key: str, stage: str, verdict: dict = None,
                   message_id: int = None, draft_state: str = ""):
        verdict = verdict or {}
        now = time.time()
        with self._conn() as c:
            c.execute(
                "INSERT INTO matches (user_id, key, stage, score, reason, verdict_json, message_id,"
                " draft_state, seen_at, sent_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                " ON CONFLICT(user_id, key) DO UPDATE SET stage = excluded.stage,"
                " score = excluded.score, reason = excluded.reason,"
                " verdict_json = excluded.verdict_json, message_id = excluded.message_id,"
                " draft_state = excluded.draft_state, sent_at = excluded.sent_at",
                (user_id, key, stage, verdict.get("score"), verdict.get("reason", ""),
                 json.dumps(verdict, ensure_ascii=False), message_id, draft_state, now,
                 now if stage == "sent" else None))

    def save_draft(self, user_id: int, key: str, draft: str, state: str):
        with self._conn() as c:
            c.execute("UPDATE matches SET draft = ?, draft_state = ? WHERE user_id = ? AND key = ?",
                      (draft, state, user_id, key))

    def match(self, user_id: int, key: str):
        with self._conn() as c:
            row = c.execute("SELECT * FROM matches WHERE user_id = ? AND key = ?",
                            (user_id, key)).fetchone()
        return dict(row) if row else None

    def mark_action(self, user_id: int, key: str, action: str) -> bool:
        with self._conn() as c:
            cur = c.execute(
                "UPDATE matches SET action = ?, action_at = ? WHERE user_id = ? AND key = ?",
                (action, time.time(), user_id, key))
            return cur.rowcount > 0

    def list_matches(self, user_id: int, stage: str = "sent", action: str = "none",
                     limit: int = 50, offset: int = 0) -> list:
        """Как State.list_orders у парсера, но только заказы этого пользователя."""
        query = ("SELECT m.*, o.order_json FROM matches m JOIN orders o ON o.key = m.key"
                 " WHERE m.user_id = ?")
        params = [user_id]
        if stage:
            query += " AND m.stage = ?"
            params.append(stage)
        if action == "none":
            query += " AND m.action IS NULL"
        elif action and action != "all":
            query += " AND m.action = ?"
            params.append(action)
        query += " ORDER BY m.seen_at DESC LIMIT ? OFFSET ?"
        params += [limit, offset]
        with self._conn() as c:
            rows = c.execute(query, params).fetchall()
        return [dict(r) for r in rows]

    def get_match_with_order(self, user_id: int, key: str):
        with self._conn() as c:
            row = c.execute(
                "SELECT m.*, o.order_json FROM matches m JOIN orders o ON o.key = m.key"
                " WHERE m.user_id = ? AND m.key = ?", (user_id, key)).fetchone()
        return dict(row) if row else None

    def digest(self, user_id: int, since: float) -> dict:
        """Те же поля, что /api/stats у парсера, — приложение их уже понимает."""
        with self._conn() as c:
            stages = dict(c.execute(
                "SELECT stage, COUNT(*) FROM matches WHERE user_id = ? AND seen_at >= ? GROUP BY stage",
                (user_id, since)).fetchall())
            actions = dict(c.execute(
                "SELECT action, COUNT(*) FROM matches WHERE user_id = ? AND action IS NOT NULL"
                " AND action_at >= ? GROUP BY action", (user_id, since)).fetchall())
        return {
            "seen": sum(stages.values()),
            # Словаря у бота приложения нет: всё, что не дошло, отсеяла модель.
            "rejected": stages.get("skipped", 0),
            "judged_out": stages.get("judged", 0),
            "sent": stages.get("sent", 0),
            "responded": actions.get("responded", 0),
            "dismissed": actions.get("dismissed", 0),
        }

    # --- лимиты --------------------------------------------------------------

    def usage(self, user_id: int) -> dict:
        with self._conn() as c:
            row = c.execute("SELECT judge_calls, drafts FROM usage WHERE user_id = ? AND day = ?",
                            (user_id, today())).fetchone()
        return {"judge_calls": row[0], "drafts": row[1]} if row else {"judge_calls": 0, "drafts": 0}

    def total_judge_calls_today(self) -> int:
        with self._conn() as c:
            return c.execute("SELECT COALESCE(SUM(judge_calls), 0) FROM usage WHERE day = ?",
                             (today(),)).fetchone()[0]

    def total_drafts_today(self) -> int:
        with self._conn() as c:
            return c.execute("SELECT COALESCE(SUM(drafts), 0) FROM usage WHERE day = ?",
                             (today(),)).fetchone()[0]

    def spend(self, user_id: int, judge_calls: int = 0, drafts: int = 0):
        with self._conn() as c:
            c.execute(
                "INSERT INTO usage (user_id, day, judge_calls, drafts) VALUES (?, ?, ?, ?)"
                " ON CONFLICT(user_id, day) DO UPDATE SET judge_calls = judge_calls + excluded.judge_calls,"
                " drafts = drafts + excluded.drafts",
                (user_id, today(), judge_calls, drafts))

    # --- служебное -----------------------------------------------------------

    def get_meta(self, key, default=None):
        with self._conn() as c:
            row = c.execute("SELECT value FROM meta WHERE key = ?", (key,)).fetchone()
        return row[0] if row else default

    def set_meta(self, key, value):
        with self._conn() as c:
            c.execute("INSERT INTO meta (key, value) VALUES (?, ?)"
                      " ON CONFLICT(key) DO UPDATE SET value = excluded.value", (key, str(value)))
