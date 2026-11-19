package com.example.baitraktar.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Five colour roles, nothing else:
 *   Ink      — the field everything sits on
 *   Surface  — three lifts above Ink (cards, chips, pressed)
 *   Lime     — the one accent: the AI line, verdicts, CTAs
 *   Text     — three tiers; all pass WCAG AA on Ink and Surface1
 *   Red      — misses and destructive actions only
 */

// Accent
val Lime = Color(0xFFC9F53C)
val OnLime = Color(0xFF0A0B0A)

// Field + surfaces
val Ink = Color(0xFF0A0B0A)
val Surface1 = Color(0xFF141614)
val Surface2 = Color(0xFF1C1F1C)
val Surface3 = Color(0xFF262A26)
val Outline = Color(0xFF2E332E)
val OutlineSoft = Color(0xFF212421)

// Text — 15.9:1 / 7.7:1 / 5.1:1 against Ink
val TextPrimary = Color(0xFFF3F4F0)
val TextSecondary = Color(0xFFA0A59E)
val TextMuted = Color(0xFF7E837C)

// Miss / destructive
val Red = Color(0xFFFF5A5F)
