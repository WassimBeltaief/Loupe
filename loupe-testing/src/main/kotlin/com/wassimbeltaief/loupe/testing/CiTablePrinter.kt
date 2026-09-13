package com.wassimbeltaief.loupe.testing

import com.wassimbeltaief.loupe.runtime.LoupeReport
import com.wassimbeltaief.loupe.runtime.model.CompositionHistory
import java.util.Locale

/**
 * Formats a [LoupeReport] as a CI-friendly table.
 *
 * No Android dependency — fully JVM-testable.
 *
 * Column layout:
 *   Composable (25) | Count (5) | Cost ms (8) | Top blame (21) | Status (6)
 */
object CiTablePrinter {

    private const val TOP = "┌─────────────────────────┬───────┬──────────┬───────────────────────┬────────┐"
    private const val HDR = "│ Composable              │ Count │ Cost(ms) │ Top blame             │ Status │"
    private const val MID = "├─────────────────────────┼───────┼──────────┼───────────────────────┼────────┤"
    private const val BOT = "└─────────────────────────┴───────┴──────────┴───────────────────────┴────────┘"

    fun format(report: LoupeReport): String = buildString {
        appendLine()
        appendLine("Loupe recomposition report  (${report.durationMs}ms)")
        appendLine(TOP)
        appendLine(HDR)
        appendLine(MID)
        report.composables.values
            .sortedByDescending { it.totalCompositions }
            .forEach { appendLine(row(it)) }
        appendLine(BOT)
    }

    private fun row(h: CompositionHistory): String {
        val recompositions = (h.totalCompositions - 1).coerceAtLeast(0)
        val status = when {
            recompositions >= 16 -> "HOT"
            recompositions >= 4  -> "WARM"
            else                 -> "OK"
        }
        val blame = h.blamedParams.firstOrNull()
            ?.let { "${it.name.take(10)} (${it.recompositionCount}x)" }
            ?: "-"
        return String.format(
            Locale.US,
            "│ %-23s │ %5d │ %8.1f │ %-21s │ %-6s │",
            h.key.take(23),
            h.totalCompositions,
            h.totalDurationMs,
            blame.take(21),
            status,
        )
    }
}
