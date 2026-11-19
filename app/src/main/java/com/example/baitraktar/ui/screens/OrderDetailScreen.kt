package com.example.baitraktar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.baitraktar.model.Order
import com.example.baitraktar.ui.components.Appear
import com.example.baitraktar.ui.components.BackTopBar
import com.example.baitraktar.ui.components.MatchMeter
import com.example.baitraktar.ui.components.PrimaryButton
import com.example.baitraktar.ui.components.SecondaryButton
import com.example.baitraktar.ui.components.SectionRule
import com.example.baitraktar.ui.components.StatusChip
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Space
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary

/**
 * One order, laid out like a spec sheet: mono meta line, big title, mono price, the AI
 * verdict as a meter, then a table of facts and the brief. Hide lives in the top bar;
 * the bottom keeps just the two actions that matter.
 */
@Composable
fun OrderDetailScreen(order: Order, onBack: () -> Unit, onOpenKwork: () -> Unit, onHide: () -> Unit, onAiReply: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BackTopBar(onBack = onBack) {
            IconButton(onClick = onHide) {
                Icon(Icons.Outlined.VisibilityOff, contentDescription = "Скрыть заказ", tint = TextSecondary)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(order.status)
                Spacer(Modifier.width(10.dp))
                Text(order.timeAgo, style = LabelStyle, color = TextSecondary)
            }
            Spacer(Modifier.height(16.dp))
            Text(order.title, style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(order.price, style = MonoType.priceLarge, color = Lime)

            Spacer(Modifier.height(Space.section))
            MatchMeter(percent = order.match)

            Spacer(Modifier.height(Space.section))
            SectionRule("Детали")
            Spec("Категория", order.category)
            if (order.location.isNotBlank()) Spec("Город", order.location)
            Spec("Откликов", order.offers)

            Spacer(Modifier.height(Space.section))
            SectionRule("Задача")
            Spacer(Modifier.height(12.dp))
            Text(order.description, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            Spacer(Modifier.height(Space.block))
        }
        Column(modifier = Modifier.padding(horizontal = Space.screen, vertical = 12.dp)) {
            PrimaryButton(text = "Ответ от AI", onClick = onAiReply, icon = Icons.Outlined.AutoAwesome)
            Spacer(Modifier.height(10.dp))
            SecondaryButton(text = "Открыть на ${order.sourceLabel}", onClick = onOpenKwork, icon = Icons.Outlined.OpenInNew)
        }
    }
}

/** `Категория ……… Дизайн и графика` — one row of the spec table. */
@Composable
private fun Spec(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LabelStyle, color = TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
    HorizontalDivider(color = OutlineSoft)
}
