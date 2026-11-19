"""Bot API без обвязки: у парсера chat_id зашит в объект, здесь получатель у каждого сообщения свой."""

import json
import logging
import time

import requests

log = logging.getLogger(__name__)

MAX_MSG = 4000


class TelegramBot:
    def __init__(self, token: str):
        self.token = token or ""

    @property
    def configured(self) -> bool:
        return bool(self.token) and not self.token.startswith("${")

    def _api(self, method: str, payload: dict, timeout: int = 25) -> dict:
        r = requests.post(f"https://api.telegram.org/bot{self.token}/{method}",
                          json=payload, timeout=timeout)
        if r.status_code == 429:
            wait = (r.json().get("parameters") or {}).get("retry_after", 5)
            time.sleep(wait + 1)
            raise RuntimeError("rate limited")
        data = r.json()
        if not data.get("ok"):
            # 403 — пользователь заблокировал бота; 400 — кривой HTML. Оба не лечатся повтором.
            raise PermissionError(f"{method}: {data.get('error_code')} {data.get('description')}")
        return data.get("result")

    def send(self, chat_id: int, text: str, keyboard: list = None) -> int:
        """message_id или None. Длинный текст обрезается: одно сообщение на заказ проще править."""
        if not self.configured:
            log.warning("бот не настроен, не отправлено в %s: %s", chat_id, text[:80])
            return None
        payload = {"chat_id": chat_id, "text": text[:MAX_MSG], "parse_mode": "HTML",
                   "disable_web_page_preview": True}
        if keyboard:
            payload["reply_markup"] = {"inline_keyboard": keyboard}
        for attempt in range(3):
            try:
                return (self._api("sendMessage", payload) or {}).get("message_id")
            except PermissionError as e:
                log.warning("sendMessage в %s: %s", chat_id, e)
                return None
            except Exception as e:
                log.warning("sendMessage в %s (%s/3): %s", chat_id, attempt + 1, e)
                time.sleep(2)
        return None

    def edit(self, chat_id: int, message_id: int, text: str, keyboard: list = None) -> bool:
        payload = {"chat_id": chat_id, "message_id": message_id, "text": text[:MAX_MSG],
                   "parse_mode": "HTML", "disable_web_page_preview": True}
        if keyboard:
            payload["reply_markup"] = {"inline_keyboard": keyboard}
        try:
            self._api("editMessageText", payload, timeout=15)
            return True
        except Exception as e:
            log.warning("editMessageText: %s", e)
            return False

    def edit_keyboard(self, chat_id: int, message_id: int, keyboard: list):
        try:
            self._api("editMessageReplyMarkup", {"chat_id": chat_id, "message_id": message_id,
                                                 "reply_markup": {"inline_keyboard": keyboard}},
                      timeout=10)
        except Exception as e:
            log.warning("editMessageReplyMarkup: %s", e)

    def answer_callback(self, callback_id: str, text: str = ""):
        try:
            self._api("answerCallbackQuery", {"callback_query_id": callback_id, "text": text},
                      timeout=10)
        except Exception as e:
            log.warning("answerCallbackQuery: %s", e)

    def set_commands(self, commands: list):
        try:
            self._api("setMyCommands", {"commands": [{"command": c, "description": d}
                                                     for c, d in commands]}, timeout=10)
        except Exception as e:
            log.warning("setMyCommands: %s", e)

    def get_updates(self, offset=None, timeout: int = 30) -> list:
        if not self.configured:
            time.sleep(timeout)
            return []
        try:
            r = requests.get(
                f"https://api.telegram.org/bot{self.token}/getUpdates",
                params={"timeout": timeout,
                        "allowed_updates": json.dumps(["message", "callback_query"]),
                        **({"offset": offset} if offset is not None else {})},
                timeout=timeout + 20)
            data = r.json()
            return data.get("result", []) if data.get("ok") else []
        except Exception as e:
            log.warning("getUpdates: %s", e)
            time.sleep(5)
            return []
