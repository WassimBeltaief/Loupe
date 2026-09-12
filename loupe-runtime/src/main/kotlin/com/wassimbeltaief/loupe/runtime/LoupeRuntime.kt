package com.wassimbeltaief.loupe.runtime

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import com.wassimbeltaief.loupe.runtime.overlay.LoupeOverlayManager
import com.wassimbeltaief.loupe.runtime.registry.RecompositionRegistry
import kotlinx.coroutines.flow.StateFlow

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

    val state: StateFlow<Map<String, RecompositionHistory>> get() = registry.state

    fun install(application: Application, config: LoupeConfig = LoupeConfig()) {
        configure(config)
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
        registry.record(key, file, line, params)
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

    private fun String.matchesGlob(pattern: String): Boolean {
        if (!pattern.contains('*')) return this == pattern
        val regexStr = pattern.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex("^$regexStr$").matches(this)
    }
}
