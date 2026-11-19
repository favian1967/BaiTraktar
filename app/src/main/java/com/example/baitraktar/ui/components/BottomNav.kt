package com.example.baitraktar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Ink
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.TextMuted

enum class Tab(val label: String, val icon: ImageVector) {
    SEARCH("Радар", Icons.Outlined.Search),
    ORDERS("Заказы", Icons.Outlined.Inventory2),
    SETTINGS("Настройки", Icons.Outlined.Settings),
}

/**
 * Three tabs under a hairline. The active one is marked by a lime segment of that
 * hairline — the AI line — which slides between tabs in 350 ms.
 */
@Composable
fun KworkBottomBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val count = Tab.entries.size
    val indicator by animateFloatAsState(
        targetValue = selected.ordinal.toFloat(),
        animationSpec = tween(350, easing = KworkEasing),
        label = "tabLine",
    )
    Column(modifier = modifier.fillMaxWidth().background(Ink)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            val cy = 0.5f
            drawLine(OutlineSoft, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1f)
            val cell = size.width / count
            val segW = cell * 0.36f
            val x = cell * indicator + (cell - segW) / 2
            drawLine(Lime, Offset(x, 1f), Offset(x + segW, 1f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .selectableGroup()
                .padding(top = 8.dp, bottom = 6.dp),
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val tint by animateColorAsState(if (active) Lime else TextMuted, tween(200), label = "tabTint")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                        ) { onSelect(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(tab.label, style = LabelStyle, color = tint)
                }
            }
        }
    }
}
