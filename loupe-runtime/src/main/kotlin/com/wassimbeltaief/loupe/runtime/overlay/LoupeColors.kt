package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.ui.graphics.Color

/**
 * Shared visual grammar (CLAUDE.md "Visual Design Language").
 *
 * Light, cream-white surfaces per the design mockups — opaque, no transparency.
 */
internal object LoupeColors {
    // Severity
    val Hot = Color(0xFFE24B4A)
    val Warm = Color(0xFFEF9F27)
    val Healthy = Color(0xFF1D9E75)

    // Verdict (blame bar + chips) — colour encodes verdict type, never rank
    val VerdictUnstable = Hot
    val VerdictLambda = Warm
    val VerdictUnchanged = Color(0xFF9A9287)

    // Cream-white surfaces
    val Surface = Color(0xFFFAF6EF)          // cream white
    val SurfaceRaised = Color(0xFFFFFDF9)
    val OnSurface = Color(0xFF2A2620)
    val OnSurfaceVariant = Color(0xFF6B6459)
    val Divider = Color(0xFFE7E0D3)
    val Outline = Color(0xFFD8CFC0)
    val Scrim = Color(0x33000000)
}
