"""HTTP API для приложения Kwork Tracker — те же пути и поля, что у app/api.py парсера.

Разница одна: токен здесь не общий, а личный ключ из бота, и каждый запрос
видит только заказы своего владельца. Наружу порт не открыт — запросы приходят
через edge-Caddy по https://kwork.favian1967.site.

/api/me — дополнение: профиль и порог, чтобы приложение могло их показать и
поменять, не отправляя человека в бота.
"""

import json
import logging
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

from fp.insights import client_trust, urgency

from .bot import PROFILE_MAX, PROFILE_MIN

log = logging.getLogger(__name__)

MAX_LIMIT = 200
MAX_BODY = 16 * 1024


def public_order(row: dict, now: float = None) -> dict:
    """Строка matches + заказ из ленты → JSON в формате OrderDto приложения."""
    order = json.loads(row.get("order_json") or "{}")
    try:
        verdict = json.loads(row.get("verdict_json") or "{}")
    except ValueError:
        verdict = {}
    return {
        "id": row["key"],
        "source": order.get("source") or "kwork",
        "title": order.get("title") or "",
        "description": order.get("description") or "",
        "price": order.get("price") or "",
        "location": order.get("location") or "",
        "link": order.get("link") or "",
        "category": verdict.get("category") or "",
        "client": order.get("client") or "",
        "client_url": order.get("client_url") or "",
        "client_orders": order.get("client_orders"),
        "client_hire_rate": order.get("client_hire_rate"),
        "posted_at": order.get("posted_at"),
        "competition": order.get("competition"),
        "score": row.get("score"),
        "reason": row.get("reason") or "",
        "stage": row.get("stage"),
        "action": row.get("action"),
        "seen_at": row.get("seen_at"),
        "sent_at": row.get("sent_at"),
        "draft": row.get("draft") or "",
        # Без этого поля пустой draft неотличим от «ещё пишется» — и приложение
        # вечно показывало бы ожидание у заказов, где черновика не будет.
        "draft_state": row.get("draft_state") or "",
        "draft_origin": "cloud" if row.get("draft") else "",
        "draft_model": "",
        "notes": order.get("notes") or [],
        "urgency": urgency(order, now),
        "client_trust": client_trust(order),
    }


def public_me(user: dict) -> dict:
    return {"profile": user["profile"], "threshold": user["threshold"],
            "paused": bool(user["paused"]), "username": user["username"]}


class Handler(BaseHTTPRequestHandler):
    server_version = "KworkAppAPI/1"
    db = None

    def log_message(self, fmt, *args):
        log.debug("%s - %s", self.address_string(), fmt % args)

    def _user(self):
        header = self.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return None
        return self.db.user_by_key(header[len("Bearer "):].strip())

    def _send_json(self, status: int, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def _read_json_body(self) -> dict:
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            length = 0
        if not length or length > MAX_BODY:
            return {}
        try:
            data = json.loads(self.rfile.read(length).decode("utf-8"))
            return data if isinstance(data, dict) else {}
        except (ValueError, UnicodeDecodeError):
            return {}

    def do_GET(self):
        parsed = urlparse(self.path)
        path, qs = parsed.path, parse_qs(parsed.query)
        if path == "/api/health":
            self._send_json(200, {"ok": True, "time": time.time()})
            return
        user = self._user()
        if not user:
            self._send_json(401, {"error": "unauthorized"})
            return
        if path == "/api/orders":
            self._list(user, qs)
        elif path.startswith("/api/orders/"):
            row = self.db.get_match_with_order(user["id"], unquote(path[len("/api/orders/"):]))
            if row:
                self._send_json(200, public_order(row, time.time()))
            else:
                self._send_json(404, {"error": "not found"})
        elif path == "/api/stats":
            window = (qs.get("window") or ["today"])[0]
            since = time.time() - (7 * 86400 if window == "week" else 86400)
            self._send_json(200, self.db.digest(user["id"], since))
        elif path == "/api/me":
            self._send_json(200, public_me(user))
        else:
            self._send_json(404, {"error": "not found"})

    def do_POST(self):
        path = urlparse(self.path).path
        user = self._user()
        if not user:
            self._send_json(401, {"error": "unauthorized"})
            return
        body = self._read_json_body()
        if path.startswith("/api/orders/") and path.endswith("/action"):
            key = unquote(path[len("/api/orders/"):-len("/action")])
            action = body.get("action")
            if action not in ("responded", "dismissed"):
                self._send_json(400, {"error": "action must be responded|dismissed"})
            elif self.db.mark_action(user["id"], key, action):
                self._send_json(200, {"ok": True})
            else:
                self._send_json(404, {"error": "not found"})
        elif path == "/api/me":
            self._update_me(user, body)
        else:
            self._send_json(404, {"error": "not found"})

    def _list(self, user, qs):
        stage = (qs.get("stage") or ["sent"])[0] or None
        action = (qs.get("action") or ["none"])[0]
        try:
            limit = min(MAX_LIMIT, max(1, int((qs.get("limit") or [50])[0])))
            offset = max(0, int((qs.get("offset") or [0])[0]))
        except ValueError:
            self._send_json(400, {"error": "limit/offset must be integers"})
            return
        rows = self.db.list_matches(user["id"], stage=stage, action=action, limit=limit, offset=offset)
        now = time.time()
        self._send_json(200, {"orders": [public_order(r, now) for r in rows], "count": len(rows)})

    def _update_me(self, user, body):
        if "threshold" in body:
            t = body["threshold"]
            if not isinstance(t, int) or isinstance(t, bool) or not 0 <= t <= 100:
                self._send_json(400, {"error": "threshold must be 0..100"})
                return
            self.db.update_user(user["id"], threshold=t)
        if "profile" in body:
            p = body["profile"]
            if not isinstance(p, str) or not PROFILE_MIN <= len(p.strip()) <= PROFILE_MAX:
                self._send_json(400, {"error": f"profile must be {PROFILE_MIN}..{PROFILE_MAX} chars"})
                return
            self.db.set_profile(user["id"], p.strip())
        self._send_json(200, public_me(self.db.user(user["id"])))


def start(db, host: str = "0.0.0.0", port: int = 8080):
    bound = type("BoundHandler", (Handler,), {"db": db})
    httpd = ThreadingHTTPServer((host, port), bound)
    threading.Thread(target=httpd.serve_forever, name="api", daemon=True).start()
    log.info("API слушает %s:%s", host, port)
    return httpd
