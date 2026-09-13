package com.wassimbeltaief.loupe.runtime

import android.app.Application
import android.util.Log
import android.view.View
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import com.wassimbeltaief.loupe.runtime.overlay.HeatmapController
import com.wassimbeltaief.loupe.runtime.overlay.LoupeOverlayManager
import com.wassimbeltaief.loupe.runtime.registry.RecompositionRegistry
import com.wassimbeltaief.loupe.runtime.reporting.LogcatFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object LoupeRuntime {

    @Volatile private var config = LoupeConfig()
    @Volatile private var overlayDismissed = false
    @Volatile private var sessionStartMs = System.currentTimeMillis()

    private val _paused = MutableStateFlow(false)

    /** True while recording is paused — drives the overlay's play/pause control. */
    val isPaused: StateFlow<Boolean> = _paused.asStateFlow()

    private var registry = RecompositionRegistry(
        maxHistoryEntries = config.maxHistoryEntries,
        windowNs = config.windowSeconds * 1_000_000_000L,
    )

    private var overlayManager: LoupeOverlayManager? = null

    // #13 heatmap state
    private var heatmapController: HeatmapController? = null
    @Volatile private var heatmapContentView: View? = null

    // #23: per-key severity at last Logcat summary — summary is emitted only when
    // a composable crosses UP into warm/hot, so Logcat is never spammed per frame
    private val logcatSeverity = mutableMapOf<String, Int>()
    private val logcatScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<Map<String, RecompositionHistory>> get() = registry.state

    /** Per-instance histories — one row per on-screen instance in the overlay. */
    val instances: StateFlow<List<RecompositionHistory>> get() = registry.instances

    private val noLiveInstanceIds = MutableStateFlow<Set<String>?>(null)

    /**
     * Instance ids currently present in the composition tree, or null when not
     * tracking (heatmap host not attached). The overlay uses this to hide
     * instances that have scrolled/navigated away.
     */
    val onScreenInstanceIds: StateFlow<Set<String>?> get() =
        heatmapController?.liveInstanceIds ?: noLiveInstanceIds

    fun install(application: Application, config: LoupeConfig = LoupeConfig()) {
        configure(config)
        startLogcatReporter()
        if (config.overlayEnabled || config.heatmapEnabled) {
            val manager = LoupeOverlayManager(application)
            overlayManager = manager
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (overlayDismissed) return
                    val current = this@LoupeRuntime.config
                    // Heatmap window first so the interactive panel stays on top
                    if (current.heatmapEnabled) {
                        heatmapController?.let { manager.showHeatmap(it) }
                    }
                    if (current.overlayEnabled) {
                        manager.show(instancesFlow = registry.instances, config = current)
                    }
                }

                override fun onStop(owner: LifecycleOwner) {
                    manager.dismiss()
                }
            })
        }
    }

    // Called exclusively by compiler-injected code
    fun record(
        key: String,
        file: String,
        line: Int,
        params: Array<Pair<String, Any?>>,
        instance: Int = 0,
    ) {
        if (_paused.value || !config.recordingEnabled) return
        if (config.ignoreList.any { pattern -> key.matchesGlob(pattern) }) return
        val record = registry.record(key, file, line, params, instance)
        // #23 verbose mode: every individual recomposition with param changes
        if (config.logcatEnabled && config.logcatVerbose) {
            Log.d(LogcatFormatter.TAG, LogcatFormatter.verboseLine(record))
        }
    }

    // Called exclusively by compiler-injected code, from the finally block
    // wrapping the composable body (#36 duration measurement)
    fun recordEnd(key: String, instance: Int = 0) {
        registry.recordEnd(key, instance)
    }

    // Called exclusively by compiler-injected code, right after a local
    // `MutableState` is created. Captures internal state so `counter++` shows up
    // in the drill-down instead of only as a forced recomposition.
    fun trackState(key: String, instance: Int = 0, name: String, value: Any?) {
        if (_paused.value || !config.recordingEnabled) return
        registry.trackState(key, instance, name, value)
    }

    fun configure(config: LoupeConfig) {
        this.config = config
        sessionStartMs = System.currentTimeMillis()
        registry = RecompositionRegistry(
            maxHistoryEntries = config.maxHistoryEntries,
            windowNs = config.windowSeconds * 1_000_000_000L,
        )
        heatmapController = HeatmapController(
            config = config,
            instances = { registry.instances.value },
            onInstanceGone = { registry.clearInstance(it) },
        )
    }

    fun pause() { _paused.value = true }
    fun resume() { _paused.value = false }

    // Hides the overlay for the rest of the session (header ✕ button)
    fun dismissOverlay() {
        overlayDismissed = true
        overlayManager?.dismiss()
    }

    fun reset() {
        registry.reset()
        _paused.value = false
        synchronized(logcatSeverity) { logcatSeverity.clear() }
    }

    // ── #13 heatmap host API — driven by LoupeHeatmapHost on the main thread ──

    internal fun attachInspectionTables(tables: MutableSet<CompositionData>, contentView: View) {
        heatmapContentView = contentView
        heatmapController?.attach(tables)
        Log.d("Loupe", "heatmap: host attached (tables=${tables.size}, view=${contentView.javaClass.simpleName})")
    }

    internal fun detachInspectionTables() {
        heatmapController?.detach()
        heatmapContentView = null
    }

    /**
     * Main-thread sampling tick. Tooling boxes are relative to the app's compose
     * view; translate by the DELTA between that view's screen position and the
     * heatmap window's screen position. Using the delta (not the app position
     * alone) avoids double-counting system-bar insets, which both windows apply
     * independently — the bug that made borders drift down by the status bar.
     */
    internal fun sampleHeatmap() {
        val view = heatmapContentView ?: return
        val controller = heatmapController ?: return
        val contentLocation = IntArray(2)
        view.getLocationOnScreen(contentLocation)
        val overlayLocation = overlayManager?.heatmapScreenLocation()
        val origin = if (overlayLocation != null) {
            IntOffset(
                contentLocation[0] - overlayLocation[0],
                contentLocation[1] - overlayLocation[1],
            )
        } else {
            IntOffset(contentLocation[0], contentLocation[1])
        }
        controller.sample(origin)
    }

    fun snapshot(): LoupeReport {
        val snap = registry.snapshot()
        val hot = snap.values.filter { it.windowRecompositions >= config.hotThreshold }
        val warm = snap.values.filter { it.windowRecompositions in config.warmThreshold until config.hotThreshold }
        return LoupeReport(
            durationMs = System.currentTimeMillis() - sessionStartMs,
            composables = snap,
            totalRecompositions = snap.values.sumOf { it.totalRecompositions },
            hotComposables = hot,
            warmComposables = warm,
        )
    }

    // Testing API — records while block runs, returns report
    fun record(block: () -> Unit): LoupeReport {
        reset()
        val start = System.currentTimeMillis()
        block()
        val duration = System.currentTimeMillis() - start
        val snap = registry.snapshot()
        val hot = snap.values.filter { it.windowRecompositions >= config.hotThreshold }
        val warm = snap.values.filter { it.windowRecompositions in config.warmThreshold until config.hotThreshold }
        return LoupeReport(
            durationMs = duration,
            composables = snap,
            totalRecompositions = snap.values.sumOf { it.totalRecompositions },
            hotComposables = hot,
            warmComposables = warm,
        )
    }

    // #23: emits the summary block when a composable crosses up into warm/hot
    private fun startLogcatReporter() {
        if (!config.logcatEnabled) return
        logcatScope.launch {
            registry.state.collect { composables ->
                for (history in composables.values) {
                    val severity = when {
                        history.windowRecompositions >= config.hotThreshold -> 2
                        history.windowRecompositions >= config.warmThreshold -> 1
                        else -> 0
                    }
                    val previous = logcatSeverity[history.key] ?: 0
                    if (severity > previous) {
                        LogcatFormatter.summaryLines(
                            history, config.windowSeconds, config.hotThreshold, config.warmThreshold
                        ).forEach { Log.d(LogcatFormatter.TAG, it) }
                    }
                    logcatSeverity[history.key] = severity
                }
            }
        }
    }

    private fun String.matchesGlob(pattern: String): Boolean {
        if (!pattern.contains('*')) return this == pattern
        val regexStr = pattern.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex("^$regexStr$").matches(this)
    }
}
