"""Общий интерфейс источника заказов.

Источник обязан отдавать плоские словари одного вида — всё, что идёт дальше по
конвейеру (дедуп, словарный фильтр, ИИ, Telegram), про биржи ничего не знает.

`id` уникален только внутри биржи, поэтому в базе он хранится как "kwork:12345".
"""

import re
import time

# Поля, которые обязан заполнить любой источник. Всё, что биржа не сообщает,
# остаётся None/"" — потребители обязаны это переживать.
ORDER_FIELDS = (
    # что за заказ
    "id", "title", "description", "price", "location", "link", "source",
    # насколько поздно откликаться: возраст заказа и сколько уже откликнулось
    "posted_at",          # float|None — epoch появления заказа
    "competition",        # int|None   — сколько исполнителей уже откликнулось
    # что за заказчик: главный фильтр «не тратить время впустую»
    "client", "client_url",
    "client_orders",      # int|None — сколько заказов разместил всего
    "client_hire_rate",   # int|None — % заказов, закончившихся наймом
    "notes",              # list[str] — пометки самой биржи
)


def blank_order(**values) -> dict:
    """Заказ со всеми полями контракта; заданные значения — поверх."""
    order = {f: None for f in ORDER_FIELDS}
    order.update({"id": "", "title": "", "description": "", "price": "",
                  "location": "", "link": "", "source": "", "client": "",
                  "client_url": "", "notes": []})
    order.update(values)
    return order


def parse_timestamp(raw, fmt: str) -> float:
    """Дата биржи → epoch. Кривой формат не должен ронять весь цикл."""
    if not raw:
        return None
    try:
        return time.mktime(time.strptime(str(raw)[:19], fmt))
    except (ValueError, OverflowError):
        return None


class SessionExpired(Exception):
    """Cookies протухли — нужен ручной или автоматический перелогин."""


def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


class Source:
    """База для адаптера биржи."""

    name = ""

    def get_orders(self, pages: int = None) -> list:
        """Свежая лента, приведённая к общему виду. Кидает SessionExpired.

        `pages` — потолок страниц на этот проход; None означает «как настроено».
        """
        raise NotImplementedError

    def reset(self) -> None:
        """Сбросить HTTP-сессию (после обновления cookies)."""

    def expired_help(self) -> str:
        """Что написать в Telegram, когда сессия протухла."""
        return f"Сессия {self.name} недействительна."
