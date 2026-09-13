package com.wassimbeltaief.loupe.runtime.reporting

import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import com.wassimbeltaief.loupe.runtime.model.RecompositionRecord

/**
 * Formats Loupe output for Logcat (#23). Pure — no android.util.Log dependency —
 * so every line shape is unit-testable on the JVM. The emoji prefix encodes
 * severity, same grammar as the overlay.
 */
internal object LogcatFormatter {

    const val TAG = "Loupe"
    private const val RULE = "════════════════════════════════════════════"

    /** One summary block per composable (spec format). Only called for warm/hot. */
    fun summaryLines(
        history: RecompositionHistory,
        windowSeconds: Int,
        hotThreshold: Int = 16,
        warmThreshold: Int = 4,
    ): List<String> = buildList {
        val emoji = when {
            history.windowRecompositions >= hotThreshold -> "🔴"
            history.windowRecompositions >= warmThreshold -> "🟠"
            else -> "🟢"
        }
        add(RULE)
        add(
            "$emoji ${history.key}  ${history.windowRecompositions}x in ${windowSeconds}s  " +
                "(${"%.1f".format(java.util.Locale.US, history.totalDurationMs)}ms total)"
        )
        if (history.blamedParams.isNotEmpty()) {
            add("├─ blame:")
            history.blamedParams.forEach { add("│   ${blameLine(it)}") }
        }
        add(RULE)
    }

    /** One line per individual recomposition — logcatVerbose mode. */
    fun verboseLine(record: RecompositionRecord): String {
        val changedParams = record.params
            .filter { it.verdict == ParamVerdict.Changed || it.verdict == ParamVerdict.LambdaIdentity }
            .map { "${it.name}: ${it.previousValue} → ${it.currentValue}" }
        val changedState = record.stateChanges
            .filter { it.verdict == ParamVerdict.Changed }
            .map { "${it.name}(state): ${it.previousValue} → ${it.currentValue}" }
        val changed = (changedParams + changedState).joinToString()
        val forced = if (record.wasForced) "  [forced]" else ""
        return "${record.key}  recomposition$forced" +
            (if (changed.isNotEmpty()) "  |  $changed" else "  |  params unchanged")
    }

    private fun blameLine(param: BlamedParam): String {
        val verdict = when {
            param.isState -> "state change"
            param.recompositionCount == 0 -> "stable ✓"
            param.dominantVerdict == ParamVerdict.LambdaIdentity -> "lambda identity"
            param.suggestion != null -> "unstable"
            else -> "changed"
        }
        val suggestion = param.suggestion?.let { "  → ${it.take(60)}" } ?: ""
        return "%-12s %3dx  %-15s%s".format(param.name.take(12), param.recompositionCount, verdict, suggestion)
    }
}
