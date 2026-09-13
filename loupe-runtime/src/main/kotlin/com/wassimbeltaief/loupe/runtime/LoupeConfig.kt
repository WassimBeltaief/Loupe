package com.wassimbeltaief.loupe.runtime

data class LoupeConfig(
    val overlayEnabled: Boolean = true,
    val heatmapEnabled: Boolean = true,
    val windowSeconds: Int = 5,
    val maxHistoryEntries: Int = 500,
    val recordingEnabled: Boolean = true,
    val hotThreshold: Int = 16,
    val warmThreshold: Int = 4,
    val logcatEnabled: Boolean = false,
    val logcatVerbose: Boolean = false,
    val ignoreList: List<String> = listOf(
        "Cursor",
        "Blink*",
        "Transition*",
        "AnimatedVisibility*",
    ),
)
