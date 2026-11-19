package com.example.baitraktar.data

import com.example.baitraktar.data.remote.ActionRequestDto
import com.example.baitraktar.data.remote.ApiClientFactory
import com.example.baitraktar.data.remote.ApiService
import com.example.baitraktar.data.remote.MeDto
import com.example.baitraktar.data.remote.MeUpdateDto
import com.example.baitraktar.data.remote.OrderDto
import com.example.baitraktar.data.remote.StatsDto
import com.example.baitraktar.model.Order
import com.example.baitraktar.model.OrderStatus
import com.example.baitraktar.model.Stats
import com.example.baitraktar.model.pluralize
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.roundToLong
import retrofit2.HttpException

sealed interface OrdersResult {
    data class Success(val orders: List<Order>) : OrdersResult
    data class Failure(val message: String) : OrdersResult
}

sealed interface MeResult {
    data class Success(val me: MeDto) : MeResult
    /** Сервер не знает /api/me — это личный парсер, фильтры там в config.json. */
    data object Unsupported : MeResult
    data class Failure(val message: String) : MeResult
}

sealed interface StatsResult {
    data class Success(val stats: Stats) : StatsResult
    data class Failure(val message: String) : StatsResult
}

/**
 * Мост к парсеру на VPS (app/api.py). Никакой бизнес-логики здесь нет —
 * только HTTP и перевод DTO сервера в модель приложения; сама логика
 * (словарный фильтр, вердикт ИИ, черновик отклика) целиком живёт на сервере,
 * так что приложение никогда не "придумывает" заказы — только показывает то,
 * что реально пришло.
 */
class OrdersRepository(private val settings: SettingsStore) {

    private fun service() = ApiClientFactory.get(
        settings.settings.value.baseUrl,
        settings.settings.value.apiToken,
    )

    /** true — сервер отвечает и токен принят. Для экрана Настроек. */
    suspend fun checkConnection(baseUrl: String, apiToken: String): Boolean {
        val api = ApiClientFactory.get(baseUrl, apiToken) ?: return false
        return try {
            val health = api.health()
            if (!health.isSuccessful) return false
            // /api/health не требует токена — проверяем его отдельным запросом,
            // иначе "сервер отвечает" и "токен верный" неотличимы на этом экране.
            api.orders(limit = 1)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchOrders(): OrdersResult {
        val api = service() ?: return OrdersResult.Failure("Сервер не настроен — откройте Настройки")
        return try {
            val response = api.orders(stage = "sent", action = "all", limit = 100)
            OrdersResult.Success(response.orders.map(::toDomainOrder))
        } catch (e: Exception) {
            OrdersResult.Failure(describe(e))
        }
    }

    suspend fun fetchStats(): StatsResult {
        val api = service() ?: return StatsResult.Failure("Сервер не настроен")
        return try {
            StatsResult.Success(toDomainStats(api.stats(window = "today")))
        } catch (e: Exception) {
            StatsResult.Failure(describe(e))
        }
    }

    suspend fun fetchMe(): MeResult = callMe { it.me() }

    suspend fun updateMe(profile: String? = null, threshold: Int? = null): MeResult =
        callMe { it.updateMe(MeUpdateDto(profile = profile, threshold = threshold)) }

    private suspend fun callMe(block: suspend (ApiService) -> MeDto): MeResult {
        val api = service() ?: return MeResult.Failure("Сервер не настроен")
        return try {
            MeResult.Success(block(api))
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> MeResult.Unsupported
                400 -> MeResult.Failure("Сервер не принял: проверьте длину профиля (от 40 символов) и порог 0–100")
                else -> MeResult.Failure(describe(e))
            }
        } catch (e: Exception) {
            MeResult.Failure(describe(e))
        }
    }

    /** Оптимистичное действие: экран уже обновился, сервер только подтверждает. */
    suspend fun sendAction(id: String, action: String): Boolean {
        val api = service() ?: return false
        return try {
            api.sendAction(id, ActionRequestDto(action)).ok
        } catch (_: Exception) {
            false
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException, is ConnectException ->
            "Не удаётся подключиться к серверу — проверьте адрес в Настройках"
        is SocketTimeoutException -> "Сервер не отвечает"
        is HttpException -> when (e.code()) {
            401 -> "Ключ не подходит — проверьте Настройки или выпустите новый командой /newkey"
            else -> "Сервер вернул ошибку ${e.code()}"
        }
        else -> e.message ?: "Не удалось загрузить заказы"
    }
}

private fun toDomainStats(dto: StatsDto) = Stats(checked = dto.seen, matched = dto.sent, sent = dto.responded)

/** Заказ из API → то, что рисуют экраны. Всё форматирование — здесь, разом,
 * чтобы Compose-код ниже по стеку не считал плюрализацию и не гадал про null. */
internal fun toDomainOrder(dto: OrderDto): Order {
    val status = when (dto.action) {
        "responded" -> OrderStatus.SENT
        "dismissed" -> OrderStatus.HIDDEN
        else -> OrderStatus.MATCH
    }
    val offersText = dto.competition?.let { n ->
        "$n ${pluralize(n, "отклик", "отклика", "откликов")}"
    } ?: "неизвестно"
    val timeAgo = dto.posted_at?.let(::humanizeAge)
        ?: dto.urgency.text.ifBlank { "" }
    return Order(
        id = dto.id,
        title = dto.title,
        price = dto.price.ifBlank { "цена не указана" },
        category = dto.category.ifBlank { sourceLabel(dto.source) },
        location = dto.location,
        offers = offersText,
        timeAgo = timeAgo.ifBlank { "недавно" },
        postedAt = dto.posted_at,
        description = dto.description,
        status = status,
        link = dto.link,
        sourceLabel = sourceLabel(dto.source),
        match = dto.score ?: 0,
        aiReply = dto.draft,
        // У kwork_app пустой черновик бывает и окончательным (лимит, модель не справилась);
        // ждать стоит только "pending". Личный парсер поля не шлёт — там пустой значит «пишется».
        aiReplyPending = dto.draft.isBlank() && dto.draft_state in setOf("", "pending"),
    )
}

private fun sourceLabel(source: String) = when (source) {
    "kwork" -> "Kwork"
    "profi" -> "Profi.ru"
    "" -> "бирже"
    else -> source
}

private fun humanizeAge(postedAtEpochSeconds: Double): String {
    val minutes = ((System.currentTimeMillis() / 1000.0 - postedAtEpochSeconds) / 60).roundToLong()
        .coerceAtLeast(0)
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 60 * 24 -> "${minutes / 60} ч назад"
        else -> "${minutes / (60 * 24)} дн назад"
    }
}
