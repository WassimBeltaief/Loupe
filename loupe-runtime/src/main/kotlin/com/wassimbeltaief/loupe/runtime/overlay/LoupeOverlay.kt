package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.LoupeRuntime
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun LoupeOverlay(
    stateFlow: StateFlow<Map<String, RecompositionHistory>>,
    config: LoupeConfig,
    // Notifies the window manager to switch between corner-card and bottom-sheet layouts
    onModeChange: (drillDownOpen: Boolean) -> Unit = {},
) {
    val composables by stateFlow.collectAsState()
    var inspectedKey by remember { mutableStateOf<String?>(null) }
    val inspected = inspectedKey?.let { composables[it] }
    // Keep the last non-null history so the exit animation has content to render
    var lastInspected by remember { mutableStateOf<RecompositionHistory?>(null) }
    if (inspected != null) lastInspected = inspected

    // Auto-close if the inspected composable disappears (reset(), buffer eviction…)
    LaunchedEffect(inspectedKey, composables) {
        val key = inspectedKey
        if (key != null && composables[key] == null) inspectedKey = null
    }

    val drillDownOpen = inspected != null
    LaunchedEffect(drillDownOpen) { onModeChange(drillDownOpen) }

    Box {
        if (!drillDownOpen) {
            OverlayListPanel(
                composables = composables,
                config = config,
                onPause = LoupeRuntime::pause,
                onDismiss = LoupeRuntime::dismissOverlay,
                onInspect = { inspectedKey = it.key },
            )
        }
        AnimatedVisibility(
            visible = drillDownOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            lastInspected?.let { history ->
                DrillDownPanel(history = history, onClose = { inspectedKey = null })
            }
        }
    }
}
