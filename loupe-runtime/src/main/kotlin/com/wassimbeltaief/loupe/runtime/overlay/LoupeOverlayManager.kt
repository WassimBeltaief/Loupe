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
import com.wassimbeltaief.loupe.runtime.OverlayPosition
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import kotlinx.coroutines.flow.StateFlow

internal class LoupeOverlayManager(private val application: Application) {

    private val windowManager =
        application.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: ComposeView? = null
    private var heatmapView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var collapsedGravity: Int = Gravity.BOTTOM or Gravity.START
    private var drillDownOpen = false

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

    fun show(
        stateFlow: StateFlow<Map<String, RecompositionHistory>>,
        config: LoupeConfig,
    ) {
        if (overlayView != null) return
        if (!hasOverlayPermission()) return

        collapsedGravity = when (config.overlayPosition) {
            OverlayPosition.TopStart -> Gravity.TOP or Gravity.START
            OverlayPosition.TopEnd -> Gravity.TOP or Gravity.END
            OverlayPosition.BottomStart -> Gravity.BOTTOM or Gravity.START
            OverlayPosition.BottomEnd -> Gravity.BOTTOM or Gravity.END
        }

        val owner = ensureLifecycleOwner()

        val view = ComposeView(application).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                LoupeOverlay(
                    stateFlow = stateFlow,
                    config = config,
                    onModeChange = ::setDrillDownMode,
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = collapsedGravity
            x = 12
            y = 12
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    /**
     * #13: full-screen, non-touchable window that draws heatmap borders + badges.
     * Separate from [overlayView] because one Android window cannot be
     * simultaneously touch-through in some regions and interactive in others.
     * Added after the panel, so the panel stays on top.
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
     * #18: switches the overlay window between the collapsed corner card and the
     * full-width bottom sheet (drill-down). Touches outside the sheet still pass
     * through to the app (FLAG_NOT_TOUCH_MODAL) — the sheet never closes on
     * outside tap, per spec.
     */
    fun setDrillDownMode(open: Boolean) {
        if (drillDownOpen == open) return
        drillDownOpen = open
        val view = overlayView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        if (open) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.BOTTOM
            params.x = 0
            params.y = 0
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = collapsedGravity
            params.x = 12
            params.y = 12
        }
        windowManager.updateViewLayout(view, params)
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

    fun dismiss() {
        heatmapView?.let { windowManager.removeView(it) }
        heatmapView = null
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        lifecycleOwner?.stop()
        lifecycleOwner = null
    }
}
