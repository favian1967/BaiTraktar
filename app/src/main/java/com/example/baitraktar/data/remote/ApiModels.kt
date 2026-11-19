package com.example.baitraktar.data.remote

import kotlinx.serialization.Serializable

/**
 * JSON-модели ровно того, что отдаёт app/api.py в freelance_parser (на VPS).
 * Поля названы как в Python (snake_case) — сервер знает только про Telegram-
 * бота и никогда не согласует их с Kotlin-конвенциями, а лишний маппинг тут
 * того не стоит.
 */
@Serializable
data class UrgencyDto(
    val level: String = "normal",
    val icon: String = "",
    val text: String = "",
)

@Serializable
data class ClientTrustDto(
    val level: String = "unknown",
    val icon: String = "",
    val text: String = "",
)

@Serializable
data class OrderDto(
    val id: String,
    val source: String = "",
    val title: String = "",
    val description: String = "",
    val price: String = "",
    val location: String = "",
    val link: String = "",
    val category: String = "",
    val client: String = "",
    val client_url: String = "",
    val client_orders: Int? = null,
    val client_hire_rate: Int? = null,
    val posted_at: Double? = null,
    val competition: Int? = null,
    val score: Int? = null,
    val reason: String = "",
    val stage: String = "",
    val action: String? = null,
    val seen_at: Double? = null,
    val sent_at: Double? = null,
    val draft: String = "",
    /** Только у kwork_app: "pending" | "done" | "failed" | "skipped". У личного парсера поля нет — "". */
    val draft_state: String = "",
    val draft_origin: String = "",
    val draft_model: String = "",
    val notes: List<String> = emptyList(),
    val urgency: UrgencyDto = UrgencyDto(),
    val client_trust: ClientTrustDto = ClientTrustDto(),
)

@Serializable
data class OrdersResponseDto(
    val orders: List<OrderDto> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class ActionRequestDto(val action: String)

/** /api/me — профиль и порог пользователя kwork_app. У личного парсера эндпоинта нет (404). */
@Serializable
data class MeDto(
    val profile: String = "",
    val threshold: Int = 60,
    val paused: Boolean = false,
    val username: String = "",
)

/** null-поля не отправляются (explicitNulls = false) — сервер меняет только то, что пришло. */
@Serializable
data class MeUpdateDto(val profile: String? = null, val threshold: Int? = null)

@Serializable
data class OkResponseDto(val ok: Boolean = false)

@Serializable
data class HealthDto(val ok: Boolean = false, val time: Double = 0.0)

@Serializable
data class StatsDto(
    val seen: Int = 0,
    val rejected: Int = 0,
    val judged_out: Int = 0,
    val sent: Int = 0,
    val responded: Int = 0,
    val dismissed: Int = 0,
)
