package com.example.baitraktar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baitraktar.R

/*
 * Two voices:
 *   Manrope        — everything a human reads: headlines, body, button labels.
 *   JetBrains Mono — figures only: prices, counts, percentages. Labels stay in Manrope.
 */

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

val Mono = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
    Font(R.font.jetbrainsmono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
)

val Typography = Typography(
    // display — one per screen, tight tracking, heavy weight
    displayLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1.2).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.9).sp,
    ),
    // headline — screen titles and card titles
    headlineLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.4).sp,
    ),
    // title — list rows, card headers
    titleLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp,
    ),
    // body
    bodyLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    // label — the human voice, a step below body; mono is kept for figures only
    labelLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp,
    ),
)

/**
 * Labels, statuses, tabs, section names: the body face, a step smaller, sentence case,
 * untracked. Never mono and never caps — those read as a template, not as an instrument.
 */
val LabelStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp)

/** Figures — what the instrument reports: prices, counts, percentages. Mono is the material here. */
object MonoType {
    /** Timestamps, categories, secondary numbers. */
    val value = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp)
    /** Prices in lists. */
    val price = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.3).sp)
    /** The one big number on a screen. */
    val priceLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-1).sp)
    val counter = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 72.sp, letterSpacing = (-4).sp)
}

/*
 * Corner logic: the smaller the object, the tighter the corner.
 *   extraSmall 6  — chips, badges, tiny cards inside demos
 *   small     10  — inputs, segmented controls
 *   medium    14  — cards, buttons
 *   large     20  — hero surfaces
 *   extraLarge 28 — sheets
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spacing rhythm on a 4-pt step. */
object Space {
    val screen = 20.dp
    val section = 32.dp
    val block = 20.dp
    val card = 16.dp
    val row = 14.dp
    val gap = 8.dp
}
