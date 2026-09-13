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
import java.lang.ref.WeakReference

/**
 * The entry point of Loupe and the place where all runtime state lives.
 *
 * During normal use you only call [install] once, and the overlay does the rest.
 * The `record`, `recordEnd` and `trackState` functions are not for you: the
 * compiler plugin injects those calls into your composables.
 *
 * The other public functions are for tests and for programmatic control:
 * [pause], [resume], [reset], [snapshot] and `record { }`.
 */
object LoupeRuntime {

    @Volatile private var config = LoupeConfig()
    @Volatile private var sessionStartMs = System.currentTimeMillis()

    private val _paused = MutableStateFlow(false)

    /** True while recording is paused — drives the overlay's play/pause control. */
    val isPaused: StateFlow<Boolean> = _paused.asStateFlow()

    private var registry = RecompositionRegistry(
        maxHistoryEntries = config.maxHistoryEntries,
        windowNs = config.windowSeconds * 1_000_000_000L,
    )

    private var overlayManager: LoupeOverlayManager? = null

    // Heatmap state
    private var heatmapController: HeatmapController? = null

    // Held weakly. LoupeRuntime is a singleton, so a strong reference to the app's
    // view would keep its Activity alive after it is destroyed.
    @Volatile private var heatmapContentView: WeakReference<View>? = null

    // Per-key severity at the last Logcat summary. A summary is only emitted when
    // a composable crosses up into warm or hot, so Logcat is not flooded.
    private val logcatSeverity = mutableMapOf<String, Int>()
    private val logcatScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Aggregated histories, one per composable function, keyed by `File.Function`. */
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

    /**
     * Starts Loupe. Call this once from your `Application.onCreate()`.
     *
     * It applies [config], starts the Logcat reporter (when enabled) and shows
     * the overlay while the app is in the foreground. It is a no-op for the
     * overlay when the app does not have the overlay permission.
     */
    fun install(application: Application, config: LoupeConfig = LoupeConfig()) {
        configure(config)
        startLogcatReporter()
        if (config.overlayEnabled || config.heatmapEnabled) {
            val manager = LoupeOverlayManager(application)
            overlayManager = manager
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
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

    /**
     * Called by compiler-injected code at the start of a composable body.
     * Not part of the public API.
     */
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
        // Verbose mode logs every single recomposition.
        if (config.logcatEnabled && config.logcatVerbose) {
            Log.d(LogcatFormatter.TAG, LogcatFormatter.verboseLine(record))
        }
    }

    /**
     * Called by compiler-injected code in the `finally` block that wraps the
     * composable body. It closes the record and stores the measured duration.
     * Not part of the public API.
     */
    fun recordEnd(key: String, instance: Int = 0) {
        registry.recordEnd(key, instance)
    }

    /**
     * Called by compiler-injected code right after a local `MutableState` is
     * created. It lets the drill-down show a state change such as `counter 0 -> 1`
     * instead of a forced recomposition with no visible cause.
     * Not part of the public API.
     */
    fun trackState(key: String, instance: Int = 0, name: String, value: Any?) {
        if (_paused.value || !config.recordingEnabled) return
        registry.trackState(key, instance, name, value)
    }

    /**
     * Applies a new config and starts a fresh recording session.
     * [install] calls this for you.
     */
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

    /** Stops recording. The overlay stays visible and keeps showing what it has. */
    fun pause() { _paused.value = true }

    /** Resumes recording after [pause]. */
    fun resume() { _paused.value = false }

    /** Clears all history and counts, and resumes recording. */
    fun reset() {
        registry.reset()
        _paused.value = false
        synchronized(logcatSeverity) { logcatSeverity.clear() }
    }

    // ── Heatmap host API. Driven by LoupeHeatmapHost, on the main thread. ────

    internal fun attachInspectionTables(tables: MutableSet<CompositionData>, contentView: View) {
        heatmapContentView = WeakReference(contentView)
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
        val view = heatmapContentView?.get() ?: return
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

    /** Returns the current state as an immutable [LoupeReport]. */
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

    /**
     * Records while [block] runs, then returns the report.
     *
     * History is cleared first, so the report only contains what happened inside
     * the block. This is the main entry point for the CI testing API.
     */
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

    // Emits the summary block when a composable becomes warm or hot.
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

    /** Simple name match, where `*` matches any part of the name. */
    private fun String.matchesGlob(pattern: String): Boolean {
        if (!pattern.contains('*')) return this == pattern
        val regexStr = pattern.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex("^$regexStr$").matches(this)
    }
}
