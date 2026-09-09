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

        val owner = OverlayLifecycleOwner().also {
            it.start()
            lifecycleOwner = it
        }

        val view = ComposeView(application).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { LoupeOverlay(stateFlow = stateFlow, config = config) }
        }

        val gravity = when (config.overlayPosition) {
            OverlayPosition.TopStart -> Gravity.TOP or Gravity.START
            OverlayPosition.TopEnd -> Gravity.TOP or Gravity.END
            OverlayPosition.BottomStart -> Gravity.BOTTOM or Gravity.START
            OverlayPosition.BottomEnd -> Gravity.BOTTOM or Gravity.END
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            x = 12
            y = 12
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    fun dismiss() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        lifecycleOwner?.stop()
        lifecycleOwner = null
    }
}
