package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.LoupeRuntime
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun LoupeOverlay(
    stateFlow: StateFlow<Map<String, RecompositionHistory>>,
    config: LoupeConfig,
) {
    val composables by stateFlow.collectAsState()
    OverlayListPanel(
        composables = composables,
        config = config,
        onPause = LoupeRuntime::pause,
        onDismiss = {},
    )
}
