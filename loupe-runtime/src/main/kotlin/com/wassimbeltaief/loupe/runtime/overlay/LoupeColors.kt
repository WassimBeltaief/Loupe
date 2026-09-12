package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.ui.graphics.Color

/**
 * Shared visual grammar (CLAUDE.md "Visual Design Language"). One source so the
 * list panel, drill-down, heatmap and future surfaces read as one tool.
 */
internal object LoupeColors {
    // Severity (counts within the rolling window)
    val Hot = Color(0xFFE24B4A)
    val Warm = Color(0xFFEF9F27)
    val Healthy = Color(0xFF1D9E75)

    // Verdict (blame bar + chips) — colour encodes verdict type, never rank
    val VerdictUnstable = Hot          // genuine unstable value change
    val VerdictLambda = Warm           // lambda identity — ambiguous, never red
    val VerdictUnchanged = Color(0xFFC4C4C4)

    // Surface
    val Surface = Color(0xF0121212)
    val OnSurface = Color(0xFFEEEEEE)
    val Divider = Color(0xFF2A2A2A)
}
