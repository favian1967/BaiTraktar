package com.example.baitraktar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.baitraktar.ui.theme.Lime
import kotlinx.coroutines.delay

/** cubic-bezier(0.22, 1, 0.36, 1) — the house easing for everything that moves. */
val KworkEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Card-style entrance: opacity 0 → 1, translateY [offsetY] → 0 over [durationMillis].
 * Content is laid out immediately (so the layout doesn't jump), only drawn animated.
 */
@Composable
fun Appear(
    visible: Boolean,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    durationMillis: Int = 400,
    offsetY: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            if (delayMillis > 0) delay(delayMillis.toLong())
            progress.animateTo(1f, tween(durationMillis, easing = KworkEasing))
        } else {
            progress.snapTo(0f)
        }
    }
    val offsetPx = with(LocalDensity.current) { offsetY.toPx() }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * offsetPx
        },
    ) { content() }
}

/** A check mark that is stroked in over ~200 ms rather than just popping in. */
@Composable
fun AnimatedCheck(
    visible: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = Lime,
    strokeWidth: Dp = 2.dp,
    delayMillis: Int = 0,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            if (delayMillis > 0) delay(delayMillis.toLong())
            progress.animateTo(1f, tween(200, easing = KworkEasing))
        } else {
            progress.snapTo(0f)
        }
    }
    Canvas(modifier = modifier.size(size)) {
        val p = progress.value
        if (p <= 0f) return@Canvas
        val w = this.size.width
        val h = this.size.height
        val full = Path().apply {
            moveTo(w * 0.22f, h * 0.53f)
            lineTo(w * 0.43f, h * 0.74f)
            lineTo(w * 0.80f, h * 0.32f)
        }
        val measure = PathMeasure().apply { setPath(full, false) }
        val segment = Path()
        measure.getSegment(0f, measure.length * p, segment, true)
        drawPath(
            segment,
            color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
