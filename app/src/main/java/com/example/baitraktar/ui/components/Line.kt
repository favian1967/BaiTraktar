package com.example.baitraktar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Surface3
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary

/**
 * `Label ───────────── [trailing]` — a sentence-case label, a hairline that takes the rest,
 * optional trailing slot (a link, a count).
 */
@Composable
fun SectionRule(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LabelStyle, color = TextMuted)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = OutlineSoft)
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * AI verdict as a line: `Совпадение` on the left, the percentage on the right, and a
 * track that fills to [percent] with the house easing.
 */
@Composable
fun MatchMeter(percent: Int, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = tween(700, easing = KworkEasing),
        label = "match",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Совпадение", style = LabelStyle, color = TextMuted)
            Spacer(Modifier.weight(1f))
            Text("$percent%", style = MonoType.price, color = if (percent >= 70) Lime else TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            val cy = size.height / 2
            drawLine(Surface3, Offset(0f, cy), Offset(size.width, cy), strokeWidth = size.height, cap = StrokeCap.Round)
            if (fill > 0f) {
                drawLine(
                    if (percent >= 70) Lime else TextPrimary,
                    Offset(0f, cy), Offset(size.width * fill, cy),
                    strokeWidth = size.height, cap = StrokeCap.Round,
                )
            }
        }
    }
}
