package com.wassimbeltaief.loupe.runtime.overlay

import android.util.Log
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
 * (slot table) and exposes their footprints for the heatmap window.
 *
 * SLOT-TABLE ACCESS IS MAIN-THREAD ONLY — [sample] must be called from the main
 * thread. `LoupeHeatmapHost`'s LaunchedEffect drives exactly that.
 *
 * Note on instance identity: the tooling tree tells us *how many* instances of a
 * composable are on screen and where they are, but not which registry instance
 * (`compoundKeyHash`) each box is — Compose's compound hash folds in the runtime
 * group index, which the tooling tree does not expose. We therefore treat the N
 * most recently active instances of a key as the on-screen ones (N = live boxes),
 * then order them by first-composition time to pair with position-sorted boxes.
 * Keeping the most recent set means navigated-away instances disappear, at the cost
 * of exact box↔instance pairing and correctness for reversed/reordered layouts.
 *
 * The tooling-data API is explicitly "likely to change"; any failure degrades
 * gracefully (heatmap clears, the rest of Loupe keeps working).
 */
internal class HeatmapController(
    private val config: LoupeConfig,
    private val instances: () -> List<RecompositionHistory>,
    private val onInstanceGone: (instanceId: String) -> Unit = {},
) {
    /**
     * Live reference to the host's `LocalInspectionTables` set. It must NOT be
     * copied: subcompositions (every `LazyColumn`/`SubcomposeLayout` item) register
     * their `CompositionData` into that set *after* this call, as they compose.
     * Copying froze us to the root composition, which is why lazy-list content had
     * no heatmap border and was filtered out of the overlay list.
     */
    private var tables: MutableSet<CompositionData> = mutableSetOf()

    private val _boxes = MutableStateFlow<List<HeatmapBox>>(emptyList())
    val boxes: StateFlow<List<HeatmapBox>> = _boxes.asStateFlow()

    /**
     * Instance ids (`key#compoundKeyHash`) of tracked composables currently present
     * in the composition tree. `null` = not tracking (host not attached); empty =
     * tracking, nothing on screen. Drives per-instance filtering of the overlay list.
     */
    private val _liveInstanceIds = MutableStateFlow<Set<String>?>(null)
    val liveInstanceIds: StateFlow<Set<String>?> = _liveInstanceIds.asStateFlow()

    fun attach(newTables: MutableSet<CompositionData>) {
        tables = newTables
    }

    fun detach() {
        tables = mutableSetOf()
        _boxes.value = emptyList()
        _liveInstanceIds.value = null
    }

    /**
     * @param origin screen position of the app's compose root view, added to the
     *   window-relative tooling boxes so they align with the full-screen overlay.
     */
    @OptIn(UiToolingDataApi::class)
    fun sample(origin: IntOffset) {
        val histories = instances()
        val byKey = histories.groupBy { it.key }

        val raw = mutableListOf<HeatmapMatcher.RawBox>()
        try {
            // Same main thread as composition, so reading the live set is safe;
            // take a snapshot so registrations mid-iteration can't interfere.
            tables.toList().forEach { table ->
                HeatmapMatcher.collect(table.asTree(), raw)
            }
        } catch (_: Throwable) {
            // Tooling internals changed or table disposed — skip this tick, never crash
            return
        }

        // Group raw boxes by their resolved key, sorted by position (top-to-bottom,
        // left-to-right) for stable pairing with registry instances.
        val boxesByKey = mutableMapOf<String, MutableList<HeatmapMatcher.RawBox>>()
        raw.forEach { found ->
            val key = resolveKey(byKey.keys, found) ?: return@forEach
            boxesByKey.getOrPut(key) { mutableListOf() } += found
        }
        boxesByKey.values.forEach { list ->
            list.sortWith(compareBy({ it.rect.top }, { it.rect.left }))
        }

        // Instance pairing: an instance is "on screen" if it is one of the N most
        // recently active for its key (N = number of live boxes). Take those, then
        // order them by first-composition time so they line up with the
        // position-sorted boxes 1:1. This assumes composition order matches layout
        // order, which holds for most real UIs. When there are more boxes than
        // instances, excess boxes show the aggregate count.
        data class PairedBox(val raw: HeatmapMatcher.RawBox, val count: Int, val instanceId: String?)
        val pairedBoxes = mutableListOf<PairedBox>()
        val liveIds = mutableSetOf<String>()

        boxesByKey.forEach { (key, keyBoxes) ->
            val instances = byKey[key]
                ?.sortedByDescending { it.latestNs() }
                ?.take(keyBoxes.size)
                ?.sortedBy { it.earliestNs() }
                ?: emptyList()

            keyBoxes.forEachIndexed { i, rawBox ->
                val instance = instances.getOrNull(i)
                if (instance != null) {
                    pairedBoxes += PairedBox(rawBox, instance.totalRecompositions, instance.instanceId)
                    liveIds += instance.instanceId
                } else {
                    // More boxes than instances — fall back to aggregate (max) count
                    val aggregate = instances.maxOfOrNull { it.totalRecompositions } ?: 0
                    pairedBoxes += PairedBox(rawBox, aggregate, null)
                }
            }
        }

        val boxes = mutableListOf<HeatmapBox>()
        pairedBoxes.forEach { (rawBox, count, _) ->
            if (!config.heatmapEnabled) return@forEach
            boxes += HeatmapBox(
                key = resolveKey(byKey.keys, rawBox) ?: return@forEach,
                name = rawBox.name,
                rect = IntRect(
                    left = rawBox.rect.left + origin.x,
                    top = rawBox.rect.top + origin.y,
                    right = rawBox.rect.right + origin.x,
                    bottom = rawBox.rect.bottom + origin.y,
                ),
                count = count,
                severity = HeatmapMatcher.severityFor(
                    count,
                    config.hotThreshold,
                    config.warmThreshold,
                ),
            )
        }
        // Notify when instances disappear (navigation away) so registry can clear them
        val previousLive = _liveInstanceIds.value ?: emptySet()
        val disappeared = previousLive - liveIds
        disappeared.forEach { onInstanceGone(it) }

        _liveInstanceIds.value = liveIds
        if (config.heatmapEnabled) _boxes.value = boxes
        logDiagnostics(raw, boxes, byKey.keys)
    }

    private fun RecompositionHistory.latestNs(): Long =
        records.firstOrNull()?.timestampNs ?: Long.MIN_VALUE

    private fun RecompositionHistory.earliestNs(): Long =
        records.lastOrNull()?.timestampNs ?: Long.MAX_VALUE

    /** Key match first; fall back to the composable's simple name. */
    private fun resolveKey(keys: Set<String>, found: HeatmapMatcher.RawBox): String? {
        if (keys.contains(found.key)) return found.key
        return keys.firstOrNull { it.substringAfterLast('.') == found.name }
    }

    // Throttled diagnostics — heatmap issues are otherwise invisible (debug builds only)
    private var lastDiagnosticNs = 0L

    private fun logDiagnostics(
        raw: List<HeatmapMatcher.RawBox>,
        boxes: List<HeatmapBox>,
        keys: Set<String>,
    ) {
        val now = System.nanoTime()
        if (now - lastDiagnosticNs < 2_000_000_000L) return
        lastDiagnosticNs = now
        Log.d(
            "Loupe",
            "heatmap: tables=${tables.size} raw=${raw.size} matched=${boxes.size} " +
                "rawNames=${raw.map { it.name }.distinct().take(6)} " +
                "historyKeys=${keys.take(6)}",
        )
    }
}
