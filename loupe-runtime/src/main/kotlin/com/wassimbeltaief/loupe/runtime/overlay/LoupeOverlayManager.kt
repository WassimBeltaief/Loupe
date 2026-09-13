package com.wassimbeltaief.loupe.runtime.overlay

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the overlay windows: adds them to the WindowManager, resizes them when
 * the overlay state changes and removes them when the app goes to the
 * background.
 *
 * There are two windows. The heatmap window is full screen and ignores touches.
 * The panel window is interactive and sized to its content, so touches outside
 * it still reach the app.
 */
internal class LoupeOverlayManager(private val application: Application) {

    private val windowManager =
        application.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: ComposeView? = null
    private var heatmapView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var windowMode: OverlayWindowMode = OverlayWindowMode.Collapsed

    // Overlay windows need a user-granted permission. When it is missing, log the
    // exact adb command that grants it, then skip the overlay.
    private fun hasOverlayPermission(): Boolean {
        if (Settings.canDrawOverlays(application)) return true
        Log.w("Loupe", "SYSTEM_ALERT_WINDOW permission not granted — overlay disabled. " +
            "Grant it via: adb shell appops set ${application.packageName} SYSTEM_ALERT_WINDOW allow")
        return false
    }

    private fun ensureLifecycleOwner(): OverlayLifecycleOwner =
        lifecycleOwner ?: OverlayLifecycleOwner().also {
            it.start()
            lifecycleOwner = it
        }

    /** Adds the interactive overlay panel, collapsed at the bottom. Does nothing if already shown. */
    fun show(
        instancesFlow: StateFlow<List<RecompositionHistory>>,
        config: LoupeConfig,
    ) {
        if (overlayView != null) return
        if (!hasOverlayPermission()) return

        val owner = ensureLifecycleOwner()
        val view = ComposeView(application).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                LoupeOverlay(
                    instancesFlow = instancesFlow,
                    config = config,
                    onWindowModeChange = ::setWindowMode,
                )
            }
        }

        // Starts collapsed: a full-width title bar pinned to the bottom.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            x = 0
            y = 0
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    /**
     * Resizes the panel to match the current UI state. The window is sized to its
     * content, and it is not touch-modal, so touches outside it pass through to
     * the app below.
     */
    fun setWindowMode(mode: OverlayWindowMode) {
        if (windowMode == mode) return
        windowMode = mode
        val view = overlayView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        when (mode) {
            OverlayWindowMode.Collapsed -> {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            OverlayWindowMode.Sheet -> {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = listHeightPx()
            }
            OverlayWindowMode.Fullscreen -> {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.MATCH_PARENT
            }
        }
        params.gravity = Gravity.BOTTOM
        params.x = 0
        params.y = 0
        windowManager.updateViewLayout(view, params)
    }

    private fun listHeightPx(): Int =
        (application.resources.displayMetrics.heightPixels * LIST_HEIGHT_FRACTION).toInt()

    /**
     * Adds the full-screen, non-touchable window that draws the heatmap borders
     * and badges. It is added before the panel, so the panel stays on top.
     */
    fun showHeatmap(controller: HeatmapController) {
        if (heatmapView != null) return
        if (!hasOverlayPermission()) return

        val owner = ensureLifecycleOwner()
        val view = ComposeView(application).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val boxes by controller.boxes.collectAsState()
                HeatmapLayer(boxes = boxes)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(view, params)
        heatmapView = view
        Log.d("Loupe", "heatmap: window added")
    }

    /**
     * Screen position of the heatmap window's content view. Used to convert the
     * app-relative tooling boxes into overlay-relative coordinates (both windows
     * can differ in how system bars inset them).
     */
    fun heatmapScreenLocation(): IntArray? {
        val view = heatmapView ?: return null
        return IntArray(2).also { view.getLocationOnScreen(it) }
    }

    /** Removes both windows and stops the lifecycle owner. */
    fun dismiss() {
        heatmapView?.let { windowManager.removeView(it) }
        heatmapView = null
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        windowMode = OverlayWindowMode.Collapsed
        lifecycleOwner?.stop()
        lifecycleOwner = null
    }

    private companion object {
        /** Expanded list sheet occupies at most a third of the screen. */
        const val LIST_HEIGHT_FRACTION = 0.33f
    }
}
