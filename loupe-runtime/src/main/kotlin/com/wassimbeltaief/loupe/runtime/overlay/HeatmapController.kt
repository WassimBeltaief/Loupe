package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.ui.geometry.Rect

// Tracks on-screen bounds per composable key for heatmap border rendering.
// Populated by Modifier.onGloballyPositioned injected by the compiler plugin (future issue).
internal object HeatmapController {
    private val bounds = mutableMapOf<String, Rect>()

    fun updateBounds(key: String, rect: Rect) {
        bounds[key] = rect
    }

    fun getBounds(key: String): Rect? = bounds[key]

    fun clear() = bounds.clear()
}
