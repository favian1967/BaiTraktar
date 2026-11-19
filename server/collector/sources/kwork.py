"""Клиент к ленте проектов kwork.ru.

Kwork стоит за Qrator, поэтому здесь тот же curl_cffi с имперсонацией Chrome,
что и у profi.ru: обычный HTTP-клиент ловит 403 на ровном месте. Cookies берутся
из cookies_kwork.txt (строка Cookie целиком из браузера) и обновляются командой
/set_cookies в Telegram — файл переписывается на лету.

Внутренний эндпоинт лениво отдаёт JSON только с X-Requested-With: XMLHttpRequest;
без него прилетает HTML страницы логина, что и считается протухшей сессией.
"""

import html
import logging
import random
import re
import time

from curl_cffi import requests as creq

from .base import SessionExpired, Source, blank_order, normalize, parse_timestamp

log = logging.getLogger(__name__)

PROJECTS_URL = "https://kwork.ru/projects"
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
      "Chrome/151.0.0.0 Safari/537.36")

# Маркеры страницы логина/капчи — по ним отличаем протухшую сессию от сбоя сети.
# Проверяются ТОЛЬКО на ответе, который не разобрался как JSON: слова вроде
# "captcha" сплошь и рядом встречаются в текстах самих заказов, и поиск по телу
# живого JSON объявлял сессию мёртвой на ровном месте.
LOGIN_MARKERS = ("<title>login", "<title>вход", "kwork.ru/login",
                 'action="/login"', "captcha", "qrator")

TAG_RE = re.compile(r"<[^>]+>")


def strip_html(text: str) -> str:
    """Описания на Kwork приходят с <br> и ссылками — конвейеру нужен чистый текст."""
    # Сначала снимаем экранирование, потом теги: часть разметки приезжает
    # уже экранированной (&lt;b&gt;), и в обратном порядке она осталась бы в тексте.
    text = html.unescape(text or "")
    text = re.sub(r"<br\s*/?>", " ", text, flags=re.I)
    return normalize(TAG_RE.sub(" ", text))


def _as_int(value):
    """Kwork отдаёт счётчики то числом, то строкой ("1" / 16 / None)."""
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


class KworkClient(Source):
    name = "kwork"

    def __init__(self, cookies_path, max_pages: int = 5):
        self.cookies_path = cookies_path
        self.max_pages = max_pages
        self._session = None

    # --- cookies -------------------------------------------------------------

    def _read_cookies(self) -> str:
        try:
            return self.cookies_path.read_text(encoding="utf-8").strip()
        except FileNotFoundError:
            return ""

    def write_cookies(self, raw: str) -> None:
        """Сохранить новую строку Cookie (команда /set_cookies) и переподключиться."""
        self.cookies_path.write_text(raw.strip(), encoding="utf-8")
        self.reset()

    def reset(self) -> None:
        self._session = None

    @property
    def session(self):
        if self._session is None:
            cookies = self._read_cookies()
            if not cookies:
                raise SessionExpired(f"нет cookies: {self.cookies_path.name} пуст")
            s = creq.Session(impersonate="chrome", timeout=30)
            s.headers.update({
                "accept": "application/json, text/plain, */*",
                "x-requested-with": "XMLHttpRequest",
                "cookie": cookies,
                "referer": "https://kwork.ru/projects?view=0",
                "user-agent": UA,
            })
            self._session = s
        return self._session

    # --- запросы -------------------------------------------------------------

    def _fetch_page(self, page: int = 1) -> dict:
        body = {"view": "0", "a": "1"}
        if page > 1:
            body["page"] = str(page)

        last = None
        for attempt in range(3):
            try:
                r = self.session.post(PROJECTS_URL, data=body)
            except SessionExpired:
                raise          # нет cookies — ретраить нечего
            except Exception as e:
                last = e
                log.warning("kwork сеть: %s (попытка %s/3)", e, attempt + 1)
                time.sleep(random.uniform(2, 5))
                continue

            if r.status_code in (301, 302, 401):
                raise SessionExpired(f"{r.status_code}: редирект на логин")
            if r.status_code in (403, 429, 502, 503):
                # Qrator придерживает — отступаем, но сессию считаем живой.
                last = RuntimeError(f"HTTP {r.status_code} (антибот)")
                log.warning("kwork %s, бэкофф", r.status_code)
                time.sleep(random.uniform(20, 40))
                continue
            r.raise_for_status()

            try:
                data = r.json()
            except Exception:
                text = r.text
                if any(m in text.lower() for m in LOGIN_MARKERS):
                    raise SessionExpired("вместо JSON пришла страница логина/капчи")
                raise SessionExpired(f"ответ не JSON: {text[:150]}")
            if not isinstance(data, dict) or not data.get("success"):
                raise SessionExpired(f"success=false: {str(data)[:150]}")
            return data

        raise last or RuntimeError("kwork: не удалось получить ленту")

    def get_orders(self, pages: int = None) -> list:
        """Лента. `pages` ограничивает обход: свежие заказы всегда на первой
        странице (лента отсортирована по date_active), и в частых проходах
        глубже лезть незачем — это лишние запросы к антиботу и лишние секунды
        до уведомления."""
        limit = min(self.max_pages, pages) if pages else self.max_pages
        out, page, last_page = [], 1, 1
        while page <= min(last_page, limit):
            data = self._fetch_page(page)
            pagination = (data.get("data") or {}).get("pagination") or {}
            if page == 1:
                last_page = int(pagination.get("last_page") or 1)
            for item in pagination.get("data") or []:
                order = self.parse_project(item)
                if order["id"] and order["title"]:
                    out.append(order)
            page += 1
            if page <= min(last_page, limit):
                time.sleep(random.uniform(1.5, 3.5))
        return out

    @staticmethod
    def _money(value) -> str:
        """Kwork отдаёт бюджет как "15000.00" — хвост из нулей в уведомлении лишний."""
        if value is None or value == "":
            return ""
        try:
            number = float(value)
        except (TypeError, ValueError):
            return normalize(str(value))
        return str(int(number)) if number.is_integer() else f"{number:.2f}"

    @classmethod
    def parse_project(cls, item: dict) -> dict:
        pid = item.get("id")
        low = cls._money(item.get("priceLimit"))
        high = cls._money(item.get("possiblePriceLimit"))
        budget = f"{low} – {high}" if high and high != low else low
        if budget:
            budget += " ₽"

        user = item.get("user") or {}
        user_data = user.get("data") or {}
        notes = []
        if item.get("isHigherPrice"):
            notes.append("заказчик готов доплатить сверх бюджета")
        if item.get("hasPortfolioAvailable"):
            notes.append("смотрит портфолио")

        return blank_order(
            id=str(pid or ""),
            title=normalize(item.get("name") or ""),
            description=strip_html(item.get("description") or ""),
            price=budget,
            location=normalize(f"срок до {item['max_days']} дн." if item.get("max_days") else ""),
            client=normalize(user.get("username") or ""),
            client_url=normalize(item.get("wantUserGetProfileUrl") or ""),
            # Сколько заказов клиент вообще размещал и как часто доходил до найма —
            # главный ответ на вопрос «стоит ли вообще тратить время».
            client_orders=_as_int(user_data.get("wants_count")),
            client_hire_rate=_as_int(user_data.get("wants_hired_percent")),
            competition=_as_int(item.get("kwork_count")),
            posted_at=parse_timestamp(item.get("date_create"), "%Y-%m-%d %H:%M:%S"),
            notes=notes,
            link=f"https://kwork.ru/projects/{pid}",
            source="kwork",
        )

    def expired_help(self) -> str:
        return ("Cookies kwork.ru протухли.\n\n"
                "Скопируйте строку Cookie из браузера "
                "(F12 → Network → любой запрос к kwork.ru → Request Headers)\n"
                "и пришлите боту: <code>/set_cookies &lt;строка&gt;</code>")
