"""Чтение ленты, которую выкладывает личный парсер (freelance_parser/app/feed_export.py).

База парсера смонтирована только на чтение и открывается в режиме ro: этот
сервис не может ни испортить её, ни помешать парсеру писать.
"""

import logging
import sqlite3

log = logging.getLogger(__name__)


class FeedReader:
    def __init__(self, path):
        self.path = str(path)

    def read_after(self, seq: int, limit: int = 500) -> list:
        """[(seq, key, order_json, first_seen)] новее seq. Нет базы или она занята — пустой список."""
        try:
            c = sqlite3.connect(f"file:{self.path}?mode=ro", uri=True, timeout=5)
            try:
                return c.execute(
                    "SELECT seq, key, order_json, first_seen FROM feed WHERE seq > ? ORDER BY seq LIMIT ?",
                    (seq, limit)).fetchall()
            finally:
                c.close()
        except sqlite3.Error as e:
            # Парсер ещё не создал базу (первый запуск) или держит запись —
            # подождём следующего цикла, это не авария.
            log.info("лента пока недоступна: %s", e)
            return []
