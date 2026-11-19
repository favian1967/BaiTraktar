"""Продуктовый слой: превращает сырые поля заказа в решение фрилансера.

Уведомление «вот заказ» бесполезно. Полезное уведомление отвечает на три
вопроса, которые фрилансер всё равно задаст:

    1. Не поздно ли? — возраст заказа и сколько уже откликнулось.
    2. Стоит ли вообще? — доходит ли этот заказчик до найма.
    3. Что писать?     — черновик отклика (собирается в llm.py).

Здесь только чистые функции без сети и без состояния: их легко проверять и
легко подкручивать под свои пороги.
"""

import time

# Через сколько откликов заказ считается «людным». На Kwork первые несколько
# откликов заказчик читает, дальше просмотр становится выборочным.
CROWD_WARN = 10
CROWD_DEAD = 25

# Возраст заказа. До получаса отклик реально попадает в первую волну.
FRESH_MINUTES = 30
STALE_HOURS = 12

# Сколько заказов должно быть за плечами у клиента, чтобы делать выводы о его
# проценте найма. На выборке из одного-двух заказов «0% найма» не значит ничего.
MIN_HISTORY = 3

# Граница, за которой писать полный отклик уже незачем. Отклик стоит коннектов
# и минут работы модели, а заказ, провисевший полдня с тремя десятками
# откликов, почти наверняка уже разобран. Пороги мягче, чем у CROWD_WARN:
# «людно» — повод предупредить, а вот «не писать вовсе» должно срабатывать
# заметно позже, иначе потеряем живые заказы.
DRAFT_MAX_AGE_HOURS = 3
DRAFT_MAX_COMPETITION = 10


def humanize_age(seconds: float) -> str:
    minutes = int(seconds // 60)
    if minutes < 1:
        return "только что"
    if minutes < 60:
        return f"{minutes} мин назад"
    hours = minutes // 60
    if hours < 24:
        return f"{hours} ч назад"
    return f"{hours // 24} дн назад"


def urgency(order: dict, now: float = None) -> dict:
    """Насколько срочно и осмысленно откликаться.

    Возвращает {level, icon, text}, где level:
        hot    — первая волна, бросай всё
        normal — обычный заказ
        late   — людно, шансы падают
        dead   — почти наверняка поздно
    """
    now = now or time.time()
    posted_at = order.get("posted_at")
    competition = order.get("competition")

    parts = []
    age_level = "normal"
    if posted_at:
        age = max(0.0, now - posted_at)
        parts.append(humanize_age(age))
        if age <= FRESH_MINUTES * 60:
            age_level = "hot"
        elif age >= STALE_HOURS * 3600:
            age_level = "late"

    crowd_level = "normal"
    if competition is not None:
        parts.append(f"{competition} откл.")
        if competition >= CROWD_DEAD:
            crowd_level = "dead"
        elif competition >= CROWD_WARN:
            crowd_level = "late"
        elif competition <= 3:
            crowd_level = "hot"

    # Учитываем только то, что биржа действительно сообщила: profi.ru не отдаёт
    # счётчик откликов, и «неизвестно» не должно гасить свежесть заказа.
    known = []
    if posted_at:
        known.append(age_level)
    if competition is not None:
        known.append(crowd_level)

    order_by_severity = ["hot", "normal", "late", "dead"]
    if not known:
        level = "normal"
    elif all(item == "hot" for item in known):
        # Первая волна по всем известным признакам — ради этого всё и затевалось.
        level = "hot"
    else:
        # Иначе — по худшему: свежий, но уже людный заказ это людный заказ.
        level = max(known, key=order_by_severity.index)

    icon, hint = {
        "hot": ("🔥", "первая волна — откликаться сейчас"),
        "normal": ("🕐", ""),
        "late": ("⏳", "уже людно"),
        "dead": ("🪦", "скорее всего поздно"),
    }[level]

    text = " · ".join(parts)
    if hint:
        text = f"{text} — {hint}" if text else hint
    return {"level": level, "icon": icon, "text": text}


def client_trust(order: dict) -> dict:
    """Что известно о заказчике: доходит ли он до найма.

    level: good | unknown | risky
    """
    orders = order.get("client_orders")
    hire_rate = order.get("client_hire_rate")

    if orders is None or hire_rate is None:
        return {"level": "unknown", "icon": "", "text": ""}

    if orders <= 1:
        # Первый заказ: статистики нет, но это и не повод подозревать.
        return {"level": "unknown", "icon": "🆕",
                "text": "новый заказчик, истории пока нет"}

    text = f"{orders} заказов, нанимает в {hire_rate}%"
    if orders < MIN_HISTORY:
        # Данные есть, но их слишком мало для вывода — показываем, не судим.
        return {"level": "unknown", "icon": "👤", "text": f"{text} (мало данных)"}
    if hire_rate == 0:
        # Разместил несколько заказов и ни разу никого не выбрал.
        return {"level": "risky", "icon": "⚠️",
                "text": f"{text} — ни разу никого не нанял"}
    if hire_rate < 30:
        return {"level": "risky", "icon": "⚠️", "text": text}
    if hire_rate >= 60:
        return {"level": "good", "icon": "✅", "text": text}
    return {"level": "unknown", "icon": "👤", "text": text}


def should_warn(order: dict, now: float = None) -> bool:
    """Стоит ли явно предупредить, что заказ, вероятно, пустая трата времени."""
    return (urgency(order, now)["level"] == "dead"
            or client_trust(order)["level"] == "risky")


def draft_skip_reason(order: dict, now: float = None):
    """Почему полный отклик писать не стоит. None — стоит.

    Полный текст пишем только тому, на что реально успеваем: на заказе не
    больше DRAFT_MAX_COMPETITION откликов. Причина возвращается текстом и
    уходит в сообщение — раньше там всегда стояло «людный», даже когда заказ
    отсеял возраст при нуле откликов (08.11.2026).

    Возраст решает, только когда счётчик откликов неизвестен. Если он есть,
    он точнее: заказ, провисевший семь часов без единого отклика, никто не
    разобрал, и как раз на него отклик нужнее всего.

    Неизвестность не наказываем: profi.ru не отдаёт ни возраста, ни счётчика
    откликов, и по пустым полям нельзя заключить, что заказ протух.
    """
    now = now or time.time()

    competition = order.get("competition")
    if competition is not None:
        if competition > DRAFT_MAX_COMPETITION:
            return f"уже {competition} откл."
        return None

    posted_at = order.get("posted_at")
    if posted_at and (now - posted_at) > DRAFT_MAX_AGE_HOURS * 3600:
        return f"заказу {humanize_age(now - posted_at)}"
    return None


def deserves_draft(order: dict, now: float = None) -> bool:
    """Стоит ли тратить модель и коннекты на полный отклик."""
    return draft_skip_reason(order, now) is None


def priority_score(order: dict, llm_score: int, now: float = None) -> int:
    """Единая цифра для сортировки: качество заказа с поправкой на реальность.

    Отличный по содержанию заказ, где уже 40 откликов и заказчик никогда никого
    не нанимал, полезен меньше, чем средний, но свежий и от живого клиента.
    """
    score = llm_score
    score += {"hot": 15, "normal": 0, "late": -10, "dead": -25}[urgency(order, now)["level"]]
    score += {"good": 10, "unknown": 0, "risky": -15}[client_trust(order)["level"]]
    return max(0, min(100, score))
