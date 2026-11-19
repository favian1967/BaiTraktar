"""Ступень 1: быстрая словарная фильтрация без обращения к ИИ.

Главная идея — не тупой блэклист подстрок, а взвешенный скоринг с учётом отрицаний.
"Сайт на Tilda" — минус. "Сайт, но не на Tilda" / "перенести с Tilda на нормальный стек" —
не минус, а наоборот сигнал, что заказчику нужен настоящий разработчик.
"""

import re
from dataclasses import dataclass, field

# Насколько далеко перед ключевым словом ищем отрицание.
NEGATION_WINDOW = 45


def normalize_text(text: str) -> str:
    text = (text or "").lower().replace("ё", "е").replace(" ", " ")
    return re.sub(r"\s+", " ", text).strip()


# Бюджет на биржах — свободный текст: "15000 ₽", "1 500 – 3 000 ₽", "от 2 000 ₽",
# "15к". Число может быть с разделителями разрядов и с копейками.
_NUMBER_RE = re.compile(r"\d[\d\s.,]*")
# "к"/"k"/"тыс." сразу после числа — это тысячи, а не начало соседнего слова.
_THOUSANDS_RE = re.compile(r"\s*(?:к|k|тыс\b\.?|тысяч[а-я]*)(?![а-яa-z])", re.I)
# Копейки: разделитель и одна-две цифры в самом конце числа. Три и больше —
# это разделитель разрядов ("1.500"), а не дробная часть.
_KOPEKS_RE = re.compile(r"[.,](\d{1,2})$")


def _to_number(raw: str):
    """"1 500,50" → 1500.5. Не число — None."""
    raw = raw.replace("\u00a0", " ").replace("\u202f", " ").strip(" .,")
    if not raw:
        return None
    frac = 0.0
    kopeks = _KOPEKS_RE.search(raw)
    if kopeks:
        frac = int(kopeks.group(1)) / 10 ** len(kopeks.group(1))
        raw = raw[:kopeks.start()]
    digits = re.sub(r"\D", "", raw)
    if not digits:
        return None
    return int(digits) + frac


def top_budget(price: str):
    """Верхняя сумма из текста бюджета. Сумм не нашлось — None.

    Именно верхняя, а не нижняя: у вилки "1 500 – 5 000 ₽" заказчик может
    дойти до потолка, и отсекать такой заказ по нижней границе значит терять
    нормальную работу. Планка снизу должна убирать заведомо дешёвое, а не
    всё, что дёшево начинается.
    """
    found = []
    for match in _NUMBER_RE.finditer(price or ""):
        value = _to_number(match.group())
        if value is None:
            continue
        if _THOUSANDS_RE.match(price, match.end()):
            value *= 1000
        found.append(value)
    return max(found) if found else None


@dataclass
class PrefilterResult:
    passed: bool
    score: int
    reason: str
    hits: list = field(default_factory=list)
    negated: list = field(default_factory=list)
    doubt: bool = False

    @property
    def to_llm(self) -> bool:
        """Отдавать ли заказ нейросети: уверенно прошёл или попал в зону сомнения."""
        return self.passed or self.doubt

    def explain(self) -> str:
        parts = [f"score={self.score}"]
        if self.hits:
            parts.append("совпадения: " + ", ".join(self.hits[:8]))
        if self.negated:
            parts.append("снято отрицанием: " + ", ".join(self.negated[:5]))
        if self.doubt:
            parts.append("зона сомнения → к ИИ")
        return "; ".join(parts)


class Prefilter:
    def __init__(self, keywords: dict, settings: dict):
        self.negations = [normalize_text(n) for n in keywords.get("negations", [])]
        self.strong = self._norm_map(keywords.get("strong_include", {}))
        self.weak = self._norm_map(keywords.get("weak_include", {}))
        self.soft_groups = {
            name: {
                "weight": g.get("weight", -2),
                "forms": [normalize_text(f) for f in g.get("forms", [])],
            }
            for name, g in keywords.get("soft_negative_groups", {}).items()
        }
        self.hard_stop = [normalize_text(k) for k in keywords.get("hard_stop", [])]
        self.hard_stop_override = [normalize_text(k) for k in keywords.get("hard_stop_override", [])]

        self.min_score = settings.get("min_score", 2)
        # Нижняя планка бюджета в рублях: заказы дешевле в Telegram не уходят.
        # 0 — планки нет.
        self.min_price = settings.get("min_price", 0)
        # Зона сомнения: заказы, недобравшие до порога вот столько, всё равно уходят
        # к ИИ, если в тексте есть хоть один сильный IT-сигнал. Нужна потому, что
        # поиск отрицаний — оконная эвристика: "перепишем всё, сейчас это Тильда,
        # больше её видеть не хотим" даёт минус, хотя заказ как раз наш. Дешевле
        # заплатить за лишний вызов модели, чем молча потерять заказ.
        self.doubt_margin = settings.get("doubt_margin", 4)
        # Бонус за отрицание платформы, которую мы не любим ("не на Тильде").
        self.negated_platform_bonus = settings.get("negated_platform_bonus", 2)
        self.hard_stop_penalty = settings.get("hard_stop_penalty", 8)
        # Насколько далеко должен стоять IT-сигнал от стоп-слова, чтобы его снять.
        # "репетитор по python" — сигнал рядом, стоп остаётся; "сервис поиска
        # репетиторов ... backend на django" — сигнал далеко, стоп снимается.
        self.override_min_distance = settings.get("override_min_distance", 30)
        # Сколько разных IT-сигналов снимают жёсткий стоп независимо от расстояния.
        self.override_min_signals = settings.get("override_min_signals", 3)

    @staticmethod
    def _norm_map(mapping: dict) -> dict:
        return {normalize_text(k): v for k, v in mapping.items() if v}

    def _is_negated(self, text: str, pos: int) -> bool:
        """Есть ли отрицание в окне перед вхождением ключевого слова."""
        window = text[max(0, pos - NEGATION_WINDOW):pos]
        # Отрицание из предыдущего предложения не считается.
        for sep in (". ", "! ", "? ", "; "):
            idx = window.rfind(sep)
            if idx != -1:
                window = window[idx + len(sep):]
        return any(n in window for n in self.negations)

    def _find(self, text: str, needle: str):
        """Все позиции вхождения с проверкой границы слова слева."""
        out, start = [], 0
        while True:
            i = text.find(needle, start)
            if i == -1:
                return out
            if i == 0 or not text[i - 1].isalnum():
                out.append(i)
            start = i + 1

    @staticmethod
    def _gap(pos: int, kw: str, stop_pos: int, stop_kw: str) -> int:
        """Сколько символов между словами (а не между их началами).

        Считать от начала до начала нельзя: длина самого стоп-слова тогда
        съедает весь бюджет расстояния, и в коротком тексте порог становится
        недостижим в принципе.
        """
        if pos >= stop_pos:
            return pos - (stop_pos + len(stop_kw))
        return stop_pos - (pos + len(kw))

    def _distant_override(self, text: str, stop_hits: list):
        """Признаки того, что стоп-слово — часть предметной области, а не профессия.

        Срабатывает по одному из двух признаков:

        1. IT-сигнал стоит далеко от стоп-слова, то есть не уточняет его.
        2. IT-сигналов набралось несколько. «Репетитор по python» — это всё ещё
           репетиторство: технология одна и она уточняет профессию. А «сервис
           поиска репетиторов: django, postgres, api» перечисляет стек — так
           описывают разработку продукта, а не преподавание.

        Ошибка здесь стоит по-разному: лишний заказ у ИИ — это доли копейки,
        потерянный заказ — упущенные деньги. Поэтому правило намеренно мягкое.
        """
        found = []
        for kw in self.hard_stop_override:
            for pos in self._find(text, kw):
                found.append(kw)
                if all(
                    self._gap(pos, kw, sp, skw) > self.override_min_distance
                    for sp, skw in stop_hits
                ):
                    return kw
                break        # для порога считаем сигналы, а не их повторы

        if len(found) >= self.override_min_signals:
            return f"{len(found)} IT-сигналов: " + ", ".join(found[:4])
        return None

    def budget_ok(self, price: str):
        """Дотягивает ли бюджет заказа до нижней планки.

        Возвращает пару (пропускать ли, найденный потолок бюджета). Проверка
        стоит до словаря и до ИИ: заказ за тысячу рублей не станет нужным от
        того, что он про Django, а вызов модели по нему всё равно платный.

        Бюджет не указан вовсе — пропускаем. Пустое поле не значит «дёшево»:
        на Kwork так вывешивают заказы, где цену обсуждают в переписке, и
        молчать про них было бы хуже, чем прислать лишнее.
        """
        if self.min_price <= 0:
            return True, None
        top = top_budget(price)
        if top is None:
            return True, None
        return top >= self.min_price, top

    def check(self, title: str, description: str) -> PrefilterResult:
        text = normalize_text(f"{title}. {description}")
        if not text:
            return PrefilterResult(False, 0, "пустой текст")

        score = 0
        hits, negated = [], []
        strong_hits = weak_hits = 0
        platform_penalty = False

        for table, tag in ((self.strong, "+"), (self.weak, "~")):
            for kw, weight in table.items():
                if self._find(text, kw):
                    score += weight
                    hits.append(f"{tag}{kw}({weight})")
                    if tag == "+":
                        strong_hits += 1
                    else:
                        weak_hits += 1

        # Нелюбимые платформы: минус только если о них говорят утвердительно.
        # Считаем по концепту целиком: если хоть одна словоформа отрицается
        # ("сейчас на Тильде, надоела, не хочу тильду") — заказчик от неё уходит.
        for name, group in self.soft_groups.items():
            positions = [
                (p, form) for form in group["forms"] for p in self._find(text, form)
            ]
            if not positions:
                continue
            if any(self._is_negated(text, p) for p, _ in positions):
                score += self.negated_platform_bonus
                negated.append(name)
            else:
                score += group["weight"]
                hits.append(f"-{name}({group['weight']})")
                platform_penalty = True

        # Живой IT-сигнал: сильное слово или пара слабых. Пара слабых нужна,
        # потому что половина заказов описана бытовым языком — "нужен сайт,
        # 8 блоков, форма заявки" не содержит ни одной технологии, но это работа.
        has_signal = strong_hits > 0 or weak_hits >= 2
        # Набор сигналов — это уже описание разработки, а не мимоходом названная
        # технология. Нужен там, где одного слова заведомо мало: "репетитор по
        # python" — репетиторство, "платформа репетиторов: django, api, админка" — нет.
        signal_set = strong_hits >= 2 or (strong_hits and weak_hits >= 2)

        # Жёсткий стоп — чужая профессия. Снимается, если IT-сигнал стоит далеко
        # ("репетитор по python" — всё ещё репетиторство, а "сервис поиска
        # репетиторов: django, api" — разработка).
        stop_hits = [(p, kw) for kw in self.hard_stop for p in self._find(text, kw)]
        stopped_by = None
        if stop_hits:
            override = self._distant_override(text, stop_hits)
            if override:
                hits.append(f"стоп '{stop_hits[0][1]}' снят сигналом '{override}'")
            else:
                stopped_by = stop_hits[0][1]
                score -= self.hard_stop_penalty

        if stopped_by:
            # Раньше здесь был безусловный отказ, и на нём терялись заказы, где
            # стоп-слово — это домен заказчика, а не его профессия ("дашборд для
            # отдела стройки", "бот для службы такси, водители видят заявки").
            # Отличить одно от другого словарём нельзя, поэтому при живом наборе
            # сигналов решение отдаём ИИ: лишний вызов дешевле потерянного заказа.
            doubt = signal_set and score + self.hard_stop_penalty >= self.min_score
            reason = (f"стоп-слово '{stopped_by}', но сигналов хватает — отдаём ИИ"
                      if doubt else f"не наша сфера: {stopped_by}")
            return PrefilterResult(False, score, reason, hits, negated, doubt=doubt)

        if score >= self.min_score:
            return PrefilterResult(True, score, "прошёл словарный фильтр", hits, negated)

        # Не дотянул. Спасаем два случая: счёт близко к порогу при живом сигнале,
        # и минус за платформу поверх сигнала — там обычно и прячется
        # непойманное отрицание ("уходим с Тильды на нормальный бэкенд").
        # Спасение по платформе — страховка от НЕпойманного отрицания, поэтому
        # требует хоть какого-то следа отрицания в тексте. "Соберите на Tilda" —
        # заказчик назвал стек, спрашивать модель не о чем.
        # Затребованный конструктор без единого следа отрицания — это ответ
        # заказчика про стек, а не сомнение: такой заказ не спасает и близкий счёт.
        platform_ok = not platform_penalty or any(n in text for n in self.negations)
        doubt = has_signal and platform_ok and (
            score >= self.min_score - self.doubt_margin or platform_penalty
        )
        reason = ("почти дотянул — отдаём ИИ" if doubt
                  else f"мало сигналов (нужно >= {self.min_score})")
        return PrefilterResult(False, score, reason, hits, negated, doubt=doubt)
