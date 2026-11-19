package com.example.baitraktar.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
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
import com.example.baitraktar.ui.theme.Red
import com.example.baitraktar.ui.theme.Surface1
import com.example.baitraktar.ui.theme.Surface2
import com.example.baitraktar.ui.theme.Surface3
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun HowItWorksStep(onBack: () -> Unit, onSkip: () -> Unit, onNext: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    OnboardingStepScaffold(
        progressIndex = 0,
        onBack = onBack,
        onSkip = onSkip,
        bottom = {
            Appear(visible = shown, delayMillis = 900) {
                PrimaryButton(text = "Далее", onClick = onNext, icon = Icons.AutoMirrored.Filled.ArrowForward)
            }
        },
    ) {
        VSpace(16.dp)
        Appear(visible = shown) {
            Text("Ты не ищешь заказы.", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
        }
        Appear(visible = shown, delayMillis = 120) {
            Text("Их ищем мы.", style = MaterialTheme.typography.headlineLarge, color = TextSecondary)
        }
        VSpace(24.dp)

        Appear(visible = shown, delayMillis = 250) {
            StageRow(index = "01", title = "Сканируем", text = "Следим за новыми\nзаказами на Kwork 24/7") { ScanDemo() }
        }
        StageConnector()
        Appear(visible = shown, delayMillis = 400) {
            StageRow(index = "02", title = "Фильтруем", text = "AI пропускает только то,\nчто тебе подходит") { FilterDemo() }
        }
        StageConnector()
        Appear(visible = shown, delayMillis = 550) {
            StageRow(index = "03", title = "Присылаем", text = "Заказ и готовый ответ —\nсразу в приложение") { NotifyDemo(start = shown) }
        }

        VSpace(28.dp)
        Appear(visible = shown, delayMillis = 800) {
            SwipeDemo()
        }
        VSpace(16.dp)
    }
}

@Composable
private fun StageRow(index: String, title: String, text: String, demo: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) { demo() }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(index, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            VSpace(4.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun StageConnector() {
    Box(
        modifier = Modifier
            .padding(start = 41.dp)
            .width(2.dp)
            .height(22.dp)
            .background(Surface3, CircleShape),
    )
}

/** Tiny order chip used inside the demos. */
@Composable
private fun MiniCard(modifier: Modifier = Modifier, accent: Color = Surface3, width: Dp = 30.dp) {
    Box(
        modifier = modifier
            .width(width)
            .height(12.dp)
            .background(Surface2, RoundedCornerShape(2.dp))
            .border(0.5.dp, OutlineSoft, RoundedCornerShape(2.dp))
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(modifier = Modifier.width(12.dp).height(2.dp).background(accent, CircleShape))
    }
}

/** Search disc with small order chips popping around it. */
@Composable
private fun ScanDemo() {
    val t by rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "scanT",
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val positions = listOf(Pair(-30.dp, -26.dp), Pair(30.dp, -18.dp), Pair(-26.dp, 26.dp), Pair(30.dp, 26.dp))
        positions.forEachIndexed { i, (x, y) ->
            val phase = ((t + i / 4f) % 1f)
            val a = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f // triangle 0→1→0
            MiniCard(
                modifier = Modifier
                    .offset(x = x, y = y - 4.dp * a)
                    .alpha(a.coerceIn(0f, 1f)),
                accent = if (i % 2 == 0) Lime else Surface3,
            )
        }
        IconDisc(size = 52.dp, background = Surface1) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Lime, modifier = Modifier.size(22.dp))
        }
    }
}

/** Funnel: chips drop in from the top — even ones pass through in lime, odd ones bounce off dimmed. */
@Composable
private fun FilterDemo() {
    val t by rememberInfiniteTransition(label = "filter").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "filterT",
    )
    Box(modifier = Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        for (i in 0 until 4) {
            val phase = (t + i / 4f) % 1f
            val pass = i % 2 == 0
            val y = -40.dp + 80.dp * phase
            val pastFunnel = phase > 0.5f
            val x = if (!pass && pastFunnel) 26.dp * ((phase - 0.5f) * 2f) else 0.dp
            val alpha = when {
                phase < 0.1f -> phase * 10f
                !pass && pastFunnel -> 1f - (phase - 0.5f) * 2f
                phase > 0.9f -> (1f - phase) * 10f
                else -> 1f
            }
            MiniCard(
                modifier = Modifier.offset(x = x, y = y).alpha(alpha.coerceIn(0f, 1f)),
                accent = if (pass && pastFunnel) Lime else Surface3,
                width = 26.dp,
            )
        }
        IconDisc(size = 52.dp, background = Surface1.copy(alpha = 0.92f)) {
            Icon(Icons.Outlined.FilterAlt, contentDescription = null, tint = Lime, modifier = Modifier.size(22.dp))
        }
    }
}

/** A notification slides in and gives a 2–3 px shake, with a haptic tick. */
@Composable
private fun NotifyDemo(start: Boolean) {
    val haptic = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(start) {
        if (!start) return@LaunchedEffect
        delay(1000)
        visible = true
        delay(420)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        for (dx in listOf(2.5f, -2.5f, 1.5f, -1f, 0f)) {
            shake.animateTo(dx, tween(45))
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IconDisc(size = 52.dp, background = Surface1) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = Lime, modifier = Modifier.size(22.dp))
        }
        Appear(
            visible = visible,
            offsetY = 10.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = shake.value.dp, y = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .requiredWidth(100.dp)
                    .background(Surface2, MaterialTheme.shapes.extraSmall)
                    .border(1.dp, Lime.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 7.dp, vertical = 5.dp),
            ) {
                Text("Подходит", style = LabelStyle, color = Lime, maxLines = 1)
                Text("50 000 ₽", style = MonoType.value, color = TextPrimary, maxLines = 1)
            }
        }
    }
}

private data class DemoOrder(val title: String, val price: String)

private val demoOrders = listOf(
    DemoOrder("Лендинг для IT-сервиса", "50 000 ₽"),
    DemoOrder("Перевод статьи на английский", "1 500 ₽"),
    DemoOrder("UX/UI мобильного приложения", "30 000 ₽"),
)

/**
 * Hands-on filter: drag the card right = «Подходит», left = «Не подходит».
 * The verdict is echoed under the deck as "AI запомнил".
 */
@Composable
private fun SwipeDemo() {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var index by remember { mutableIntStateOf(0) }
    var lastVerdict by remember { mutableStateOf<Boolean?>(null) }
    val offsetX = remember { Animatable(0f) }
    val thresholdPx = with(density) { 96.dp.toPx() }
    val flyPx = with(density) { 420.dp.toPx() }

    Column {
        Text(
            "Попробуй сам: карточку вправо — подходит, влево — нет",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        VSpace(12.dp)
        Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            if (index < demoOrders.size) {
                // next card peeking behind
                if (index + 1 < demoOrders.size) {
                    DeckCard(
                        demoOrders[index + 1],
                        modifier = Modifier.graphicsLayer { scaleX = 0.95f; scaleY = 0.95f; alpha = 0.6f },
                    )
                }
                val dragFraction = (offsetX.value / thresholdPx).coerceIn(-1f, 1f)
                DeckCard(
                    demoOrders[index],
                    verdictFraction = dragFraction,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = offsetX.value
                            rotationZ = offsetX.value / 40f
                        }
                        .pointerInput(index) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val v = offsetX.value
                                    if (abs(v) > thresholdPx) {
                                        val liked = v > 0
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        scope.launch {
                                            offsetX.animateTo(if (liked) flyPx else -flyPx, tween(300, easing = KworkEasing))
                                            lastVerdict = liked
                                            index++
                                            offsetX.snapTo(0f)
                                        }
                                    } else {
                                        scope.launch { offsetX.animateTo(0f, spring(stiffness = 500f)) }
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                                },
                            )
                        },
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(20.dp).background(Lime, CircleShape), contentAlignment = Alignment.Center) {
                        AnimatedCheck(visible = true, size = 13.dp, color = Ink, strokeWidth = 1.8.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("AI уже понял, что тебе интересно", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        }
        VSpace(10.dp)
        AnimatedVisibility(visible = lastVerdict != null && index < demoOrders.size, enter = fadeIn(), exit = fadeOut()) {
            Text(
                if (lastVerdict == true) "AI запомнил: такие — подходят" else "AI запомнил: такие — не нужны",
                style = MaterialTheme.typography.bodySmall,
                color = if (lastVerdict == true) Lime else TextSecondary,
            )
        }
    }
}

@Composable
private fun DeckCard(order: DemoOrder, modifier: Modifier = Modifier, verdictFraction: Float = 0f) {
    val like = verdictFraction.coerceAtLeast(0f)
    val nope = (-verdictFraction).coerceAtLeast(0f)
    val borderColor = when {
        like > 0f -> androidx.compose.ui.graphics.lerp(OutlineSoft, Lime, like)
        nope > 0f -> androidx.compose.ui.graphics.lerp(OutlineSoft, Red, nope)
        else -> OutlineSoft
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Surface1, MaterialTheme.shapes.medium)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text("Новый заказ", style = LabelStyle, color = TextMuted)
            VSpace(2.dp)
            Text(order.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1)
            VSpace(2.dp)
            Text(order.price, style = MonoType.price, color = TextSecondary)
        }
        Text(
            "Подходит",
            style = LabelStyle,
            color = Lime,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp).alpha(like),
        )
        Text(
            "Не подходит",
            style = LabelStyle,
            color = Red,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp).alpha(nope),
        )
    }
}
