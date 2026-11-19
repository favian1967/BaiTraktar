package com.example.baitraktar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KworkColorScheme = darkColorScheme(
    primary = Lime,
    onPrimary = OnLime,
    primaryContainer = Lime,
    onPrimaryContainer = OnLime,
    secondary = Surface2,
    onSecondary = TextPrimary,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = Surface1,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Outline,
    outlineVariant = OutlineSoft,
    error = Red,
)

@Composable
fun BaiTraktarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KworkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
