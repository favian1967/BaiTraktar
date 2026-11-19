"""Цикл: забрать новое из ленты → оценить под каждого пользователя → прислать подходящее.

Деньги на модель общие (ключ владельца), поэтому всё упирается в лимиты:
суточные на человека и суточные на весь сервис. Кончился лимит — заказы
помечаются skipped, человек один раз в день получает об этом сообщение, и
ничего не копится: завтра оцениваются уже новые заказы, а не вчерашний хвост.
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor

from fp.insights import draft_skip_reason
from fp.notifier import format_order

from .judge import UserJudge

log = logging.getLogger(__name__)

DRAFT_PENDING = "✍️ <i>Пишу черновик отклика…</i>"

DEFAULT_LIMITS = {
    "max_users": 50,
    "user_judge_calls": 40,      # запросов оценки в сутки на человека (до batch_size заказов в каждом)
    "user_drafts": 10,           # черновиков в сутки на человека
    "total_judge_calls": 300,    # потолок на весь сервис
    "total_drafts": 60,
    "orders_per_cycle": 16,      # сколько свежих заказов судить за один заход на человека
}


def order_keyboard(order: dict, key: str, action: str = None) -> list:
    rows = [[{"text": "🔗 Открыть заказ", "url": order.get("link") or "https://kwork.ru/projects"}]]
    if action == "responded":
        rows.append([{"text": "✅ Отмечено: откликнулся", "callback_data": "noop"}])
    elif action == "dismissed":
        rows.append([{"text": "🚫 Отмечено: не то", "callback_data": "noop"}])
    else:
        rows.append([{"text": "✅ Откликнулся", "callback_data": f"a:r:{key}"},
                     {"text": "🚫 Не то", "callback_data": f"a:d:{key}"}])
    return rows


class Service:
    def __init__(self, db, feed, bot, config: dict):
        self.db = db
        self.feed = feed
        self.bot = bot
        self.llm_settings = config.get("llm", {})
        self.limits = {**DEFAULT_LIMITS, **(config.get("limits") or {})}
        self.cycle_seconds = int(config.get("cycle_seconds", 60))
        self.drafts = ThreadPoolExecutor(max_workers=2, thread_name_prefix="draft")
        self._pruned_at = 0.0

    # --- лента ---------------------------------------------------------------

    def pull_feed(self) -> int:
        cursor = int(self.db.get_meta("feed_seq", 0))
        rows = self.feed.read_after(cursor)
        if not rows:
            return 0
        added = self.db.add_orders(rows)
        self.db.set_meta("feed_seq", rows[-1][0])
        return added

    # --- лимиты --------------------------------------------------------------

    def judge_calls_left(self, user_id: int) -> int:
        mine = self.limits["user_judge_calls"] - self.db.usage(user_id)["judge_calls"]
        total = self.limits["total_judge_calls"] - self.db.total_judge_calls_today()
        return max(0, min(mine, total))

    def drafts_left(self, user_id: int) -> int:
        mine = self.limits["user_drafts"] - self.db.usage(user_id)["drafts"]
        total = self.limits["total_drafts"] - self.db.total_drafts_today()
        return max(0, min(mine, total))

    def _notify_limit_once(self, user: dict):
        flag = f"limit_note:{user['id']}:{time.strftime('%Y-%m-%d')}"
        if self.db.get_meta(flag):
            return
        self.db.set_meta(flag, 1)
        self.bot.send(user["chat_id"],
                      "На сегодня лимит оценки заказов исчерпан — сервис общий, и модель "
                      "платная. Завтра оценка продолжится с новых заказов.")

    # --- оценка --------------------------------------------------------------

    def judge_user(self, user: dict, judge_factory=UserJudge) -> dict:
        stats = {"orders": 0, "sent": 0, "skipped": 0}
        batch = self.db.orders_after(user["last_seq"], self.limits["orders_per_cycle"])
        if not batch:
            return stats
        stats["orders"] = len(batch)
        newest_seq = batch[-1][0]

        judge = judge_factory(self.llm_settings, user["profile"])
        if not judge.configured:
            log.error("LLM не настроен — оценка выключена")
            return stats

        calls_left = self.judge_calls_left(user["id"])
        fits = calls_left * judge.batch_size
        if fits <= 0:
            for _, key, _ in batch:
                self.db.save_match(user["id"], key, "skipped")
            stats["skipped"] = len(batch)
            self.db.update_user(user["id"], last_seq=newest_seq)
            self._notify_limit_once(user)
            return stats
        # Не влезает всё — судим самые свежие: на Kwork важнее успеть к новому заказу.
        skipped = batch[:-fits] if len(batch) > fits else []
        batch = batch[-fits:]
        for _, key, _ in skipped:
            self.db.save_match(user["id"], key, "skipped")
        stats["skipped"] = len(skipped)

        orders = [o for _, _, o in batch]
        try:
            verdicts = judge.judge_many(orders)
        except Exception as e:
            # last_seq не двигаем — эти заказы переспросим на следующем цикле.
            # Неудачный запрос тоже стоит денег, поэтому он списывается из лимита.
            log.warning("оценка для user %s не удалась: %s", user["id"], e)
            self.db.spend(user["id"], judge_calls=max(1, judge.calls_made))
            return stats
        self.db.spend(user["id"], judge_calls=judge.calls_made)

        for (_, key, order), verdict in zip(batch, verdicts):
            if verdict["suitable"] and verdict["score"] >= user["threshold"]:
                if self._deliver(user, key, order, verdict, judge):
                    stats["sent"] += 1
            else:
                self.db.save_match(user["id"], key, "judged", verdict)
        self.db.update_user(user["id"], last_seq=newest_seq)
        return stats

    def _deliver(self, user: dict, key: str, order: dict, verdict: dict, judge) -> bool:
        will_draft = draft_skip_reason(order) is None and self.drafts_left(user["id"]) > 0
        text = format_order(order, verdict, status=DRAFT_PENDING if will_draft else "")
        message_id = self.bot.send(user["chat_id"], text, order_keyboard(order, key))
        if not message_id:
            # Бот заблокирован или Telegram лёг — заказ всё равно виден в приложении.
            self.db.save_match(user["id"], key, "sent", verdict, draft_state="skipped")
            return False
        self.db.save_match(user["id"], key, "sent", verdict, message_id=message_id,
                           draft_state="pending" if will_draft else "skipped")
        if will_draft:
            # Списываем сразу, а не по готовности: иначе десяток параллельных
            # черновиков проскочил бы лимит, пока первый ещё пишется.
            self.db.spend(user["id"], drafts=1)
            self.drafts.submit(self._write_draft, user, key, order, verdict, message_id, judge)
        return True

    def _write_draft(self, user, key, order, verdict, message_id, judge):
        try:
            draft = judge.draft(order)
        except Exception as e:
            log.warning("черновик для user %s упал: %s", user["id"], e)
            draft = ""
        self.db.save_draft(user["id"], key, draft, "done" if draft else "failed")
        match = self.db.match(user["id"], key) or {}
        status = "" if draft else "<i>Черновик не получился — напишите отклик сами.</i>"
        self.bot.edit(user["chat_id"], message_id,
                      format_order(order, verdict, draft=draft, status=status),
                      order_keyboard(order, key, match.get("action")))

    # --- цикл ----------------------------------------------------------------

    def run_once(self) -> dict:
        added = self.pull_feed()
        totals = {"feed_added": added, "users": 0, "sent": 0}
        for user in self.db.active_users():
            try:
                s = self.judge_user(user)
            except Exception:
                log.exception("цикл для user %s упал", user["id"])
                continue
            totals["users"] += 1
            totals["sent"] += s["sent"]
        if time.time() - self._pruned_at > 86400:
            self.db.prune()
            self._pruned_at = time.time()
        return totals

    def loop(self):
        while True:
            started = time.time()
            try:
                totals = self.run_once()
                if totals["feed_added"] or totals["sent"]:
                    log.info("цикл: из ленты %s, пользователей %s, отправлено %s",
                             totals["feed_added"], totals["users"], totals["sent"])
            except Exception:
                log.exception("цикл упал")
            time.sleep(max(5, self.cycle_seconds - (time.time() - started)))
