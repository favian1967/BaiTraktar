package com.example.baitraktar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.baitraktar.data.OrdersRepository
import com.example.baitraktar.data.OrdersResult
import com.example.baitraktar.data.SettingsStore
import com.example.baitraktar.data.StatsResult
import com.example.baitraktar.model.Order
import com.example.baitraktar.model.OrderStatus
import com.example.baitraktar.model.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersUiState(
    val orders: List<Order> = emptyList(),
    val stats: Stats = Stats(0, 0, 0),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Сервер не настроен (нет адреса/токена в Настройках) — не то же самое, что ошибка сети. */
    val configured: Boolean = false,
    /** Когда последний раз удалось забрать заказы, System.currentTimeMillis(); null — ещё ни разу. */
    val lastUpdated: Long? = null,
    /** Заказ, скрытый свайпом: на экране его уже нет, на сервер уйдёт через [HIDE_UNDO_MS]. */
    val pendingHide: String? = null,
)

/** Сколько живёт «Отменить» после свайпа, прежде чем скрытие уйдёт на сервер. */
const val HIDE_UNDO_MS = 4_000L

/** Старше этого данные обновляются сами, когда приложение возвращается на экран. */
private const val STALE_MS = 5 * 60_000L

/**
 * Единственный источник правды об заказах на экране. Данные реальные — из
 * app/api.py на VPS; ничего здесь не выдумывается, при ошибке экран покажет
 * именно ошибку, а не подставные заказы.
 */
class OrdersViewModel(
    private val repository: OrdersRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state

    init {
        refresh()
    }

    private var pendingHideJob: Job? = null
    private var pendingHidePrevious: OrderStatus? = null

    fun order(id: String): Order? = _state.value.orders.find { it.id == id }

    fun refresh() {
        val configured = settings.settings.value.isConfigured
        _state.update { it.copy(configured = configured) }
        if (!configured) {
            _state.update { it.copy(isLoading = false, orders = emptyList(), error = null) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchOrders()) {
                is OrdersResult.Success -> _state.update { st ->
                    // Сервер ещё не знает о скрытии, которое ждёт «Отменить», — не возвращаем заказ в очередь.
                    val orders = result.orders.map {
                        if (it.id == st.pendingHide) it.copy(status = OrderStatus.HIDDEN) else it
                    }
                    st.copy(orders = orders, isLoading = false, error = null, lastUpdated = System.currentTimeMillis())
                }
                is OrdersResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
            when (val stats = repository.fetchStats()) {
                is StatsResult.Success -> _state.update { it.copy(stats = stats.stats) }
                is StatsResult.Failure -> Unit // сводка — бонус на главном экране, не повод показывать вторую ошибку
            }
        }
    }

    /** Вызывается при возврате на экран: обновляет, только если данные заметно устарели. */
    fun refreshIfStale() {
        val st = _state.value
        if (st.isLoading) return
        val updated = st.lastUpdated
        if (updated == null || System.currentTimeMillis() - updated > STALE_MS) refresh()
    }

    /**
     * Скрыть с возможностью отмены: заказ пропадает сразу, а на сервер "dismissed" уходит
     * только через [HIDE_UNDO_MS] — вернуть скрытый заказ API не умеет.
     */
    fun hideWithUndo(id: String) {
        commitPendingHide()
        val previous = _state.value.orders.find { it.id == id }?.status ?: return
        pendingHidePrevious = previous
        setStatus(id, OrderStatus.HIDDEN)
        _state.update { it.copy(pendingHide = id) }
        pendingHideJob = viewModelScope.launch {
            delay(HIDE_UNDO_MS)
            commitPendingHide()
        }
    }

    fun undoHide() {
        val id = _state.value.pendingHide ?: return
        pendingHideJob?.cancel()
        pendingHideJob = null
        pendingHidePrevious?.let { setStatus(id, it) }
        pendingHidePrevious = null
        _state.update { it.copy(pendingHide = null) }
    }

    /** Отправляет ожидающее скрытие сейчас же — перед новым свайпом или когда ViewModel уходит. */
    private fun commitPendingHide(scope: CoroutineScope = viewModelScope) {
        val id = _state.value.pendingHide ?: return
        pendingHideJob?.cancel()
        pendingHideJob = null
        pendingHidePrevious = null
        _state.update { it.copy(pendingHide = null) }
        scope.launch { repository.sendAction(id, "dismissed") }
    }

    private fun setStatus(id: String, status: OrderStatus) {
        _state.update { st ->
            st.copy(orders = st.orders.map { if (it.id == id) it.copy(status = status) else it })
        }
    }

    override fun onCleared() {
        // viewModelScope уже отменён — отправляем в своём скоупе, иначе свайп потеряется.
        commitPendingHide(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    /** action: "responded" | "dismissed". Обновляет список сразу — сервер только подтверждает. */
    fun markAction(id: String, action: String) {
        if (id == _state.value.pendingHide) {
            // Явное действие важнее свайпа — отложенный "dismissed" его бы перезаписал.
            pendingHideJob?.cancel()
            pendingHideJob = null
            pendingHidePrevious = null
            _state.update { it.copy(pendingHide = null) }
        }
        val newStatus = if (action == "responded") OrderStatus.SENT else OrderStatus.HIDDEN
        _state.update { st ->
            st.copy(orders = st.orders.map { if (it.id == id) it.copy(status = newStatus) else it })
        }
        viewModelScope.launch { repository.sendAction(id, action) }
    }

    companion object {
        fun factory(repository: OrdersRepository, settings: SettingsStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OrdersViewModel(repository, settings) as T
        }
    }
}
