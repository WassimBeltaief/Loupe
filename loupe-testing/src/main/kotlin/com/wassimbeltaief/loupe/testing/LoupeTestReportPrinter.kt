package com.wassimbeltaief.loupe.testing

import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import java.util.Locale

/**
 * Renders the end-of-run Loupe test report.
 *
 * It is a pure object with no Android dependency, so every line can be tested on
 * the JVM. It deliberately never prints the "every composable, count, cost"
 * table: a failure shows only the composable the test was about, its blame and
 * its recomposition cost.
 */
internal object LoupeTestReportPrinter {

    private const val DOUBLE_RULE = "═══════════════════════════════════════════════════════════════"
    private const val RULE = "───────────────────────────────────────────────────────────────"

    /** One failed test, with the Loupe data behind it. */
    internal data class Failure(
        val testName: String,
        val expected: String?,
        val actual: String?,
        val composableKey: String?,
        val blamedParams: List<BlamedParam>,
        val totalCompositions: Int,
        val windowRecompositions: Int,
        val totalDurationMs: Float,
        val hotThreshold: Int = 16,
        val warmThreshold: Int = 4,
    ) {
        /** A short, human description for tests that failed outside the Loupe DSL. */
        val summary: String
            get() = when {
                expected != null && actual != null -> "expected $expected, got $actual"
                actual != null -> actual
                else -> "test failed"
            }
    }

    internal data class RunReport(
        val total: Int,
        val passed: Int,
        val failed: Int,
        val skipped: Int,
        val durationMs: Long,
        val failures: List<Failure>,
    )

    fun format(report: RunReport): String = buildString {
        appendLine()
        appendLine(DOUBLE_RULE)
        appendLine(
            "Loupe test report   ${report.total} tests · ${report.passed} passed · " +
                "${report.failed} failed · ${report.skipped} skipped · ${seconds(report.durationMs)}"
        )
        if (report.failures.isNotEmpty()) {
            appendLine(RULE)
            report.failures.forEach { appendLine(failureBlock(it)) }
        }
        appendLine(DOUBLE_RULE)
    }

    private fun failureBlock(f: Failure): String = buildString {
        appendLine("FAIL  ${f.testName}")
        appendLine("      ${f.summary}")
        if (f.composableKey != null) {
            appendLine("      composable: ${f.composableKey}  [${statusOf(f)}]")
            val blame = f.blamedParams
                .filter { it.recompositionCount > 0 }
                .joinToString(", ") { "${it.name} (${it.recompositionCount}×)" }
                .ifEmpty { "—" }
            appendLine("      blame: $blame")
            appendLine(
                "      compositions: ${f.totalCompositions} total · " +
                    "${f.windowRecompositions} in window · ${ms(f.totalDurationMs)}"
            )
        }
    }.trimEnd()

    private fun statusOf(f: Failure): String = when {
        f.windowRecompositions >= f.hotThreshold -> "HOT"
        f.windowRecompositions >= f.warmThreshold -> "WARM"
        else -> "OK"
    }

    private fun seconds(valueMs: Long): String =
        "%.1fs".format(Locale.US, valueMs / 1000f)

    private fun ms(valueMs: Float): String =
        "%.1fms".format(Locale.US, valueMs)
}
