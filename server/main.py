"""kwork_app: бот @Parserkworktrbot и API приложения Kwork Tracker поверх ленты личного парсера."""

import json
import logging
import os
import re
import threading
from pathlib import Path

from kwork_app import api
from kwork_app.bot import BotHandler
from kwork_app.db import DB
from kwork_app.feed import FeedReader
from kwork_app.service import Service
from kwork_app.tg import TelegramBot

BASE = Path(__file__).resolve().parent


def load_config(path: Path) -> dict:
    """config.json с плейсхолдерами ${VAR} — секреты приходят из окружения (.env в compose)."""
    raw = path.read_text(encoding="utf-8")
    return json.loads(re.sub(r"\$\{(\w+)\}", lambda m: os.environ.get(m.group(1), m.group(0)), raw))


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    config = load_config(Path(os.environ.get("CONFIG_PATH") or BASE / "config.json"))
    db = DB(os.environ.get("APP_DB_PATH") or BASE / "data" / "app.db")
    feed = FeedReader(os.environ.get("FEED_DB_PATH") or BASE / "feed" / "feed.db")
    bot = TelegramBot(config.get("telegram", {}).get("bot_token", ""))
    if not bot.configured:
        raise SystemExit("TELEGRAM_BOT_TOKEN не задан")

    service = Service(db, feed, bot, config)
    api_cfg = config.get("api", {})
    api.start(db, api_cfg.get("host", "0.0.0.0"), int(api_cfg.get("port", 8080)))
    threading.Thread(target=BotHandler(db, bot, service).poll_forever, name="bot", daemon=True).start()
    service.loop()


if __name__ == "__main__":
    main()
