# Сервер Kwork Tracker

Telegram-бот [@Parserkworktrbot](https://t.me/Parserkworktrbot) и REST API, к
которому ходит Android-приложение. Работает в Docker на VPS за прокси Caddy:
`https://kwork.favian1967.site`.

## Путь заказа

```
Kwork ──► collector/  сбор ленты (внутренний API Kwork, антибот: случайные интервалы, отступ при 403/429)
              │        пишет сырые заказы в feed.db
              ▼
          kwork_app/service.py   раз в минуту: новые заказы × каждый пользователь
              │        ИИ оценивает пачкой по 8 под профиль человека (judge.py)
              │        подходящие → Telegram + черновик отклика в фоне
              ▼
          SQLite app.db (db.py) ──► bot.py   команды бота
                                └─► api.py   REST API для приложения
```

| Файл | Что делает |
|---|---|
| `collector/sources/kwork.py` | Клиент ленты Kwork |
| `collector/feed_export.py` | Складывает ленту в `feed.db` |
| `kwork_app/feed.py` | Читает `feed.db` (только чтение) |
| `kwork_app/judge.py` | Промпты оценки и черновика под профиль пользователя |
| `kwork_app/service.py` | Цикл оценки, суточные лимиты на ИИ |
| `kwork_app/bot.py` | `/start` с выдачей ключа, `/profile`, `/threshold`, кнопки под заказами |
| `kwork_app/api.py` | `/api/orders`, `/api/stats`, `/api/me` — то, что вызывает приложение |
| `kwork_app/db.py` | Схема базы и запросы |
| `fp/` | Вызов модели, разбор ответа, формат уведомления — общие с личным парсером автора |

## База данных (SQLite, `data/app.db`)

```
users 1 ──── N matches N ──── 1 orders
  │
  1 ──── N usage
```

- `users` — кто подключился: Telegram-чат, **хеш** ключа (сам ключ не хранится), профиль, порог
- `orders` — заказы из ленты
- `matches` — оценка заказа для конкретного пользователя: процент, причина, черновик, отметка «откликнулся / не то»
- `usage` — сколько запросов к ИИ сделано за день, для лимитов

## Запуск

```bash
cp .env.example .env          # TELEGRAM_BOT_TOKEN и LLM_API_KEY
docker compose up -d --build
```

Тесты (сеть и ключи не нужны):

```bash
python -m unittest discover -s tests -t .
```

Сборщик ленты в продакшене работает внутри личного парсера автора (он
приватный: в нём cookies аккаунта Kwork и ключи) — оттуда сюда скопированы
только модули из `collector/` и `fp/`, см. `vendor.sh`.
