"""Команды бота @Parserkworktrbot: регистрация по /start, профиль, порог, ключ приложения."""

import html
import logging
import time

from .service import order_keyboard

log = logging.getLogger(__name__)

PROFILE_MIN = 40
PROFILE_MAX = 3000

COMMANDS = [
    ("start", "Подключиться и получить ключ"),
    ("profile", "Показать или сменить профиль"),
    ("threshold", "Порог совпадения, например /threshold 70"),
    ("pause", "Не присылать заказы"),
    ("resume", "Снова присылать"),
    ("status", "Профиль, порог, лимиты на сегодня"),
    ("newkey", "Выпустить новый ключ для приложения"),
    ("help", "Что умеет бот"),
]

ASK_PROFILE = (
    "Опишите одним сообщением, что вы делаете и какие заказы ищете. "
    "Чем конкретнее, тем точнее оценка. Например:\n\n"
    "<i>Делаю сайты на Tilda и лендинги, логотипы и баннеры. Беру заказы от 3000 ₽. "
    "Не берусь за 1С, мобильные приложения и тексты.</i>"
)

HELP = (
    "Бот присылает заказы с Kwork, которые подходят под ваш профиль. Оценивает их ИИ, "
    "к подходящим пишет черновик отклика.\n\n"
    "Те же заказы видны в приложении Kwork Tracker: адрес сервера там уже указан, "
    "нужен только ваш ключ.\n\n"
    + "\n".join(f"/{c} — {d}" for c, d in COMMANDS)
)


def key_message(key: str) -> str:
    return ("Ключ для приложения Kwork Tracker:\n\n"
            f"<code>{html.escape(key)}</code>\n\n"
            "Нажмите, чтобы скопировать, и вставьте в приложении. Ключ показывается один раз; "
            "потеряете — /newkey выпустит новый, старый перестанет работать.")


class BotHandler:
    def __init__(self, db, bot, service):
        self.db = db
        self.bot = bot
        self.service = service

    def poll_forever(self):
        self.bot.set_commands(COMMANDS)
        offset = int(self.db.get_meta("tg_offset", 0)) or None
        while True:
            for update in self.bot.get_updates(offset=offset, timeout=30):
                offset = update["update_id"] + 1
                try:
                    self.handle(update)
                except Exception:
                    log.exception("апдейт %s не обработан", update.get("update_id"))
                # Смещение сохраняется после каждого апдейта: перезапуск не должен
                # повторить /newkey или отметку заказа.
                self.db.set_meta("tg_offset", offset)

    def handle(self, update: dict):
        if "callback_query" in update:
            self._callback(update["callback_query"])
            return
        msg = update.get("message") or {}
        chat = msg.get("chat") or {}
        if chat.get("type") != "private":
            return  # в группах бот не работает: ключ нельзя показывать при всех
        text = (msg.get("text") or "").strip()
        chat_id = chat.get("id")
        username = (msg.get("from") or {}).get("username") or ""
        if not chat_id or not text:
            return

        user = self.db.user_by_chat(chat_id)
        if text.startswith("/"):
            command, _, arg = text[1:].partition(" ")
            command = command.split("@")[0].lower()
            self._command(chat_id, username, user, command, arg.strip())
        elif user and user["awaiting"] == "profile":
            self._save_profile(chat_id, user, text)
        elif user:
            self.bot.send(chat_id, "Команды — /help. Сменить профиль — /profile.")
        else:
            self.bot.send(chat_id, "Чтобы подключиться, нажмите /start.")

    # --- команды -------------------------------------------------------------

    def _command(self, chat_id, username, user, command, arg):
        if command == "start":
            self._start(chat_id, username, user)
            return
        if not user:
            self.bot.send(chat_id, "Сначала /start.")
            return
        if command == "help":
            self.bot.send(chat_id, HELP)
        elif command == "profile":
            self.db.update_user(user["id"], awaiting="profile")
            current = (f"Сейчас:\n<i>{html.escape(user['profile'])}</i>\n\n"
                       if user["profile"] else "")
            self.bot.send(chat_id, current + "Пришлите новый текст профиля или /cancel.")
        elif command == "cancel":
            self.db.update_user(user["id"], awaiting="")
            self.bot.send(chat_id, "Хорошо, профиль не меняю.")
        elif command == "threshold":
            self._threshold(chat_id, user, arg)
        elif command == "pause":
            self.db.update_user(user["id"], paused=1)
            self.bot.send(chat_id, "Пауза. Заказы не оцениваются и не приходят. /resume — продолжить.")
        elif command == "resume":
            # С паузы — только новые заказы: оценивать накопившееся за дни незачем.
            self.db.update_user(user["id"], paused=0, last_seq=self.db.latest_seq())
            self.bot.send(chat_id, "Продолжаю. Новые подходящие заказы придут сюда.")
        elif command == "status":
            self._status(chat_id, user)
        elif command == "newkey":
            key = self.db.rotate_key(user["id"])
            self.bot.send(chat_id, "Старый ключ больше не работает.\n\n" + key_message(key))
        else:
            self.bot.send(chat_id, "Такой команды нет. /help — список.")

    def _start(self, chat_id, username, user):
        if user:
            self.bot.send(chat_id, "Вы уже подключены. /status — что настроено, "
                                   "/newkey — новый ключ для приложения.")
            return
        if self.db.count_users() >= self.service.limits["max_users"]:
            self.bot.send(chat_id, "Сейчас мест нет: сервис учебный, и число пользователей ограничено.")
            return
        user, key = self.db.create_user(chat_id, username)
        log.info("новый пользователь %s (@%s)", user["id"], username)
        self.bot.send(chat_id, "Привет! Я присылаю подходящие заказы с Kwork.\n\n" + key_message(key))
        self.bot.send(chat_id, ASK_PROFILE)

    def _save_profile(self, chat_id, user, text):
        if len(text) < PROFILE_MIN:
            self.bot.send(chat_id, "Слишком коротко — по паре слов модель не поймёт, что вам подходит. "
                                   "Опишите подробнее.")
            return
        if len(text) > PROFILE_MAX:
            self.bot.send(chat_id, f"Слишком длинно: до {PROFILE_MAX} символов.")
            return
        first = not user["profile"]
        self.db.set_profile(user["id"], text)
        self.bot.send(chat_id, (
            "Профиль сохранён. " +
            (f"Порог совпадения — {user['threshold']}%, поменять: /threshold. "
             "Новые подходящие заказы придут сюда и в приложение." if first
             else "Новые заказы будут оцениваться уже по нему.")))

    def _threshold(self, chat_id, user, arg):
        try:
            value = int(arg)
        except ValueError:
            self.bot.send(chat_id, f"Сейчас {user['threshold']}%. Новый — числом: /threshold 70")
            return
        if not 0 <= value <= 100:
            self.bot.send(chat_id, "Порог — от 0 до 100.")
            return
        self.db.update_user(user["id"], threshold=value)
        self.bot.send(chat_id, f"Порог {value}%: приходят заказы, которые модель оценила не ниже.")

    def _status(self, chat_id, user):
        usage = self.db.usage(user["id"])
        lim = self.service.limits
        digest = self.db.digest(user["id"], time.time() - 86400)
        profile = user["profile"] or "не задан — /profile"
        if len(profile) > 300:
            profile = profile[:300] + "…"
        self.bot.send(chat_id, (
            f"<b>Профиль:</b> <i>{html.escape(profile)}</i>\n"
            f"<b>Порог:</b> {user['threshold']}%" + (" · <b>на паузе</b>" if user["paused"] else "") + "\n\n"
            f"За сутки: оценено {digest['seen']}, прислано {digest['sent']}, "
            f"откликов {digest['responded']}\n"
            f"Лимит на сегодня: оценка {usage['judge_calls']}/{lim['user_judge_calls']}, "
            f"черновики {usage['drafts']}/{lim['user_drafts']}"))

    # --- кнопки под заказом --------------------------------------------------

    def _callback(self, cb: dict):
        data = cb.get("data") or ""
        chat_id = ((cb.get("message") or {}).get("chat") or {}).get("id")
        message_id = (cb.get("message") or {}).get("message_id")
        user = self.db.user_by_chat(chat_id) if chat_id else None
        if not data.startswith("a:") or not user:
            self.bot.answer_callback(cb["id"])
            return
        _, kind, key = data.split(":", 2)
        action = {"r": "responded", "d": "dismissed"}.get(kind)
        if not action or not self.db.mark_action(user["id"], key, action):
            self.bot.answer_callback(cb["id"], "Заказ не найден")
            return
        self.bot.answer_callback(cb["id"], "Отмечено")
        order = self.db.order(key) or {}
        if message_id:
            self.bot.edit_keyboard(chat_id, message_id, order_keyboard(order, key, action))
