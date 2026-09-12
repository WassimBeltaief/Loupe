package com.wassimbeltaief.loupe.runtime

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import com.wassimbeltaief.loupe.runtime.overlay.LoupeOverlayManager
import com.wassimbeltaief.loupe.runtime.registry.RecompositionRegistry
import com.wassimbeltaief.loupe.runtime.reporting.LogcatFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object LoupeRuntime {

    @Volatile private var config = LoupeConfig()
    @Volatile private var paused = false
    @Volatile private var overlayDismissed = false
    @Volatile private var sessionStartMs = System.currentTimeMillis()

    private var registry = RecompositionRegistry(
        maxHistoryEntries = config.maxHistoryEntries,
        windowNs = config.windowSeconds * 1_000_000_000L,
    )

    private var overlayManager: LoupeOverlayManager? = null

    // #23: per-key severity at last Logcat summary — summary is emitted only when
    // a composable crosses UP into warm/hot, so Logcat is never spammed per frame
    private val logcatSeverity = mutableMapOf<String, Int>()
    private val logcatScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<Map<String, RecompositionHistory>> get() = registry.state

    fun install(application: Application, config: LoupeConfig = LoupeConfig()) {
        configure(config)
        startLogcatReporter()
        if (config.overlayEnabled) {
            val manager = LoupeOverlayManager(application)
            overlayManager = manager
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (!overlayDismissed) {
                        manager.show(stateFlow = registry.state, config = this@LoupeRuntime.config)
                    }
                }
                override fun onStop(owner: LifecycleOwner) {
                    manager.dismiss()
                }
            })
        }
    }

    // Called exclusively by compiler-injected code
    fun record(key: String, file: String, line: Int, params: Array<Pair<String, Any?>>) {
        if (paused || !config.recordingEnabled) return
        if (config.ignoreList.any { pattern -> key.matchesGlob(pattern) }) return
        val record = registry.record(key, file, line, params)
        // #23 verbose mode: every individual recomposition with param changes
        if (config.logcatEnabled && config.logcatVerbose) {
            Log.d(LogcatFormatter.TAG, LogcatFormatter.verboseLine(record))
        }
    }

    // Called exclusively by compiler-injected code, from the finally block
    // wrapping the composable body (#36 duration measurement)
    fun recordEnd(key: String) {
        registry.recordEnd(key)
    }

    fun configure(config: LoupeConfig) {
        this.config = config
        sessionStartMs = System.currentTimeMillis()
        registry = RecompositionRegistry(
            maxHistoryEntries = config.maxHistoryEntries,
            windowNs = config.windowSeconds * 1_000_000_000L,
        )
    }

    fun pause() { paused = true }
    fun resume() { paused = false }

    // Hides the overlay for the rest of the session (header ✕ button)
    fun dismissOverlay() {
        overlayDismissed = true
        overlayManager?.dismiss()
    }

    fun reset() {
        registry.reset()
        paused = false
        synchronized(logcatSeverity) { logcatSeverity.clear() }
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
