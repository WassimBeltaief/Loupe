package com.wassimbeltaief.loupe.runtime.overlay

/** Window sizing requested by the overlay UI. */
internal enum class OverlayWindowMode { Collapsed, Sheet, Fullscreen }

/** UI state machine for the overlay. */
internal sealed interface OverlayState {
    /** Only the title bar, pinned to the bottom, full width. */
    data object Collapsed : OverlayState

    /** Composables list sheet, up to 33% of the screen, full width. */
    data object Composables : OverlayState

    /** Fullscreen detail for one composable instance. */
    data class Detail(val instanceId: String) : OverlayState

    fun toWindowMode(): OverlayWindowMode = when (this) {
        Collapsed -> OverlayWindowMode.Collapsed
        Composables -> OverlayWindowMode.Sheet
        is Detail -> OverlayWindowMode.Fullscreen
    }
}
