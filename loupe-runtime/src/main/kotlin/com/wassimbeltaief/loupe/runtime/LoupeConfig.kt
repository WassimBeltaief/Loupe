package com.wassimbeltaief.loupe.runtime

/**
 * All the knobs of Loupe, with safe defaults.
 *
 * Pass one to [LoupeRuntime.install] when the defaults are not what you need.
 */
data class LoupeConfig(
    /** Show the on-device overlay. */
    val overlayEnabled: Boolean = true,

    /** Draw heatmap borders over the composables on screen (needs [LoupeHeatmapHost]). */
    val heatmapEnabled: Boolean = true,

    /** How many seconds a recomposition count stays in the rolling window. */
    val windowSeconds: Int = 5,

    /** Maximum records kept per composable, newest first. */
    val maxHistoryEntries: Int = 500,

    /** Master switch for recording. When false, nothing is captured. */
    val recordingEnabled: Boolean = true,

    /** A composable is red at or above this many recompositions in the window. */
    val hotThreshold: Int = 16,

    /** A composable is amber at or above this many recompositions in the window. */
    val warmThreshold: Int = 4,

    /** Write a summary block to Logcat when a composable gets hot. */
    val logcatEnabled: Boolean = false,

    /** Also log every single recomposition. Use only for short sessions. */
    val logcatVerbose: Boolean = false,

    /** Composables that are never recorded. Supports `*` as a wildcard. */
    val ignoreList: List<String> = listOf(
        "Cursor",
        "Blink*",
        "Transition*",
        "AnimatedVisibility*",
    ),
)
