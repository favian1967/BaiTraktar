package com.example.baitraktar.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.baitraktar.model.Order
import com.example.baitraktar.model.OrderStatus
import com.example.baitraktar.ui.OrdersUiState
import com.example.baitraktar.ui.components.EmptyState
import com.example.baitraktar.ui.components.Logo
import com.example.baitraktar.ui.components.OrderRow
import com.example.baitraktar.ui.components.OrderRowSkeleton
import com.example.baitraktar.ui.components.SecondaryButton
import com.example.baitraktar.ui.components.SectionRule
import com.example.baitraktar.ui.components.TextAction
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Space
import com.example.baitraktar.ui.theme.Surface2
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary
import java.util.Date

/** How many matched orders the radar lists before sending the user to the full list. */
private const val QUEUE_SIZE = 5

private enum class QueueSort(val label: String) { FRESH("Свежие"), BEST("Лучшие") }

/**
 * The radar is a queue: the orders that passed the filter and still wait for a reply lead
 * the screen. The day's figures sit above it as one row of numbers. A row swiped away is
 * hidden with a few seconds to undo. [state] comes straight from the parser on the VPS
 * (see OrdersViewModel) — nothing here is invented locally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: OrdersUiState,
    onRefresh: () -> Unit,
    onResume: () -> Unit,
    onOpenOrder: (Order) -> Unit,
    onHideOrder: (Order) -> Unit,
    onUndoHide: () -> Unit,
    onSeeAll: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val stats = state.stats
    var sort by rememberSaveable { mutableStateOf(QueueSort.FRESH) }
    val waiting = state.orders.filter { it.status == OrderStatus.MATCH }.let { list ->
        when (sort) {
            // Orders without a posting time go last rather than jumping to the top.
            QueueSort.FRESH -> list.sortedByDescending { it.postedAt ?: 0.0 }
            QueueSort.BEST -> list.sortedByDescending { it.match }
        }
    }

    LifecycleResumeEffect(Unit) {
        onResume()
        onPauseOrDispose { }
    }

    // The snackbar lives exactly as long as the hide is pending in the ViewModel: when it is
    // sent (or undone) pendingHide clears, this effect restarts and the snackbar goes with it.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.pendingHide) {
        if (state.pendingHide == null) return@LaunchedEffect
        val result = snackbar.showSnackbar("Заказ скрыт", actionLabel = "Отменить", duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) onUndoHide()
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.configured,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Logo(size = 32.dp)
                Spacer(Modifier.weight(1f))
                Text(
                    statusText(state),
                    style = LabelStyle,
                    color = if (state.error != null) TextSecondary else TextMuted,
                )
            }

            Spacer(Modifier.height(Space.block))
            Text("Сегодня", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            Spacer(Modifier.height(Space.block))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure(stats.matched, "прошли фильтр", Modifier.weight(1f), accent = true)
                Figure(stats.checked, "проверено", Modifier.weight(1f))
                Figure(stats.checked - stats.matched, "отсеяно AI", Modifier.weight(1f))
                Figure(stats.sent, "отправлено", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.block))
            HorizontalDivider(color = OutlineSoft)

            Spacer(Modifier.height(Space.block))
            SectionRule("Ждут отклика") {
                TextAction("Все заказы", onClick = onSeeAll)
            }
            if (waiting.size > 1) {
                SortSwitch(sort, onSelect = { sort = it }, modifier = Modifier.padding(top = Space.gap))
            }
            when {
                !state.configured -> {
                    EmptyState(
                        "Сервер не подключён",
                        detail = "Получите ключ у бота @Parserkworktrbot и вставьте его в Настройках — здесь появятся заказы под ваш профиль.",
                    )
                    SecondaryButton(text = "Открыть настройки", onClick = onOpenSettings)
                }
                state.isLoading && waiting.isEmpty() -> repeat(3) { OrderRowSkeleton() }
                state.error != null && waiting.isEmpty() -> {
                    EmptyState("Нет связи с сервером", detail = state.error)
                    SecondaryButton(text = "Повторить", onClick = onRefresh)
                }
                waiting.isEmpty() -> EmptyState(
                    "Новых подходящих заказов нет",
                    detail = "Заказы, прошедшие фильтр, появятся здесь. Потяните вниз, чтобы обновить.",
                )
                else -> {
                    val shown = waiting.take(QUEUE_SIZE)
                    shown.forEachIndexed { i, order ->
                        // Keyed by id: a swiped row must not hand its offset to the row that slides up.
                        key(order.id) {
                            SwipeToHide(onHide = { onHideOrder(order) }) {
                                OrderRow(order, onClick = { onOpenOrder(order) }, showMatch = true)
                            }
                        }
                        if (i < shown.lastIndex) HorizontalDivider(color = OutlineSoft)
                    }
                    if (waiting.size > shown.size) {
                        TextAction("Ещё ${waiting.size - shown.size} в списке заказов", onClick = onSeeAll)
                    }
                }
            }
            Spacer(Modifier.height(Space.block))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(Space.screen),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Surface2,
                contentColor = TextPrimary,
                actionColor = Lime,
            )
        }
    }
}

/** `Обновляю` / `Обновлено 14:32` / `Нет связи · 14:32` — how fresh the numbers below are. */
@Composable
private fun statusText(state: OrdersUiState): String {
    // Not configured: the empty state below already says so, with the action.
    if (!state.configured) return ""
    if (state.isLoading) return "Обновляю"
    val context = LocalContext.current
    val time = state.lastUpdated?.let { DateFormat.getTimeFormat(context).format(Date(it)) }
    return when {
        state.error != null -> if (time != null) "Нет связи · $time" else "Нет связи"
        time != null -> "Обновлено $time"
        else -> ""
    }
}

/** Two words, the current one in full colour. Small enough to sit under the section label. */
@Composable
private fun SortSwitch(selected: QueueSort, onSelect: (QueueSort) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        QueueSort.entries.forEach { s ->
            Text(
                s.label,
                style = LabelStyle,
                color = if (s == selected) TextPrimary else TextMuted,
                modifier = Modifier.clickable { onSelect(s) }.padding(vertical = 6.dp),
            )
        }
    }
}

/** End-to-start swipe hides the row; behind it, a plain `Скрыть` on the surface tone. */
@Composable
private fun SwipeToHide(onHide: () -> Unit, content: @Composable () -> Unit) {
    val swipe = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onHide() },
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(Surface2).padding(horizontal = Space.card),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Скрыть", style = LabelStyle, color = TextSecondary)
            }
        },
    ) {
        // Opaque, so the label behind shows only where the row has moved away.
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** A figure over its label: one cell of the day's row. */
@Composable
private fun Figure(value: Int, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value.toString(), style = MonoType.priceLarge, color = if (accent) Lime else TextPrimary, maxLines = 1)
        Text(label, style = LabelStyle, color = TextSecondary, maxLines = 2)
    }
}
