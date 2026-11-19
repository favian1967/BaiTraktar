package com.example.baitraktar.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Ink
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Surface3
import com.example.baitraktar.ui.theme.TextSecondary
import com.example.baitraktar.ui.theme.OnLime
import com.example.baitraktar.ui.theme.Outline
import com.example.baitraktar.ui.theme.Surface1
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.TextPrimary

/**
 * Lime CTA. Touch: scale 0.97 in 100 ms. Release: spring back, the arrow nudges right.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (pressed) tween(100) else spring(stiffness = 600f),
        label = "ctaScale",
    )
    val arrowShift by animateDpAsState(
        targetValue = if (pressed) 6.dp else 0.dp,
        animationSpec = tween(220, easing = KworkEasing),
        label = "ctaArrow",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = Lime,
            contentColor = OnLime,
            disabledContainerColor = Lime.copy(alpha = 0.35f),
            disabledContentColor = OnLime.copy(alpha = 0.6f),
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge)
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = arrowShift)
                        .size(20.dp),
                )
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(100), label = "secScale")
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Outline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Surface1, contentColor = TextPrimary),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge)
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterEnd).size(18.dp),
                )
            }
        }
    }
}

/**
 * Inline text action ("Все →", "Выбрать все"). Mono caps, lime by default,
 * padded so the touch target is 44 dp tall even though the text is 11 sp.
 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Lime,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clickable(
                onClick = onClick,
            )
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        if (icon != null) {
            Spacer(Modifier.width(4.dp))
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
    }
}

/** Material switch in house colours: ink thumb on lime when on, muted on Surface3 when off. */
@Composable
fun KworkSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Ink,
            checkedTrackColor = Lime,
            uncheckedThumbColor = TextSecondary,
            uncheckedTrackColor = Surface3,
            uncheckedBorderColor = OutlineSoft,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
/** Small round thumb on a 3 dp track — quieter than the Material default. */
@Composable
fun KworkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        interactionSource = interaction,
        modifier = modifier.systemGestureExclusion(),
        thumb = { SliderThumb(interaction) },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(3.dp),
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 0.dp,
                drawStopIndicator = null,
                colors = SliderDefaults.colors(activeTrackColor = Lime, inactiveTrackColor = Surface3),
            )
        },
    )
}

/** 20 dp disc that grows to 24 while dragged; ink core so it reads on both track colours. */
@Composable
private fun SliderThumb(interaction: MutableInteractionSource) {
    val dragged by interaction.collectIsDraggedAsState()
    val size by animateDpAsState(if (dragged) 24.dp else 20.dp, tween(120), label = "thumb")
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .background(TextPrimary, CircleShape)
                .border(4.dp, Ink, CircleShape),
        )
    }
}
