"""Telegram: отправка заказов и приём действий пользователя."""

import html
import json
import logging
import time

import requests

from .insights import client_trust, urgency

log = logging.getLogger(__name__)

MAX_MSG = 4000
SOURCE_LABEL = {"kwork": "Kwork", "profi": "Profi.ru"}

# Порядок блоков в сообщении подчинён тому, как его читают: сначала «стоит ли
# вообще» (срочность и заказчик), потом суть, и только потом текст отклика.
LEVEL_ICON = {"hot": "🔥", "normal": "🟢", "late": "🟡", "dead": "🟠"}


def _split(text: str) -> list:
    if len(text) <= MAX_MSG:
        return [text]
    parts, buf = [], ""
    for line in text.splitlines(keepends=True):
        if len(buf) + len(line) > MAX_MSG:
            parts.append(buf)
            buf = line
        else:
            buf += line
    if buf.strip():
        parts.append(buf)
    return parts


class Telegram:
    def __init__(self, settings: dict):
        self.token = settings.get("bot_token", "")
        self.chat_id = settings.get("chat_id", "")

    @property
    def configured(self) -> bool:
        return bool(self.token and self.chat_id) and not self.token.startswith("${")

    def _api(self, method: str, payload: dict, timeout: int = 25):
        r = requests.post(f"https://api.telegram.org/bot{self.token}/{method}",
                          json=payload, timeout=timeout)
        if r.status_code == 429:
            wait = r.json().get("parameters", {}).get("retry_after", 5)
            time.sleep(wait + 1)
            raise RuntimeError("rate limited")
        r.raise_for_status()
        return r.json()

    def send(self, text: str, disable_preview: bool = True, keyboard: list = None):
        """Отправляет сообщение. Возвращает message_id последней части или False.

        message_id нужен, чтобы дописать в это же сообщение черновик отклика,
        когда он будет готов, — само уведомление ждать его не должно.
        """
        if not self.configured:
            log.warning("Telegram не настроен, сообщение не отправлено:\n%s", text)
            return False
        parts = _split(text)
        message_id = None
        for index, part in enumerate(parts):
            payload = {
                "chat_id": self.chat_id,
                "text": part,
                "parse_mode": "HTML",
                "disable_web_page_preview": disable_preview,
            }
            # Кнопки вешаются на последнее сообщение — под всем текстом.
            if keyboard and index == len(parts) - 1:
                payload["reply_markup"] = {"inline_keyboard": keyboard}
            for attempt in range(3):
                try:
                    reply = self._api("sendMessage", payload)
                    message_id = (reply.get("result") or {}).get("message_id")
                    break
                except Exception as e:
                    log.warning("Telegram ошибка (%s/3): %s", attempt + 1, e)
                    time.sleep(3)
            else:
                return False
        return message_id or True

    def get_updates(self, offset=None, timeout: int = 0) -> list:
        """Команды и нажатия кнопок. timeout=0 — не блокируемся между циклами."""
        if not self.configured:
            return []
        try:
            r = requests.get(
                f"https://api.telegram.org/bot{self.token}/getUpdates",
                params={"timeout": timeout,
                        "allowed_updates": json.dumps(["message", "callback_query"]),
                        **({"offset": offset} if offset is not None else {})},
                timeout=timeout + 20,
            )
            data = r.json()
            return data.get("result", []) if data.get("ok") else []
        except Exception as e:
            log.warning("getUpdates: %s", e)
            return []

    def answer_callback(self, callback_id: str, text: str = ""):
        try:
            self._api("answerCallbackQuery",
                      {"callback_query_id": callback_id, "text": text}, timeout=10)
        except Exception as e:
            log.warning("answerCallbackQuery: %s", e)

    def append_text(self, message_id: int, text: str, keyboard: list = None) -> bool:
        """Дописывает текст в уже отправленное сообщение (черновик отклика)."""
        if not (self.configured and isinstance(message_id, int)):
            return False
        payload = {"chat_id": self.chat_id, "message_id": message_id, "text": text,
                   "parse_mode": "HTML", "disable_web_page_preview": True}
        if keyboard:
            payload["reply_markup"] = {"inline_keyboard": keyboard}
        try:
            self._api("editMessageText", payload, timeout=15)
            return True
        except Exception as e:
            log.warning("editMessageText: %s", e)
            return False

    def replace_keyboard(self, message_id: int, keyboard: list):
        """Подменяет кнопки — чтобы было видно, что действие засчитано."""
        try:
            self._api("editMessageReplyMarkup",
                      {"chat_id": self.chat_id, "message_id": message_id,
                       "reply_markup": {"inline_keyboard": keyboard}}, timeout=10)
        except Exception as e:
            log.warning("editMessageReplyMarkup: %s", e)


def order_keyboard(order: dict) -> list:
    """Кнопки под заказом: открыть, отметить отклик, отметить промах.

    «Не то» — не только учёт: именно из этих отметок потом видно, где словарь и
    промпт пропускают мусор.
    """
    key = f"{order.get('source', '')}:{order.get('id', '')}"
    return [
        [{"text": "🔗 Открыть заказ", "url": order.get("link") or "https://kwork.ru"}],
        [{"text": "✅ Откликнулся", "callback_data": f"a:r:{key}"},
         {"text": "🚫 Не то", "callback_data": f"a:d:{key}"}],
    ]


def format_order(order: dict, verdict: dict, draft: str = "", origin: str = "",
                 model: str = "", status: str = "", now: float = None) -> str:
    """HTML-сообщение о подходящем заказе.

    Порядок неслучаен: фрилансер сначала решает «бежать или пропустить», и
    только потом читает описание. Поэтому срочность и заказчик — вверху.
    """
    esc = html.escape
    score = verdict.get("score", 0)
    urg = urgency(order, now)
    trust = client_trust(order)

    lines = [f"{LEVEL_ICON.get(urg['level'], '🟢')} <b>{esc(order['title'])}</b>"]

    label = SOURCE_LABEL.get(order.get("source", ""), order.get("source", ""))
    head = " · ".join(x for x in (label, f"{score}/100",
                                  esc(verdict.get("category") or "")) if x)
    lines.append(f"<i>{head}</i>")

    lines.append("")
    if urg["text"]:
        lines.append(f"{urg['icon']} {esc(urg['text'])}")
    if trust["text"]:
        client = f"{trust['icon']} {esc(trust['text'])}"
        if order.get("client"):
            client += f" · {esc(order['client'])}"
        lines.append(client)
    elif order.get("client"):
        lines.append(f"👤 {esc(order['client'])}")

    money = " · ".join(x for x in (order.get("price"), order.get("location")) if x)
    if money:
        lines.append(f"💰 {esc(money)}")
    for note in (order.get("notes") or [])[:3]:
        lines.append(f"   <i>{esc(note)}</i>")

    desc = order.get("description") or ""
    if desc:
        if len(desc) > 900:
            desc = desc[:900] + "…"
        lines.append(f"\n{esc(desc)}")

    if verdict.get("reason"):
        lines.append(f"\n🤖 {esc(verdict['reason'])}")
    if verdict.get("red_flags"):
        flags = "\n".join(f"• {esc(f)}" for f in verdict["red_flags"][:4])
        lines.append(f"\n⚠️ <b>Обратить внимание:</b>\n{flags}")

    if draft:
        # Подпись не косметика. Текст пишет то локальная GRAVITAS, то облако,
        # а внутри ПК ещё и запасная модель, если основная лежит, — качество у
        # них разное. Без подписи упавший туннель или сдохшую модель замечаешь
        # через неделю по тому, что отклики стали хуже.
        why = {"cloud_offline": "облако, ПК не отвечал",
               "cloud_failed": "облако, модели на ПК не справились"}
        marks = [m for m in (esc(model), why.get(origin, "")) if m]
        title = "📝 <b>Черновик отклика</b>"
        if marks:
            title += f" · <i>{' · '.join(marks)}</i>"
        # <code> в Telegram копируется одним касанием — отклик можно сразу
        # вставить в поле биржи, не выделяя текст руками.
        lines.append(f"\n{title} <i>(нажмите, чтобы скопировать)</i>:")
        lines.append(f"<code>{esc(draft)}</code>")
    elif status:
        # Живой статус вместо пустого места. Уведомление приходит раньше
        # черновика, а перебор моделей занимает минуты — по сообщению должно
        # быть видно не только «текст будет», но и как идут дела: молчащая
        # заглушка не даёт решить, ждать её или писать отклик самому.
        lines.append(f"\n{status}")

    return "\n".join(lines)
