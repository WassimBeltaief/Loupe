package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.tooling.data.asTree
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Locates tracked composables on screen by sampling the Compose tooling data
 * (slot table) and exposes their footprints for the heatmap window (#13).
 *
 * SLOT-TABLE ACCESS IS MAIN-THREAD ONLY — [sample] must be called from the main
 * thread. `LoupeHeatmapHost`'s LaunchedEffect drives exactly that.
 *
 * The tooling-data API is explicitly "likely to change"; any failure degrades
 * gracefully (heatmap clears, the rest of Loupe keeps working).
 */
internal class HeatmapController(
    private val config: LoupeConfig,
    private val histories: () -> Map<String, RecompositionHistory>,
) {
    private val tables = mutableSetOf<CompositionData>()

    private val _boxes = MutableStateFlow<List<HeatmapBox>>(emptyList())
    val boxes: StateFlow<List<HeatmapBox>> = _boxes.asStateFlow()

    fun attach(newTables: MutableSet<CompositionData>) {
        synchronized(tables) { tables.addAll(newTables) }
    }

    fun detach() {
        synchronized(tables) { tables.clear() }
        _boxes.value = emptyList()
    }

    /**
     * @param origin screen position of the app's compose root view, added to the
     *   window-relative tooling boxes so they align with the full-screen overlay.
     */
    @OptIn(UiToolingDataApi::class)
    fun sample(origin: IntOffset) {
        if (!config.heatmapEnabled) return

        val snapshot = histories()
        if (snapshot.isEmpty()) {
            if (_boxes.value.isNotEmpty()) _boxes.value = emptyList()
            return
        }

        val raw = mutableListOf<HeatmapMatcher.RawBox>()
        try {
            synchronized(tables) { tables.toList() }.forEach { table ->
                HeatmapMatcher.collect(table.asTree(), raw)
            }
        } catch (_: Throwable) {
            // Tooling internals changed or table disposed — skip this tick, never crash
            return
        }

        _boxes.value = raw.mapNotNull { found ->
            val history = snapshot[found.key] ?: return@mapNotNull null
            HeatmapBox(
                key = found.key,
                name = found.name,
                rect = IntRect(
                    left = found.rect.left + origin.x,
                    top = found.rect.top + origin.y,
                    right = found.rect.right + origin.x,
                    bottom = found.rect.bottom + origin.y,
                ),
                count = history.windowRecompositions,
                severity = HeatmapMatcher.severityFor(
                    history.windowRecompositions,
                    config.hotThreshold,
                    config.warmThreshold,
                ),
            )
        }
    }
}
