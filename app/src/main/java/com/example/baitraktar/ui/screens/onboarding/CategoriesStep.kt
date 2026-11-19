package com.example.baitraktar.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.baitraktar.model.PriceScale
import com.example.baitraktar.model.formatRub
import com.example.baitraktar.model.pluralize
import com.example.baitraktar.ui.components.CategoryChip
import com.example.baitraktar.ui.components.KworkSlider
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.components.Appear
import com.example.baitraktar.ui.components.KworkEasing
import com.example.baitraktar.ui.components.PrimaryButton
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Surface1
import com.example.baitraktar.ui.theme.Surface2
import com.example.baitraktar.ui.theme.Surface3
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class Category(val name: String, val icon: ImageVector)

val onboardingCategories = listOf(
    Category("Дизайн", Icons.Outlined.DesktopWindows),
    Category("UX/UI", Icons.Outlined.DesktopWindows),
    Category("Разработка", Icons.Outlined.Code),
    Category("Тексты", Icons.Outlined.Description),
    Category("Маркетинг", Icons.Outlined.BarChart),
    Category("Аудио и видео", Icons.Outlined.PlayCircleOutline),
    Category("Бизнес", Icons.Outlined.Work),
    Category("Другое", Icons.Outlined.MoreHoriz),
)

private fun pluralDirections(n: Int) = "$n " + pluralize(n, "направление выбрано", "направления выбрано", "направлений выбрано")


@Composable
fun CategoriesStep(
    state: OnboardingState,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    var sliderT by remember { mutableFloatStateOf(PriceScale.toSlider(state.minPrice)) }
    val price = PriceScale.rounded(sliderT)
    val animatedPrice by animateIntAsState(price, tween(220, easing = KworkEasing), label = "price")
    var lastTick by remember { mutableStateOf(price) }
    LaunchedEffect(price) {
        if (price != lastTick) {
            lastTick = price
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    // 03 → 04: the chosen filters collapse into one card that shrinks and flies up.
    var flying by remember { mutableStateOf(false) }
    val flyProgress = remember { Animatable(0f) }
    val flyIn = remember { Animatable(0f) }
    fun startFly() {
        if (flying) return
        flying = true
        state.minPrice = price.toFloat()
        scope.launch {
            flyIn.animateTo(1f, tween(320, easing = KworkEasing))
            delay(450)
            flyProgress.animateTo(1f, tween(520, easing = KworkEasing))
            onNext()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().alpha(1f - flyIn.value * 0.85f)) {
            OnboardingStepScaffold(
                progressIndex = 1,
                onBack = onBack,
                onSkip = onSkip,
                bottom = {
                    Appear(visible = shown, delayMillis = 500) {
                        PrimaryButton(
                            text = if (state.categories.isEmpty()) "Продолжить с настройками по умолчанию" else "Продолжить",
                            onClick = ::startFly,
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                        )
                    }
                },
            ) {
                VSpace(16.dp)
                Appear(visible = shown) {
                    Text("Что ты хочешь\nполучать?", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
                }
                VSpace(10.dp)
                Appear(visible = shown, delayMillis = 100) {
                    Text(
                        "Выбери несколько направлений.\nОстальное AI отсеет сам.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                VSpace(22.dp)
                Appear(visible = shown, delayMillis = 200) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        onboardingCategories.forEach { c ->
                            val isSelected = c.name in state.categories
                            CategoryChip(
                                label = c.name,
                                icon = c.icon,
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    if (isSelected) state.categories.remove(c.name) else state.categories.add(c.name)
                                },
                            )
                        }
                    }
                }
                VSpace(12.dp)
                AnimatedVisibility(
                    visible = state.categories.isNotEmpty(),
                    enter = fadeIn(tween(250)) + expandVertically(tween(250, easing = KworkEasing)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
                ) {
                    Text(pluralDirections(state.categories.size), style = MaterialTheme.typography.bodySmall, color = Lime)
                }

                // Price + live preview rise in once at least one category is picked.
                AnimatedVisibility(
                    visible = state.categories.isNotEmpty(),
                    enter = fadeIn(tween(450, easing = KworkEasing)) +
                        slideInVertically(tween(450, easing = KworkEasing)) { it / 3 } +
                        expandVertically(tween(450, easing = KworkEasing)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(250, easing = KworkEasing)),
                ) {
                    Column {
                        VSpace(28.dp)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Минимальная цена заказа", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Spacer(Modifier.weight(1f))
                            Text("от ${formatRub(animatedPrice)}", style = MonoType.price, color = Lime)
                        }
                        KworkSlider(
                            value = sliderT,
                            onValueChange = { sliderT = it },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                VSpace(16.dp)
            }
        }

        if (flying) {
            FilterSummaryCard(
                categories = state.categories.toList(),
                price = price,
                appear = flyIn.value,
                fly = flyProgress.value,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** The chosen filters gathered into one card that scales down and flies off the top. */
@Composable
private fun FilterSummaryCard(categories: List<String>, price: Int, appear: Float, fly: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .graphicsLayer {
                val s = (0.9f + 0.1f * appear) * (1f - 0.6f * fly)
                scaleX = s; scaleY = s
                alpha = appear * (1f - fly)
                translationY = -fly * size.height * 4f
            }
            .width(240.dp)
            .background(Surface2, MaterialTheme.shapes.large)
            .border(1.dp, Lime.copy(alpha = 0.7f), MaterialTheme.shapes.large)
            .padding(18.dp),
    ) {
        Text("Твой фильтр", style = LabelStyle, color = Lime)
        VSpace(10.dp)
        (categories.ifEmpty { listOf("По умолчанию") }).take(4).forEach {
            Text(it, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        if (categories.size > 4) Text("+${categories.size - 4}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        VSpace(10.dp)
        Text("от ${formatRub(price)}", style = MonoType.value, color = TextSecondary)
    }
}
