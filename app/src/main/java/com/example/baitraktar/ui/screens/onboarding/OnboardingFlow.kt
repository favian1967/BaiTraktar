package com.example.baitraktar.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.baitraktar.ui.components.KworkEasing
import com.example.baitraktar.ui.theme.Lime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the onboarding collects; handed to the app when it finishes. */
class OnboardingState {
    val categories = listOf("Дизайн").toMutableStateList()
    var minPrice by mutableFloatStateOf(3_000f)
}

/**
 * First launch: welcome → how it works → what to receive → done.
 *
 * 01→02 is a lime band that grows from the CTA and the next screen appears out of it;
 * the other transitions are a soft fade/rise (03→04 is preceded by the filter card flying up,
 * which lives inside [CategoriesStep]).
 */
@Composable
fun OnboardingFlow(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val state = remember { OnboardingState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Lime band: coverage 0..1 grows from a hairline at the CTA to the whole screen.
    val bandCover = remember { Animatable(0f) }
    val bandAlpha = remember { Animatable(0f) }
    var bandRunning by remember { mutableStateOf(false) }

    fun goWithBand(next: Int) {
        if (bandRunning) return
        bandRunning = true
        scope.launch {
            bandAlpha.snapTo(1f)
            bandCover.snapTo(0f)
            bandCover.animateTo(1f, tween(500, easing = KworkEasing))
            step = next
            delay(60)
            bandAlpha.animateTo(0f, tween(450, easing = KworkEasing))
            bandCover.snapTo(0f)
            bandRunning = false
        }
    }

    BackHandler(enabled = step > 0 && !bandRunning) { step-- }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (initialState == 0 && targetState == 1) {
                    // the band hides the swap; just let the new screen be there
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                } else {
                    (fadeIn(tween(450, easing = KworkEasing)) +
                        slideInVertically(tween(450, easing = KworkEasing)) { it / 12 } +
                        scaleIn(tween(450, easing = KworkEasing), initialScale = 0.98f)) togetherWith
                        fadeOut(tween(250))
                }
            },
            label = "onboarding",
        ) { s ->
            when (s) {
                0 -> WelcomeStep(onStart = { goWithBand(1) }, onLogin = onFinish)
                1 -> HowItWorksStep(onBack = { step = 0 }, onSkip = onFinish, onNext = { step = 2 })
                2 -> CategoriesStep(
                    state = state,
                    onBack = { step = 1 },
                    onSkip = onFinish,
                    onNext = { step = 3 },
                )
                else -> DoneStep(
                    onFinish = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onFinish()
                    },
                )
            }
        }

        if (bandAlpha.value > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cover = bandCover.value
                val originY = size.height * 0.84f // roughly where the CTA sits
                val top = originY * (1f - cover)
                val bottom = originY + (size.height - originY) * cover
                val h = (bottom - top).coerceAtLeast(3f)
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Lime.copy(alpha = 0.92f), Lime, Lime.copy(alpha = 0.92f)),
                        startY = top, endY = top + h,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, h),
                    alpha = bandAlpha.value,
                )
                // thin bright core so it reads as a "line" while still small
                drawRect(
                    color = Color.White.copy(alpha = 0.35f * (1f - cover) * bandAlpha.value),
                    topLeft = Offset(0f, originY - 1f),
                    size = Size(size.width, 2f),
                )
            }
        }
    }
}
