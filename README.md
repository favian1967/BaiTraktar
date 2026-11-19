# Kwork Tracker

Android-приложение, которое показывает подходящие заказы с Kwork. Каждый новый
заказ оценивает ИИ под профиль пользователя, к подходящим пишет черновик отклика.
Вход по ключу из Telegram-бота [@Parserkworktrbot](https://t.me/Parserkworktrbot),
ключ хранится на телефоне зашифрованным аппаратным ключом под отпечатком.

## Запуск из Android Studio

1. Android Studio → **File → New → Project from Version Control**, вставить адрес
   этого репозитория → **Clone**.
2. Дождаться синхронизации Gradle. JDK и зависимости скачаются сами, первый раз —
   несколько минут.
3. Выбрать эмулятор или телефон и нажать **Run ▶**.

Нужна свежая Android Studio: проект на AGP 9.4, compileSdk 37. Подписывать
ничего не нужно — Run собирает debug-версию.

## Как войти

1. В Telegram написать боту [@Parserkworktrbot](https://t.me/Parserkworktrbot) `/start`.
2. Скопировать ключ, который пришлёт бот, и отправить ему пару предложений о том,
   какие заказы вы ищете.
3. В приложении: **Настройки → Ключ из бота → Проверить и сохранить**.

Сервер по умолчанию — `https://kwork.favian1967.site`. Подключиться к своему
экземпляру парсера можно через **Настройки → Свой сервер**.

## Как устроено

```
Kwork ──► сборщик ленты ──► ИИ-оценка под профиль каждого пользователя
                                   │
                         SQLite (пользователи, заказы, оценки)
                            │                      │
                      Telegram-бот          REST API (HTTPS) ──► это приложение
```

## Что где лежит

| Папка | Что это |
|---|---|
| `app/` | Android-приложение (Kotlin, Jetpack Compose) |
| `app/.../data/` | Сеть (Retrofit), настройки, `TokenVault` — шифрование ключа в Android Keystore |
| `app/.../ui/screens/` | Экраны |
| `server/` | Сервер: Telegram-бот, REST API, ИИ-оценка, база данных — подробно в [server/README.md](server/README.md) |
| `server/collector/` | Сбор ленты заказов с Kwork |

## Релизная сборка

`./gradlew assembleRelease` подписывает APK, если в корне лежат `release.jks` и
`keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
Без них релиз собирается неподписанным. Оба файла в git не попадают.
