package com.wassimbeltaief.loupe.runtime

import com.wassimbeltaief.loupe.runtime.registry.RecompositionRegistry
import kotlinx.coroutines.flow.StateFlow

object LoupeRuntime {

    @Volatile private var config = LoupeConfig()
    @Volatile private var paused = false

    private var registry = RecompositionRegistry(
        maxHistoryEntries = config.maxHistoryEntries,
        windowNs = config.windowSeconds * 1_000_000_000L,
    )

    val state: StateFlow<*> get() = registry.state

    // Called exclusively by compiler-injected code
    fun record(key: String, file: String, line: Int, params: Array<Pair<String, Any?>>) {
        if (paused || !config.recordingEnabled) return
        if (config.ignoreList.any { pattern -> key.matchesGlob(pattern) }) return
        registry.record(key, file, line, params)
    }

    fun configure(config: LoupeConfig) {
        this.config = config
        registry = RecompositionRegistry(
            maxHistoryEntries = config.maxHistoryEntries,
            windowNs = config.windowSeconds * 1_000_000_000L,
        )
    }

    fun pause() { paused = true }
    fun resume() { paused = false }

    fun reset() {
        registry.reset()
        paused = false
    }

    fun snapshot(): LoupeReport {
        val snap = registry.snapshot()
        val hot = snap.values.filter { it.windowRecompositions >= config.hotThreshold }
        val warm = snap.values.filter { it.windowRecompositions in config.warmThreshold until config.hotThreshold }
        return LoupeReport(
            durationMs = 0L,
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
