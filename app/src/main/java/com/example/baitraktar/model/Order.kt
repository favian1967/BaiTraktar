package com.example.baitraktar.model

enum class OrderStatus(val label: String) {
    SENT("Отправлено"),
    MATCH("Подходит"),
    HIDDEN("Скрыто"),
    OFF_PROFILE("Не по профилю"),
}

/**
 * `id` — "kwork:12345" / "profi:67890", как в базе парсера (app/state.py на
 * сервере) — используется как ключ и для API-запросов по конкретному заказу.
 * Все текстовые поля приходят уже отформатированными из
 * [com.example.baitraktar.data.toDomainOrder]: экраны ничего не считают сами.
 */
data class Order(
    val id: String,
    val title: String,
    val price: String,
    val category: String,
    val location: String,
    val offers: String,
    val timeAgo: String,
    /** Время публикации, epoch-секунды; null — биржа его не отдала. Для сортировки, не для показа. */
    val postedAt: Double?,
    val description: String,
    val status: OrderStatus,
    val link: String,
    /** "Kwork" / "Profi.ru" — куда ведёт [link]; подставляется в подписи кнопок. */
    val sourceLabel: String,
    /** Оценка ИИ по профилю, 0–100. */
    val match: Int,
    val aiReply: String,
    /** Черновик ещё пишется на сервере (локальной GRAVITAS или облаком) — aiReply пуст не из-за ошибки. */
    val aiReplyPending: Boolean,
)

data class Stats(val checked: Int, val matched: Int, val sent: Int)
