package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.LoupeRuntime
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import kotlinx.coroutines.flow.StateFlow

/**
 * Overlay orchestrator. Three states, each resizing the host window:
 *  - [OverlayState.Collapsed] — a full-width title bar pinned to the bottom
 *  - [OverlayState.Composables] — the composables sheet, up to 33% of the screen
 *  - [OverlayState.Detail] — fullscreen detail for one instance
 */
@Composable
internal fun LoupeOverlay(
    instancesFlow: StateFlow<List<RecompositionHistory>>,
    config: LoupeConfig,
    onWindowModeChange: (OverlayWindowMode) -> Unit,
) {
    val instances by instancesFlow.collectAsState()
    val onScreenIds by LoupeRuntime.onScreenInstanceIds.collectAsState()
    val isPaused by LoupeRuntime.isPaused.collectAsState()
    // Hide instances no longer composed (navigated away / scrolled off). The heatmap
    // reports live instance ids, so the detail screen no longer keeps the grid's rows.
    // null = not tracking (no heatmap host) → fall back to showing everything.
    val visibleInstances = onScreenIds?.let { ids ->
        instances.filter { it.instanceId in ids }
    } ?: instances
    var state by remember { mutableStateOf<OverlayState>(OverlayState.Collapsed) }

    // If the inspected instance disappears (reset/eviction), fall back to the list
    LaunchedEffect(state, instances) {
        val target = state as? OverlayState.Detail ?: return@LaunchedEffect
        if (instances.none { it.instanceId == target.instanceId }) state = OverlayState.Composables
    }

    LaunchedEffect(state) { onWindowModeChange(state.toWindowMode()) }

    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (slideInVertically { it } + fadeIn()) togetherWith
                (slideOutVertically { it } + fadeOut())
        },
        label = "loupe-overlay",
    ) { current ->
        when (current) {
            OverlayState.Collapsed -> CollapsedBar(
                count = visibleInstances.size,
                onExpand = { state = OverlayState.Composables },
            )

            OverlayState.Composables -> OverlayListPanel(
                instances = visibleInstances,
                config = config,
                isPaused = isPaused,
                onTogglePause = {
                    if (isPaused) LoupeRuntime.resume() else LoupeRuntime.pause()
                },
                onCollapse = { state = OverlayState.Collapsed },
                onSelect = { state = OverlayState.Detail(it.instanceId) },
            )

            is OverlayState.Detail -> instances
                .firstOrNull { it.instanceId == current.instanceId }
                ?.let { history ->
                    DrillDownPanel(
                        history = history,
                        config = config,
                        onBack = { state = OverlayState.Composables },
                    )
                }
        }
    }
}

/** Collapsed state: full-width title bar floating at the bottom with an expand affordance. */
@Composable
private fun CollapsedBar(count: Int, onExpand: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LoupeSheetInset)
            .loupeSheetSurface()
            .clickable(onClick = onExpand)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Loupe",
            color = LoupeColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count tracked",
            color = LoupeColors.OnSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "⌃",
            color = LoupeColors.OnSurface,
            fontSize = 16.sp,
        )
    }
}
