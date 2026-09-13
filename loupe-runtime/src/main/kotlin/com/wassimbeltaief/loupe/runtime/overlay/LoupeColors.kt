package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.ui.graphics.Color

/**
 * The colours of the overlay.
 *
 * There are two systems, and they must not be mixed:
 * - Severity colours show how often something recomposes: red, amber or green.
 * - Verdict colours show the type of a change in the blame bar. Here the colour
 *   means "what kind of change", not "how bad it is". A lambda identity change
 *   is amber even when it caused many recompositions, because the cause may be
 *   legitimate.
 */
internal object LoupeColors {
    // Severity
    val Hot = Color(0xFFE24B4A)
    val Warm = Color(0xFFEF9F27)
    val Healthy = Color(0xFF1D9E75)

    // Verdict (blame bar and chips). Colour means type, not rank.
    val VerdictUnstable = Hot
    val VerdictLambda = Warm
    val VerdictUnchanged = Color(0xFF9A9287)

    // Cream-white surfaces
    val Surface = Color(0xFFFAF6EF)
    val SurfaceRaised = Color(0xFFFFFDF9)
    val OnSurface = Color(0xFF2A2620)
    val OnSurfaceVariant = Color(0xFF6B6459)
    val Divider = Color(0xFFE7E0D3)
    val Outline = Color(0xFFD8CFC0)
}
