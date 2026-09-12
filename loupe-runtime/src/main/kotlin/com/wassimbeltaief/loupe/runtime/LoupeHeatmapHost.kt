package com.wassimbeltaief.loupe.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.LocalInspectionTables
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

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
 * It exposes the composition's slot tables to Loupe (via `LocalInspectionTables`)
 * and drives main-thread sampling. This is the **one** Loupe feature that requires
 * a code change — everything else is zero-instrumentation. Use in debug builds only.
 */
@Composable
fun LoupeHeatmapHost(content: @Composable () -> Unit) {
    val tables = remember { mutableSetOf<CompositionData>() }
    val view = LocalView.current

    DisposableEffect(tables, view) {
        LoupeRuntime.attachInspectionTables(tables, view)
        onDispose { LoupeRuntime.detachInspectionTables() }
    }

    // Slot-table access is main-thread only — LaunchedEffect runs on the app's
    // composition dispatcher (main), sampled at a low, cheap rate.
    LaunchedEffect(tables) {
        while (true) {
            LoupeRuntime.sampleHeatmap()
            delay(HEATMAP_SAMPLE_INTERVAL_MS.milliseconds)
        }
    }

    CompositionLocalProvider(LocalInspectionTables provides tables) {
        content()
    }
}

private const val HEATMAP_SAMPLE_INTERVAL_MS = 250L
