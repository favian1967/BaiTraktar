package com.example.baitraktar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.baitraktar.model.Order
import com.example.baitraktar.model.OrderStatus
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Ink
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Space
import com.example.baitraktar.ui.theme.Surface1
import com.example.baitraktar.ui.theme.Surface2
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    color: Color = Surface1,
    padding: PaddingValues = PaddingValues(Space.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(color, MaterialTheme.shapes.medium)
            .border(1.dp, OutlineSoft, MaterialTheme.shapes.medium)
            .padding(padding),
        content = content,
    )
}

/** Mono status tag. MATCH is the only one that gets solid lime — it is the verdict that matters. */
@Composable
fun StatusChip(status: OrderStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        OrderStatus.MATCH -> Lime to Ink
        OrderStatus.SENT -> Lime.copy(alpha = 0.16f) to Lime
        OrderStatus.HIDDEN -> Surface2 to TextSecondary
        OrderStatus.OFF_PROFILE -> Surface2 to TextMuted
    }
    Box(
        modifier = modifier
            .background(bg, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(status.label, style = LabelStyle, color = fg)
    }
}

/**
 * One order in any list. Title, then status and category on the left; price over age on the right.
 * No card chrome — rows sit on the field and are separated by hairlines, so a list reads
 * as an instrument log rather than a stack of boxes.
 * [showMatch] puts the AI score before the age — for lists where the score is what you sort by.
 */
@Composable
fun OrderRow(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier, showMatch: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.row),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                order.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(order.status)
                Spacer(Modifier.width(8.dp))
                Text(order.category, style = LabelStyle, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(order.price, style = MonoType.price, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // score 0 is "no verdict from the server", not a 0 % match — don't print it.
                if (showMatch && order.match > 0) {
                    Text("${order.match}%", style = LabelStyle, color = if (order.match >= 70) Lime else TextSecondary)
                    Text(" · ", style = LabelStyle, color = TextMuted)
                }
                Text(order.timeAgo, style = LabelStyle, color = TextMuted)
            }
        }
    }
}

/**
 * Selectable chip with an icon. Lime border snaps in, a check is stroked inside,
 * width animates so neighbours shift over. Shared by onboarding and filters.
 */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val border by animateColorAsState(if (selected) Lime else OutlineSoft, tween(160), label = "chipBorder")
    val bg by animateColorAsState(if (selected) Lime.copy(alpha = 0.08f) else Surface1, tween(220), label = "chipBg")
    val bump = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            bump.snapTo(0.96f)
            bump.animateTo(1f, tween(260, easing = KworkEasing))
        }
    }
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
            .height(44.dp)
            .background(bg, MaterialTheme.shapes.small)
            .border(1.dp, border, MaterialTheme.shapes.small)
            .clickable(
                onClick = onClick,
            )
            .animateContentSize(tween(220, easing = KworkEasing))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) Lime else TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.size(18.dp).background(Lime, CircleShape), contentAlignment = Alignment.Center) {
                AnimatedCheck(visible = true, size = 12.dp, color = Ink, strokeWidth = 1.8.dp)
            }
        }
    }
}

/** What belongs here and why it is not here yet — plain words, no ornament. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 32.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        if (detail != null) {
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

/** Loading placeholder in the shape of [OrderRow], so the list does not jump when data lands. */
@Composable
fun OrderRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = Space.row)) {
        Column(modifier = Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.8f).height(16.dp).background(Surface2, MaterialTheme.shapes.extraSmall))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(120.dp).height(12.dp).background(Surface2, MaterialTheme.shapes.extraSmall))
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.width(64.dp).height(16.dp).background(Surface2, MaterialTheme.shapes.extraSmall))
    }
}
