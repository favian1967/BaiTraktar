package com.example.baitraktar.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.baitraktar.R
import com.example.baitraktar.ui.components.AnimatedCheck
import com.example.baitraktar.ui.components.Appear
import com.example.baitraktar.ui.components.Logo
import com.example.baitraktar.ui.components.KworkEasing
import com.example.baitraktar.ui.components.PrimaryButton
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Ink
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Red
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class StreamOrder(val title: String, val price: String, val match: Boolean)

// The matching one goes last so every card crosses the AI line before it settles.
private val streamOrders = listOf(
    StreamOrder("Логотип для бренда", "3 000 ₽", match = false),
    StreamOrder("Монтаж Reels", "7 000 ₽", match = false),
    StreamOrder("Сайт на Tilda", "12 000 ₽", match = false),
    StreamOrder("Дизайн презентации", "8 000 ₽", match = false),
    StreamOrder("Лендинг для IT-сервиса", "50 000 ₽", match = true),
)

/** Brightness / contrast / saturation lift for the artwork so it doesn't read as muddy. */
private val heroColorFilter: ColorFilter = run {
    val contrast = 1.18f
    val brightness = 14f
    val matrix = ColorMatrix().apply { setToSaturation(1.35f) }
    matrix *= ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    ColorFilter.colorMatrix(matrix)
}

/**
 * Timeline (ms):
 *   0–500    black, K mark assembles from three layers, lime line sweeps through it
 *   500      artwork fades in under the mark, mark dissolves into the tile in the picture
 *   550/700  headline line 1, then line 2 ("тебя" in lime)
 *   900–3000 order stream rises through the AI line; misses dim and fall back, the match grows
 *   3100     CTA appears
 */
@Composable
fun WelcomeStep(onStart: () -> Unit, onLogin: () -> Unit) {
    val haptic = LocalHapticFeedback.current

    val kAssemble = remember { Animatable(0f) }
    val lineSweep = remember { Animatable(0f) }
    val kAlpha = remember { Animatable(1f) }
    val artAlpha = remember { Animatable(0f) }
    val stream = remember { Animatable(0f) }
    var title1 by remember { mutableStateOf(false) }
    var title2 by remember { mutableStateOf(false) }
    var showStream by remember { mutableStateOf(false) }
    var showCta by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { kAssemble.animateTo(1f, tween(500, easing = KworkEasing)) }
        launch { delay(120); lineSweep.animateTo(1f, tween(480, easing = KworkEasing)) }
        delay(500)
        launch { artAlpha.animateTo(1f, tween(700, easing = KworkEasing)) }
        launch { delay(150); kAlpha.animateTo(0f, tween(450)) }
        delay(50); title1 = true
        delay(150); title2 = true
        delay(200); showStream = true
        stream.animateTo(1f, tween(2100, easing = LinearEasing))
        delay(150)
        showCta = true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Ink)) {
        val screenH = maxHeight
        val artHeight = screenH * 0.62f
        val artLift = screenH * 0.05f
        // The artwork is cropped by width (941×1672), so the tile's screen position follows
        // the width: it sits at ~47 % of the scaled image height.
        val kCenterY = maxWidth * (1672f / 941f) * 0.47f - artLift

        // ---- artwork ----
        Image(
            painter = painterResource(R.drawable.hero_welcome),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            colorFilter = heroColorFilter,
            modifier = Modifier
                .fillMaxWidth()
                .height(artHeight + artLift)
                .offset(y = -artLift)
                .alpha(artAlpha.value),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        0.86f to Ink.copy(alpha = 0.85f),
                        1f to Ink,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenH - artHeight + 1.dp)
                .align(Alignment.BottomCenter)
                .background(Ink),
        )

        // ---- intro: subtle texture + assembling K + lime line ----
        if (kAlpha.value > 0f) {
            IntroMark(
                assemble = kAssemble.value,
                sweep = lineSweep.value,
                alpha = kAlpha.value,
                centerY = kCenterY,
            )
        }

        // ---- copy + stream + CTA, anchored to the bottom ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = OnboardingHPadding),
        ) {
            Appear(visible = title1, durationMillis = 500) {
                Text("Подходящие заказы", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
            }
            Appear(visible = title2, durationMillis = 500) {
                Text(
                    buildAnnotatedString {
                        append("сами находят ")
                        append("тебя")
                        append(".")
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                )
            }
            VSpace(14.dp)
            OrderStream(
                progress = stream.value,
                visible = showStream,
                height = (screenH * 0.2f).coerceIn(150.dp, 190.dp),
                onMatch = { haptic.performHapticFeedback(HapticFeedbackType.Confirm) },
            )
            VSpace(14.dp)
            Appear(visible = showCta, durationMillis = 450) {
                Column {
                    PrimaryButton(text = "Начать", onClick = onStart, icon = Icons.AutoMirrored.Filled.ArrowForward)
                    VSpace(12.dp)
                    Text(
                        "Сервер уже настроен — пропустить",
                        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = onLogin,
                            )
                            .padding(vertical = 6.dp),
                    )
                }
            }
            VSpace(14.dp)
        }
    }
}

/** Black field with faint grain, the K mark converging from three offset layers, a lime line through it. */
@Composable
private fun IntroMark(assemble: Float, sweep: Float, alpha: Float, centerY: Dp) {
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize().alpha(alpha)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // faint texture: sparse dots, deterministic so it doesn't flicker
            val step = 26.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = (row % 2) * step / 2
                while (x < size.width) {
                    val n = ((x * 7 + y * 13).toInt() % 17) / 17f
                    drawCircle(Color.White.copy(alpha = 0.035f * n), radius = 1.2f, center = Offset(x, y))
                    x += step
                }
                y += step
                row++
            }
            // lime line sweeping horizontally through the mark
            val cy = with(density) { centerY.toPx() }
            val lineW = size.width * 0.9f
            val startX = -lineW + (size.width + lineW) * sweep
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Lime.copy(alpha = 0.7f), Lime, Lime.copy(alpha = 0.7f), Color.Transparent),
                    startX = startX, endX = startX + lineW,
                ),
                topLeft = Offset(startX, cy - 1f),
                size = Size(lineW, 2f),
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Lime.copy(alpha = 0.18f), Color.Transparent),
                    startX = startX, endX = startX + lineW,
                ),
                topLeft = Offset(startX, cy - 12f),
                size = Size(lineW, 24f),
            )
        }
        // The tile converges from three ghost copies: a lime one and a grey one slide in
        // from opposite corners while the real one fades up and settles from 90 % scale.
        val spread = 18.dp * (1f - assemble)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = centerY - 44.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Logo(
                size = 88.dp,
                colorFilter = ColorFilter.tint(Lime.copy(alpha = 0.35f * assemble), BlendMode.SrcIn),
                modifier = Modifier.offset(x = -spread, y = spread),
            )
            Logo(
                size = 88.dp,
                colorFilter = ColorFilter.tint(TextMuted.copy(alpha = 0.6f * assemble), BlendMode.SrcIn),
                modifier = Modifier.offset(x = spread, y = -spread),
            )
            Logo(
                size = 88.dp,
                modifier = Modifier.graphicsLayer {
                    this.alpha = assemble
                    scaleX = 0.9f + 0.1f * assemble; scaleY = scaleX
                },
            )
        }
    }
}

/**
 * Five order cards rising through a horizontal lime AI line. Cards that cross the line get a
 * verdict: misses dim to 30 %, shrink and keep drifting up; the match locks under the line and
 * grows ~7 %.
 */
@Composable
private fun OrderStream(progress: Float, visible: Boolean, height: Dp, onMatch: () -> Unit) {
    val cardH = 40.dp
    val gap = 8.dp
    val pitch = cardH + gap
    val lineY = height * 0.42f
    val n = streamOrders.size
    // Travel is chosen so that the last (matching) card stops just under the line.
    val startY = height + 6.dp
    val endYForLast = lineY + 6.dp
    val travel = startY + pitch * (n - 1) - endYForLast

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .alpha(if (visible) 1f else 0f),
    ) {
        // the AI line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = lineY)
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color.Transparent, Lime.copy(alpha = 0.9f), Lime, Lime.copy(alpha = 0.9f), Color.Transparent)),
                ),
        )
        Text(
            "AI",
            style = LabelStyle,
            color = Lime,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = lineY - 18.dp)
                .background(Ink.copy(alpha = 0.7f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        streamOrders.forEachIndexed { i, order ->
            val y = startY + pitch * i - travel * progress
            val crossed = y + cardH / 2 < lineY
            val settled = order.match && crossed
            StreamCard(order = order, crossed = crossed, settled = settled, y = y, cardH = cardH, onMatch = onMatch)
        }
    }
}

@Composable
private fun StreamCard(order: StreamOrder, crossed: Boolean, settled: Boolean, y: Dp, cardH: Dp, onMatch: () -> Unit) {
    val miss = crossed && !order.match
    val alpha by animateFloatAsState(if (miss) 0.3f else 1f, tween(250, easing = KworkEasing), label = "sAlpha")
    val scale by animateFloatAsState(
        when {
            settled -> 1.07f
            miss -> 0.96f
            else -> 1f
        },
        tween(if (settled) 500 else 250, easing = KworkEasing),
        label = "sScale",
    )
    val borderColor by animateFloatAsState(if (settled) 1f else 0f, tween(400), label = "sBorder")
    LaunchedEffect(settled) { if (settled) onMatch() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = y)
            .height(cardH)
            .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
            .background(Color(0xFF1A1D1A).copy(alpha = 0.92f), MaterialTheme.shapes.small)
            .border(
                1.dp,
                androidx.compose.ui.graphics.lerp(OutlineSoft, Lime, borderColor),
                MaterialTheme.shapes.small,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(order.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(order.price, style = MonoType.price, color = if (settled) Lime else TextSecondary)
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (order.match) {
                Box(modifier = Modifier.size(18.dp).background(Lime.copy(alpha = if (settled) 1f else 0f), CircleShape), contentAlignment = Alignment.Center) {
                    AnimatedCheck(visible = settled, size = 12.dp, color = Ink, strokeWidth = 1.8.dp)
                }
            } else {
                val xAlpha by animateFloatAsState(if (crossed) 1f else 0f, tween(200), label = "x")
                Text("×", style = MaterialTheme.typography.titleMedium, color = Red.copy(alpha = 0.8f), modifier = Modifier.alpha(xAlpha))
            }
        }
    }
}
