package com.wassimbeltaief.loupe.runtime.reporting

import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import com.wassimbeltaief.loupe.runtime.model.RecompositionRecord

/**
 * Formats the Logcat output.
 *
 * It is a pure object, with no `android.util.Log` dependency, so every line can
 * be tested on the JVM. The emoji prefix uses the same severity grammar as the
 * overlay: red is hot, orange is warm, green is healthy.
 */
internal object LogcatFormatter {

    const val TAG = "Loupe"
    private const val RULE = "════════════════════════════════════════════"

    /**
     * The summary block for one composable. It is only emitted when a composable
     * becomes warm or hot, so Logcat is not flooded.
     */
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

    /** One line for a single recomposition. Used by the verbose Logcat mode. */
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
