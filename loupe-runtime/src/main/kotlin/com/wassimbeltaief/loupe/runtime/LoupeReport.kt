package com.wassimbeltaief.loupe.runtime

import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory

/**
 * A snapshot of everything Loupe recorded during one test.
 *
 * Use it to enforce recomposition budgets in CI. Each `assert...` method throws
 * an [AssertionError] with a clear message when the budget is broken, which
 * makes a failing build point straight at the problem.
 *
 * Get one from `LoupeRuntime.record { ... }` or `LoupeRuntime.snapshot()`.
 */
data class LoupeReport(
    val durationMs: Long,
    /** One entry per composable function, keyed by `File.Function`. */
    val composables: Map<String, RecompositionHistory>,
    val totalRecompositions: Int,
    val hotComposables: List<RecompositionHistory>,
    val warmComposables: List<RecompositionHistory>,
) {

    /** Fails when any composable recomposed more than [maxCount] times. */
    fun assertMaxRecompositions(maxCount: Int) {
        val violations = composables.values.filter { it.totalRecompositions > maxCount }
        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { "  ${it.key}: ${it.totalRecompositions} recompositions (threshold: $maxCount)" }
            throw AssertionError("assertMaxRecompositions($maxCount) failed\n$detail")
        }
    }

    /** Fails when any composable spent more than [maxMs] milliseconds in total. */
    fun assertMaxRecompositionCost(maxMs: Float) {
        val violations = composables.values.filter { it.totalDurationMs > maxMs }
        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { "  ${it.key}: ${"%.1f".format(java.util.Locale.US, it.totalDurationMs)}ms (threshold: ${maxMs}ms)" }
            throw AssertionError("assertMaxRecompositionCost(${maxMs}ms) failed\n$detail")
        }
    }

    /**
     * Fails when [composableName] recomposed more than once, so it never skipped.
     * This is the check for "this composable should be stable".
     */
    fun assertStable(composableName: String) {
        val history = composables[composableName]
            ?: throw AssertionError("assertStable: composable '$composableName' was never recorded")
        if (history.totalRecompositions > 1) {
            throw AssertionError("assertStable('$composableName') failed: recomposed ${history.totalRecompositions} times")
        }
    }

    /** Fails when any lambda parameter of [composableName] is blamed. */
    fun assertNoBlamedLambdas(composableName: String) {
        val history = composables[composableName]
            ?: throw AssertionError("assertNoBlamedLambdas: composable '$composableName' was never recorded")
        val lambdaParams = history.blamedParams.filter { it.dominantVerdict == ParamVerdict.LambdaIdentity }
        if (lambdaParams.isNotEmpty()) {
            val names = lambdaParams.joinToString { it.name }
            throw AssertionError("assertNoBlamedLambdas('$composableName') failed: lambda params blamed: $names")
        }
    }
}
