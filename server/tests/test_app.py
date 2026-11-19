import json
import sqlite3
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

from kwork_app import api
from kwork_app.bot import BotHandler
from kwork_app.db import DB, hash_key
from kwork_app.feed import FeedReader
from kwork_app.service import Service


class FakeBot:
    configured = True

    def __init__(self):
        self.sent = []          # (chat_id, text, keyboard)
        self.edits = []
        self.callbacks = []
        self._next_id = 100

    def send(self, chat_id, text, keyboard=None):
        self.sent.append((chat_id, text, keyboard))
        self._next_id += 1
        return self._next_id

    def edit(self, chat_id, message_id, text, keyboard=None):
        self.edits.append((chat_id, message_id, text, keyboard))
        return True

    def edit_keyboard(self, chat_id, message_id, keyboard):
        self.edits.append((chat_id, message_id, None, keyboard))

    def answer_callback(self, callback_id, text=""):
        self.callbacks.append((callback_id, text))

    def texts_to(self, chat_id):
        return [t for c, t, _ in self.sent if c == chat_id]


class FakeJudge:
    """Ставит score из словаря по заголовку; считает запросы как настоящий."""

    batch_size = 8
    configured = True
    fail = False

    def __init__(self, scores):
        self.scores = scores
        self.calls_made = 0

    def __call__(self, settings, profile):   # фабрика: Service зовёт judge_factory(settings, profile)
        self.profile = profile
        return self

    def judge_many(self, orders):
        self.calls_made += -(-len(orders) // self.batch_size)
        if self.fail:
            raise RuntimeError("модель легла")
        return [{"suitable": self.scores.get(o["title"], 0) > 0, "score": self.scores.get(o["title"], 0),
                 "category": "", "reason": "тест", "red_flags": []} for o in orders]

    def draft(self, order):
        return "Добрый день, сделаю."


def order(n, title=None):
    return {"id": str(n), "title": title or f"Заказ {n}", "description": "описание", "price": "5000 ₽",
            "location": "", "link": f"https://kwork.ru/projects/{n}", "source": "kwork", "client": "",
            "client_url": "", "posted_at": None, "competition": 1, "client_orders": None,
            "client_hire_rate": None, "notes": []}


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.feed_path = root / "feed.db"
        with sqlite3.connect(self.feed_path) as c:
            c.execute("CREATE TABLE feed (seq INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT UNIQUE NOT NULL,"
                      " source TEXT NOT NULL, order_json TEXT NOT NULL, first_seen REAL NOT NULL)")
        self.db = DB(root / "app.db")
        self.bot = FakeBot()
        self.service = Service(self.db, FeedReader(self.feed_path), self.bot,
                               {"llm": {}, "limits": {"user_judge_calls": 3, "total_judge_calls": 100}})
        self.handler = BotHandler(self.db, self.bot, self.service)

    def tearDown(self):
        self.service.drafts.shutdown(wait=True)
        self.tmp.cleanup()

    def publish(self, *orders):
        with sqlite3.connect(self.feed_path) as c:
            c.executemany("INSERT OR IGNORE INTO feed (key, source, order_json, first_seen) VALUES (?, ?, ?, 0)",
                          [(f"kwork:{o['id']}", "kwork", json.dumps(o, ensure_ascii=False)) for o in orders])
        self.service.pull_feed()

    def message(self, chat_id, text, username="u"):
        self.handler.handle({"update_id": 1, "message": {"chat": {"id": chat_id, "type": "private"},
                                                         "from": {"username": username}, "text": text}})

    def register(self, chat_id, profile="Делаю сайты на Python и телеграм-ботов, беру заказы от 3000 рублей"):
        self.message(chat_id, "/start")
        key = next(t for t in self.bot.texts_to(chat_id) if "<code>" in t).split("<code>")[1].split("</code>")[0]
        self.message(chat_id, profile)
        return self.db.user_by_chat(chat_id), key


class TestRegistration(Base):
    def test_start_issues_key_and_stores_only_hash(self):
        user, key = self.register(1)
        self.assertTrue(key.startswith("kt_"))
        self.assertEqual(user["key_hash"], hash_key(key))
        raw = sqlite3.connect(self.db.path).execute("SELECT * FROM users").fetchall()
        self.assertNotIn(key, repr(raw))
        self.assertEqual(self.db.user_by_key(key)["chat_id"], 1)

    def test_short_profile_rejected(self):
        self.message(2, "/start")
        self.message(2, "сайты")
        self.assertEqual(self.db.user_by_chat(2)["profile"], "")
        self.assertEqual(self.db.user_by_chat(2)["awaiting"], "profile")

    def test_newkey_revokes_old(self):
        user, key = self.register(3)
        self.message(3, "/newkey")
        self.assertIsNone(self.db.user_by_key(key))
        new = self.bot.texts_to(3)[-1].split("<code>")[1].split("</code>")[0]
        self.assertEqual(self.db.user_by_key(new)["id"], user["id"])

    def test_max_users(self):
        self.service.limits["max_users"] = 1
        self.register(4)
        self.message(5, "/start")
        self.assertIsNone(self.db.user_by_chat(5))

    def test_group_chats_ignored(self):
        self.handler.handle({"update_id": 1, "message": {"chat": {"id": -9, "type": "group"},
                                                         "from": {}, "text": "/start"}})
        self.assertIsNone(self.db.user_by_chat(-9))
        self.assertEqual(self.bot.sent, [])

    def test_threshold(self):
        self.register(6)
        self.message(6, "/threshold 75")
        self.assertEqual(self.db.user_by_chat(6)["threshold"], 75)
        self.message(6, "/threshold 300")
        self.assertEqual(self.db.user_by_chat(6)["threshold"], 75)


class TestJudging(Base):
    def test_only_new_orders_after_registration(self):
        self.publish(order(1, "старый"))
        user, _ = self.register(10)
        self.publish(order(2, "новый"))
        judge = FakeJudge({"старый": 90, "новый": 90})
        self.service.judge_user(self.db.user(user["id"]), judge_factory=judge)
        sent = [t for t in self.bot.texts_to(10) if "Открыть" not in t and "<b>" in t]
        self.assertTrue(any("новый" in t for t in sent))
        self.assertFalse(any("старый" in t for t in sent))

    def test_threshold_and_isolation(self):
        a, _ = self.register(11)
        b, _ = self.register(12)
        self.db.update_user(b["id"], threshold=95)
        self.publish(order(3, "бот на python"))
        for u in (a, b):
            self.service.judge_user(self.db.user(u["id"]), judge_factory=FakeJudge({"бот на python": 80}))
        self.assertEqual(self.db.match(a["id"], "kwork:3")["stage"], "sent")
        self.assertEqual(self.db.match(b["id"], "kwork:3")["stage"], "judged")
        self.assertEqual(len(self.db.list_matches(b["id"])), 0)

    def test_daily_limit_skips_and_notifies_once(self):
        user, _ = self.register(13)
        self.service.limits["user_judge_calls"] = 0
        self.publish(order(4), order(5))
        self.service.judge_user(self.db.user(user["id"]), judge_factory=FakeJudge({}))
        self.publish(order(6))
        self.service.judge_user(self.db.user(user["id"]), judge_factory=FakeJudge({}))
        self.assertEqual(self.db.match(user["id"], "kwork:4")["stage"], "skipped")
        self.assertEqual(sum("лимит" in t for t in self.bot.texts_to(13)), 1)

    def test_limit_keeps_newest(self):
        user, _ = self.register(14)
        self.service.limits["user_judge_calls"] = 1
        self.publish(*[order(n) for n in range(100, 112)])     # 12 заказов, влезает 8
        self.service.judge_user(self.db.user(user["id"]), judge_factory=FakeJudge({}))
        self.assertEqual(self.db.match(user["id"], "kwork:100")["stage"], "skipped")
        self.assertEqual(self.db.match(user["id"], "kwork:111")["stage"], "judged")

    def test_llm_failure_retries_next_cycle(self):
        user, _ = self.register(15)
        self.publish(order(7, "x"))
        judge = FakeJudge({"x": 90})
        judge.fail = True
        self.service.judge_user(self.db.user(user["id"]), judge_factory=judge)
        self.assertIsNone(self.db.match(user["id"], "kwork:7"))
        judge.fail = False
        self.service.judge_user(self.db.user(user["id"]), judge_factory=FakeJudge({"x": 90}))
        self.assertEqual(self.db.match(user["id"], "kwork:7")["stage"], "sent")
        self.assertGreaterEqual(self.db.usage(user["id"])["judge_calls"], 2)

    def test_draft_written_and_message_edited(self):
        user, _ = self.register(16)
        self.publish(order(8, "y"))
        self.service.judge_user(self.db.user(user["id"]), judge_factory=FakeJudge({"y": 90}))
        self.service.drafts.shutdown(wait=True)
        m = self.db.match(user["id"], "kwork:8")
        self.assertEqual(m["draft_state"], "done")
        self.assertTrue(any("Добрый день" in (t or "") for _, _, t, _ in self.bot.edits))

    def test_callback_marks_only_own_order(self):
        a, _ = self.register(17)
        b, _ = self.register(18)
        self.publish(order(9, "z"))
        self.service.judge_user(self.db.user(a["id"]), judge_factory=FakeJudge({"z": 90}))
        cb = {"id": "c1", "data": "a:r:kwork:9", "message": {"chat": {"id": 18}, "message_id": 1}}
        self.handler.handle({"update_id": 2, "callback_query": cb})
        self.assertIsNone(self.db.match(b["id"], "kwork:9"))
        cb["message"]["chat"]["id"] = 17
        self.handler.handle({"update_id": 3, "callback_query": cb})
        self.assertEqual(self.db.match(a["id"], "kwork:9")["action"], "responded")


class TestAPI(Base):
    def setUp(self):
        super().setUp()
        self.httpd = api.start(self.db, "127.0.0.1", 0)
        self.base = f"http://127.0.0.1:{self.httpd.server_address[1]}"

    def tearDown(self):
        self.httpd.shutdown()
        super().tearDown()

    def call(self, path, key=None, body=None):
        req = urllib.request.Request(self.base + path, data=json.dumps(body).encode() if body is not None else None,
                                     headers={"Authorization": f"Bearer {key}"} if key else {},
                                     method="POST" if body is not None else "GET")
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def test_health_and_auth(self):
        self.assertEqual(self.call("/api/health")[0], 200)
        self.assertEqual(self.call("/api/orders")[0], 401)
        self.assertEqual(self.call("/api/orders", key="kt_wrong")[0], 401)

    def test_orders_are_per_user_and_match_app_shape(self):
        a, ka = self.register(20)
        b, kb = self.register(21)
        self.publish(order(30, "w"))
        self.service.judge_user(self.db.user(a["id"]), judge_factory=FakeJudge({"w": 88}))
        status, data = self.call("/api/orders?stage=sent&action=all", ka)
        self.assertEqual(status, 200)
        o = data["orders"][0]
        self.assertEqual(o["id"], "kwork:30")
        self.assertEqual(o["score"], 88)
        for field in ("title", "price", "link", "draft", "urgency", "client_trust", "posted_at", "competition"):
            self.assertIn(field, o)
        self.assertEqual(self.call("/api/orders?stage=sent&action=all", kb)[1]["orders"], [])
        self.assertEqual(self.call("/api/orders/kwork:30", kb)[0], 404)
        self.assertEqual(self.call("/api/orders/kwork:30/action", kb, {"action": "dismissed"})[0], 404)
        self.assertEqual(self.call("/api/orders/kwork:30/action", ka, {"action": "responded"})[0], 200)
        stats = self.call("/api/stats?window=today", ka)[1]
        self.assertEqual((stats["sent"], stats["responded"]), (1, 1))

    def test_me(self):
        _, key = self.register(22)
        self.assertEqual(self.call("/api/me", key)[1]["threshold"], 60)
        self.assertEqual(self.call("/api/me", key, {"threshold": 70})[1]["threshold"], 70)
        self.assertEqual(self.call("/api/me", key, {"threshold": "70"})[0], 400)
        self.assertEqual(self.call("/api/me", key, {"profile": "коротко"})[0], 400)


class TestFeedExport(unittest.TestCase):
    """Экспорт из парсера и чтение здесь — две половины одного контракта."""

    def test_roundtrip(self):
        import importlib.util
        path = Path(__file__).resolve().parents[1] / "collector" / "feed_export.py"
        spec = importlib.util.spec_from_file_location("feed_export", path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        with tempfile.TemporaryDirectory() as tmp:
            export = mod.FeedExport(Path(tmp) / "feed.db")
            self.assertEqual(export.publish("kwork", [order(1), order(2)]), 2)
            self.assertEqual(export.publish("kwork", [order(2), order(3)]), 1)
            rows = FeedReader(Path(tmp) / "feed.db").read_after(0)
            self.assertEqual([r[1] for r in rows], ["kwork:1", "kwork:2", "kwork:3"])
            self.assertEqual(FeedReader(Path(tmp) / "feed.db").read_after(rows[1][0])[0][1], "kwork:3")

    def test_missing_feed_is_not_an_error(self):
        self.assertEqual(FeedReader("/nonexistent/feed.db").read_after(0), [])


if __name__ == "__main__":
    unittest.main()
