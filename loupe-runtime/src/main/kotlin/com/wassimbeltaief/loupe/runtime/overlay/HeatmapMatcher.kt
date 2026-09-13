package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.ui.tooling.data.CallGroup
import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.unit.IntRect

internal enum class HeatmapSeverity { Hot, Warm, Healthy }

/** One matched composable footprint, in overlay/screen pixel coordinates. */
internal data class HeatmapBox(
    val key: String,
    val name: String,
    val rect: IntRect,
    /** Total recompositions (monotonic) — shown in the badge. */
    val count: Int,
    /** Cumulative recomposition band, drives the border colour (never cools down). */
    val severity: HeatmapSeverity,
)

/**
 * Maps Compose tooling data ([Group]) to Loupe keys and severity.
 * It has no Android or UI dependency, so the tree walk and the key resolution
 * can be tested on the JVM.
 */
@OptIn(UiToolingDataApi::class)
internal object HeatmapMatcher {

    /** A named call group found in the slot tree, before severity is attached. */
    data class RawBox(val key: String, val name: String, val rect: IntRect)

    /**
     * Builds a Loupe key, `FileName.Function`, from a tooling group name and its
     * source file, so it can be matched with the recorded history.
     */
    fun keyFor(name: String, sourceFile: String?): String? {
        if (name.isEmpty()) return null
        val fileBase = sourceFile?.substringBeforeLast('.')?.takeIf { it.isNotEmpty() } ?: return name
        return if (fileBase == name) name else "$fileBase.$name"
    }

    fun severityFor(count: Int, hotThreshold: Int, warmThreshold: Int): HeatmapSeverity = when {
        count >= hotThreshold -> HeatmapSeverity.Hot
        count >= warmThreshold -> HeatmapSeverity.Warm
        else -> HeatmapSeverity.Healthy
    }

    /**
     * Depth-first collection of named call groups with a non-empty footprint.
     * A `CallGroup`'s box is the union of its emitted nodes, so plain composables
     * (even without an explicit modifier) yield a usable bounding box.
     */
    fun collect(tree: Group, out: MutableList<RawBox> = mutableListOf()): List<RawBox> {
        visit(tree, out)
        return out
    }

    private fun visit(group: Group, out: MutableList<RawBox>) {
        if (group is CallGroup) {
            val name = group.name
            if (!name.isNullOrEmpty() && !group.box.isEmpty) {
                keyFor(name, group.location?.sourceFile)?.let { key ->
                    out += RawBox(key = key, name = name, rect = group.box)
                }
            }
        }
        group.children.forEach { visit(it, out) }
    }
}
