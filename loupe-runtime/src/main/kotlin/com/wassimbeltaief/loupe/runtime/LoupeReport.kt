package com.wassimbeltaief.loupe.runtime

import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory

data class LoupeReport(
    val durationMs: Long,
    val composables: Map<String, RecompositionHistory>,
    val totalRecompositions: Int,
    val hotComposables: List<RecompositionHistory>,
    val warmComposables: List<RecompositionHistory>,
) {
    fun assertMaxRecompositions(maxCount: Int) {
        val violations = composables.values.filter { it.totalRecompositions > maxCount }
        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { "  ${it.key}: ${it.totalRecompositions} recompositions (threshold: $maxCount)" }
            throw AssertionError("assertMaxRecompositions($maxCount) failed\n$detail")
        }
    }

    fun assertMaxRecompositionCost(maxMs: Float) {
        val violations = composables.values.filter { it.totalDurationMs > maxMs }
        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { "  ${it.key}: ${"%.1f".format(java.util.Locale.US, it.totalDurationMs)}ms (threshold: ${maxMs}ms)" }
            throw AssertionError("assertMaxRecompositionCost(${maxMs}ms) failed\n$detail")
        }
    }

    fun assertStable(composableName: String) {
        val history = composables[composableName]
            ?: throw AssertionError("assertStable: composable '$composableName' was never recorded")
        if (history.totalRecompositions > 1) {
            throw AssertionError("assertStable('$composableName') failed: recomposed ${history.totalRecompositions} times")
        }
    }

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
