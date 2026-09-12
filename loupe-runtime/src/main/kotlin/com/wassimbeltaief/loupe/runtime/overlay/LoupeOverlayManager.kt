package com.wassimbeltaief.loupe.runtime.overlay

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
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
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var collapsedGravity: Int = Gravity.BOTTOM or Gravity.START
    private var drillDownOpen = false

    fun show(
        stateFlow: StateFlow<Map<String, RecompositionHistory>>,
        config: LoupeConfig,
    ) {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(application)) {
            Log.w("Loupe", "SYSTEM_ALERT_WINDOW permission not granted — overlay disabled. " +
                "Grant it via: adb shell appops set ${application.packageName} SYSTEM_ALERT_WINDOW allow")
            return
        }

        collapsedGravity = when (config.overlayPosition) {
            OverlayPosition.TopStart -> Gravity.TOP or Gravity.START
            OverlayPosition.TopEnd -> Gravity.TOP or Gravity.END
            OverlayPosition.BottomStart -> Gravity.BOTTOM or Gravity.START
            OverlayPosition.BottomEnd -> Gravity.BOTTOM or Gravity.END
        }

        val owner = OverlayLifecycleOwner().also {
            it.start()
            lifecycleOwner = it
        }

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

    fun dismiss() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        lifecycleOwner?.stop()
        lifecycleOwner = null
    }
}
