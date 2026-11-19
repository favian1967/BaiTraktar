package com.example.baitraktar.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.baitraktar.ui.components.AnimatedCheck
import com.example.baitraktar.ui.components.Appear
import com.example.baitraktar.ui.components.KworkEasing
import com.example.baitraktar.ui.components.PrimaryButton
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Ink
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Surface1
import com.example.baitraktar.ui.theme.Surface2
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private val checks = listOf("Заказ найден", "AI проверил", "Ответ подготовлен")

/**
 * Timeline (ms): 0 title · 120 subtitle · 350 order card · 700 AI MATCH counts to 94 ·
 * 1150 reply card rises · 1650/1950/2250 checks · last check fires a lime pulse · 2600 CTA.
 */
@Composable
fun DoneStep(onFinish: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var shown by remember { mutableStateOf(false) }
    var matchTarget by remember { mutableIntStateOf(0) }
    var checksShown by remember { mutableIntStateOf(0) }
    var showCta by remember { mutableStateOf(false) }
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        shown = true
        delay(700); matchTarget = 94
        delay(950)
        repeat(checks.size) { i ->
            checksShown = i + 1
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            delay(300)
        }
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(750, easing = KworkEasing))
        showCta = true
    }
    val match by animateIntAsState(matchTarget, tween(700, easing = KworkEasing), label = "match")

    OnboardingStepScaffold(
        progressIndex = 2,
        onBack = null,
        onSkip = null,
        bottom = {
            Appear(visible = showCta, durationMillis = 450) {
                PrimaryButton(text = "Перейти к заказам", onClick = onFinish, icon = Icons.AutoMirrored.Filled.ArrowForward)
            }
        },
    ) {
        VSpace(16.dp)
        Appear(visible = shown) {
            Text("Готово.", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
        }
        Appear(visible = shown, delayMillis = 120) {
            Text("Теперь можно работать.", style = MaterialTheme.typography.headlineLarge, color = TextSecondary)
        }
        VSpace(12.dp)
        Appear(visible = shown, delayMillis = 200) {
            Text(
                "Мы будем следить за Kwork,\nотбирать подходящие заказы\nи присылать их тебе.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        VSpace(24.dp)

        Appear(visible = shown, delayMillis = 350, durationMillis = 500) {
            OrderShowcase(match = match, pulse = pulse.value)
        }
        VSpace(10.dp)
        Appear(visible = shown, delayMillis = 1150, durationMillis = 500, offsetY = 36.dp) {
            ReplyCard()
        }
        VSpace(20.dp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            checks.forEachIndexed { i, label ->
                val on = checksShown > i
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(if (on) Lime else Surface2, CircleShape)
                            .border(1.dp, if (on) Lime else OutlineSoft, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedCheck(visible = on, size = 14.dp, color = Ink, strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (on) TextPrimary else TextMuted,
                    )
                }
            }
        }
        VSpace(16.dp)
    }
}

/** Big order card with the AI MATCH badge; [pulse] 0→1 draws an expanding lime ring around it. */
@Composable
private fun OrderShowcase(match: Int, pulse: Float) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (pulse > 0f && pulse < 1f) {
                    val grow = 14.dp.toPx() * pulse
                    drawRoundRect(
                        color = Lime.copy(alpha = 0.65f * (1f - pulse)),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = CornerRadius(20.dp.toPx() + grow),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            .background(Brush.verticalGradient(listOf(Color(0xFF1E211E), Color(0xFF141614))), shape)
            .border(1.dp, Lime.copy(alpha = 0.55f), shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).background(Lime, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Новый заказ", style = LabelStyle, color = Lime)
            Spacer(Modifier.weight(1f))
            Text("2 мин назад", style = LabelStyle, color = TextMuted)
        }
        VSpace(12.dp)
        Text("Дизайн лендинга\nдля IT-сервиса", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        VSpace(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("50 000 ₽", style = MonoType.priceLarge, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .background(Lime.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall)
                    .border(1.dp, Lime.copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Совпадение", style = LabelStyle, color = Lime)
                Spacer(Modifier.width(8.dp))
                Text("$match%", style = MonoType.price, color = Lime)
            }
        }
    }
}

@Composable
private fun ReplyCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(Surface1, MaterialTheme.shapes.medium)
            .border(1.dp, OutlineSoft, MaterialTheme.shapes.medium)
            .padding(14.dp),
    ) {
        Text("Ответ подготовлен", style = LabelStyle, color = Lime)
        VSpace(6.dp)
        Text(
            "Здравствуйте! Заинтересовал ваш проект. Имею опыт в дизайне лендингов для IT-сервисов…",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 2,
        )
    }
}
