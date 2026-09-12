package com.wassimbeltaief.loupe.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.LocalInspectionTables
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/**
 * Opt-in host for the on-device heatmap (#13). Wrap your app content once:
 *
 * ```
 * setContent {
 *     LoupeHeatmapHost {
 *         App()
 *     }
 * }
 * ```
 *
 * It exposes the composition's slot tables to Loupe and drives main-thread
 * sampling. This is the **one** Loupe feature that requires a code change —
 * everything else is zero-instrumentation. Use in debug builds only.
 */
@Composable
fun LoupeHeatmapHost(content: @Composable () -> Unit) {
    val tables = remember { mutableSetOf<CompositionData>() }
    val view = LocalView.current
    // The composition this host belongs to (the app's root composition). The
    // CompositionLocal below only reaches SUBcompositions — the root must be
    // registered explicitly, exactly as Compose's own Inspectable() does.
    val hostComposition = currentComposer.compositionData

    DisposableEffect(tables, view, hostComposition) {
        tables.add(hostComposition)
        LoupeRuntime.attachInspectionTables(tables, view)
        onDispose { LoupeRuntime.detachInspectionTables() }
    }

    // Slot-table access is main-thread only — LaunchedEffect runs on the app's
    // composition dispatcher (main), sampled at a low, cheap rate.
    LaunchedEffect(tables) {
        while (true) {
            LoupeRuntime.sampleHeatmap()
            delay(HEATMAP_SAMPLE_INTERVAL_MS)
        }
    }

    CompositionLocalProvider(LocalInspectionTables provides tables) {
        content()
    }
}

private const val HEATMAP_SAMPLE_INTERVAL_MS = 250L
