package com.example.baitraktar.model

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** `12 000 ₽` — thin-space groups, ruble sign. */
fun formatRub(n: Int): String =
    n.toString().reversed().chunked(3).joinToString(" ").reversed() + " ₽"

/**
 * Price sliders work in 0..1; price grows quadratically so the cheap end,
 * where most orders live, gets most of the track.
 */
object PriceScale {
    const val MIN = 500f
    const val MAX = 200_000f

    fun toPrice(t: Float): Float = MIN + t * t * (MAX - MIN)
    fun toSlider(p: Float): Float = sqrt(((p - MIN) / (MAX - MIN)).coerceIn(0f, 1f))
    /** Rounded to 500 so labels don't jitter while dragging. */
    fun rounded(t: Float): Int = (toPrice(t) / 500f).roundToInt() * 500
}

fun pluralize(n: Int, one: String, few: String, many: String): String {
    val r10 = n % 10
    val r100 = n % 100
    return when {
        r100 in 11..14 -> many
        r10 == 1 -> one
        r10 in 2..4 -> few
        else -> many
    }
}
