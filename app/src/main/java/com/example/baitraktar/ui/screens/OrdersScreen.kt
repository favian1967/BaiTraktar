package com.example.baitraktar.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.semantics.Role
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.TextSecondary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.baitraktar.model.Order
import com.example.baitraktar.model.OrderStatus
import com.example.baitraktar.ui.components.EmptyState
import com.example.baitraktar.ui.components.KworkEasing
import com.example.baitraktar.ui.components.OrderRow
import com.example.baitraktar.ui.components.OrderRowSkeleton
import com.example.baitraktar.ui.components.SecondaryButton
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Space
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary

private enum class OrdersTab(val label: String, val emptyText: String) {
    ALL("Все", "Заказов пока нет"),
    MATCHING("Подходят", "Нет заказов, ждущих отклика"),
    SENT("Отправлены", "Вы ещё не откликались"),
}

private fun OrdersTab.filter(all: List<Order>) = when (this) {
    OrdersTab.ALL -> all
    OrdersTab.MATCHING -> all.filter { it.status == OrderStatus.MATCH }
    OrdersTab.SENT -> all.filter { it.status == OrderStatus.SENT }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    orders: List<Order>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    configured: Boolean,
    onOpenOrder: (Order) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var tab by remember { mutableStateOf(OrdersTab.ALL) }
    val filtered = tab.filter(orders)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(Space.block))
        Text(
            "Заказы",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = Space.screen),
        )
        Spacer(Modifier.height(Space.block))
        LineTabs(
            selected = tab,
            counts = OrdersTab.entries.map { it.filter(orders).size },
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = Space.screen),
        )
        PullToRefreshBox(
            isRefreshing = isLoading && orders.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                !configured -> EmptyStateWithAction(
                    text = "Сервер не подключён",
                    detail = "Получите ключ у бота @Parserkworktrbot и вставьте его в Настройках — здесь появятся заказы под ваш профиль.",
                    actionText = "Открыть настройки",
                    onAction = onOpenSettings,
                )
                isLoading && orders.isEmpty() -> Column(Modifier.padding(horizontal = Space.screen, vertical = 4.dp)) {
                    repeat(5) { OrderRowSkeleton() }
                }
                error != null && orders.isEmpty() -> EmptyStateWithAction(
                    text = "Не удалось загрузить заказы",
                    detail = error,
                    actionText = "Повторить",
                    onAction = onRetry,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Space.screen, vertical = 4.dp),
                ) {
                    // Refresh failed but there is a cached list — say so instead of silently showing stale data.
                    if (error != null) item(key = "error") { StaleBanner(error) }
                    if (filtered.isEmpty()) item(key = "empty") { EmptyState(tab.emptyText) }
                    itemsIndexed(filtered, key = { _, o -> o.id }) { i, order ->
                        OrderRow(order, onClick = { onOpenOrder(order) })
                        if (i < filtered.lastIndex) HorizontalDivider(color = OutlineSoft)
                    }
                }
            }
        }
    }
}

@Composable
private fun StaleBanner(error: String) {
    Text(
        "Не удалось обновить: $error. Потяните вниз, чтобы повторить.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
    HorizontalDivider(color = OutlineSoft)
}

@Composable
private fun EmptyStateWithAction(text: String, detail: String, actionText: String, onAction: () -> Unit) {
    // Scrollable so the action stays reachable in landscape, where the tabs eat most of the height.
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
        EmptyState(text, detail = detail)
        SecondaryButton(text = actionText, onClick = onAction)
    }
}

/**
 * Mono tab labels with counts, sitting on a hairline. The active one is underlined by
 * a lime segment that slides along the line — the same gesture as the bottom bar.
 */
@Composable
private fun LineTabs(
    selected: OrdersTab,
    counts: List<Int>,
    onSelect: (OrdersTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = OrdersTab.entries.size
    val pos by animateFloatAsState(selected.ordinal.toFloat(), tween(350, easing = KworkEasing), label = "tabPos")
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            OrdersTab.entries.forEachIndexed { i, t ->
                val active = t == selected
                val color by animateColorAsState(if (active) TextPrimary else TextMuted, tween(200), label = "tabColor")
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                        ) { onSelect(t) }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.label, style = LabelStyle, color = color)
                    Spacer(Modifier.width(6.dp))
                    Text(counts[i].toString(), style = LabelStyle, color = if (active) Lime else TextMuted)
                }
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            drawLine(OutlineSoft, Offset(0f, 1.5f), Offset(size.width, 1.5f), strokeWidth = 1f)
            val cell = size.width / n
            val x = cell * pos
            drawLine(Lime, Offset(x, 1f), Offset(x + cell * 0.5f, 1f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }
}
