"""Сырая лента Kwork для соседнего сервиса kwork_app (бот @Parserkworktrbot).

Парсер и так скачивает ленту каждые пару минут — второй опросчик с теми же
cookies удвоил бы запросы к антиботу на одном аккаунте. Поэтому всё, что
пришло из ленты, без фильтров и до словаря, складывается сюда, а kwork_app
читает эту базу и судит заказы под своих пользователей сам.

Экспорт — побочная обязанность: любая ошибка здесь пишется в лог и
проглатывается, на уведомления владельцу она влиять не должна. Включается
только переменной FEED_DB_PATH — без неё (тесты, локальный запуск) молчит.
"""

import json
import logging
import os
import sqlite3
import time
from contextlib import contextmanager

log = logging.getLogger(__name__)

#: Сколько держать заказы. kwork_app читает ленту раз в минуту, неделя запаса —
#: чтобы он мог пролежать выключенным выходные и ничего не потерять.
KEEP_SECONDS = 7 * 86400
PRUNE_EVERY = 3600


class FeedExport:
    def __init__(self, path):
        self.path = str(path) if path else ""
        self._pruned_at = 0.0
        if self.path:
            try:
                with self._conn() as c:
                    c.execute(
                        "CREATE TABLE IF NOT EXISTS feed ("
                        " seq INTEGER PRIMARY KEY AUTOINCREMENT,"
                        " key TEXT UNIQUE NOT NULL,"
                        " source TEXT NOT NULL,"
                        " order_json TEXT NOT NULL,"
                        " first_seen REAL NOT NULL)")
            except Exception as e:
                log.warning("feed.db не открылась (%s) — экспорт ленты выключен", e)
                self.path = ""

    @classmethod
    def from_env(cls):
        return cls(os.environ.get("FEED_DB_PATH", ""))

    @property
    def enabled(self) -> bool:
        return bool(self.path)

    @contextmanager
    def _conn(self):
        c = sqlite3.connect(self.path, timeout=5)
        try:
            with c:
                yield c
        finally:
            c.close()

    def publish(self, source: str, orders: list) -> int:
        """Новые заказы ленты — в базу. Уже известные не трогает. Возвращает, сколько добавлено."""
        if not self.enabled or not orders:
            return 0
        now = time.time()
        try:
            with self._conn() as c:
                before = c.total_changes
                c.executemany(
                    "INSERT OR IGNORE INTO feed (key, source, order_json, first_seen) VALUES (?, ?, ?, ?)",
                    [(f"{source}:{o['id']}", source, json.dumps(o, ensure_ascii=False), now)
                     for o in orders if o.get("id")])
                added = c.total_changes - before
                if now - self._pruned_at > PRUNE_EVERY:
                    c.execute("DELETE FROM feed WHERE first_seen < ?", (now - KEEP_SECONDS,))
                    self._pruned_at = now
            return added
        except Exception as e:
            log.warning("Экспорт ленты не записался: %s", e)
            return 0
