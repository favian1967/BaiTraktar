package com.example.baitraktar.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.baitraktar.ui.components.AnimatedCheck
import com.example.baitraktar.ui.components.KworkEasing
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Ink
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.Space
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.Surface3
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary

val OnboardingHPadding = Space.screen

/**
 * Frame for steps 2–4: top row (back / progress / skip), scrollable body that takes the
 * remaining height, bottom slot pinned above the nav bar. Body scrolls on short screens.
 */
@Composable
fun OnboardingStepScaffold(
    progressIndex: Int,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    bottom: @Composable ColumnScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
                }
            }
            StepProgress(current = progressIndex, total = 3)
            if (onSkip != null) {
                Text(
                    "Пропустить",
                    style = LabelStyle,
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable(
                            onClick = onSkip,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OnboardingHPadding),
            content = body,
        )
        Column(
            modifier = Modifier.padding(horizontal = OnboardingHPadding, vertical = 16.dp),
            content = bottom,
        )
    }
}

/** `✓ ━ ● ━ ○` — passed steps turn into small checks, the current one is a lime dot. */
@Composable
fun StepProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val done = i < current
            val active = i == current
            val size by animateDpAsState(
                if (done) 16.dp else if (active) 10.dp else 7.dp,
                tween(300, easing = KworkEasing),
                label = "dot$i",
            )
            val color by animateColorAsState(if (done || active) Lime else Surface3, label = "dotColor$i")
            Box(
                modifier = Modifier.size(size).background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedCheck(visible = done, size = 11.dp, color = Ink, strokeWidth = 1.8.dp)
            }
            if (i < total - 1) {
                val lineColor by animateColorAsState(if (i < current) Lime.copy(alpha = 0.6f) else Surface3, label = "line$i")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .width(22.dp)
                        .height(2.dp)
                        .background(lineColor, CircleShape),
                )
            }
        }
    }
}

/** Round tinted disc with content centered — used for step icons and list bullets. */
@Composable
fun IconDisc(size: Dp, background: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

