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
import kotlin.time.Duration.Companion.milliseconds

/**
 * Turns on the heatmap by wrapping the app content once:
 *
 * ```
 * setContent {
 *     LoupeHeatmapHost {
 *         App()
 *     }
 * }
 * ```
 *
 * The heatmap needs the composition slot tables to know where each composable
 * is on screen. Compose only exposes them when it is asked to. This host does
 * that, and samples the tables on the main thread a few times per second.
 *
 * This is the only Loupe feature that needs a code change. Use it in debug
 * builds only.
 */
@Composable
fun LoupeHeatmapHost(content: @Composable () -> Unit) {
    val tables = remember { mutableSetOf<CompositionData>() }
    val view = LocalView.current
    // Ask Compose to collect source information, otherwise tooling groups have
    // no names and the heatmap cannot match them. This is what Compose's own
    // Inspectable() does. It must run before content().
    currentComposer.collectParameterInformation()
    // The root composition (the app itself). The CompositionLocal below only
    // reaches subcompositions, so the root is registered here by hand.
    val hostComposition = currentComposer.compositionData

    DisposableEffect(tables, view, hostComposition) {
        tables.add(hostComposition)
        LoupeRuntime.attachInspectionTables(tables, view)
        onDispose { LoupeRuntime.detachInspectionTables() }
    }

    // Slot tables may only be read on the main thread. A LaunchedEffect runs on
    // the composition dispatcher, so that is the right place to sample.
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
