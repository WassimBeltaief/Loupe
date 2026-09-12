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
            val detail = violations.joinToString("\n") { "  ${it.key}: ${"%.1f".format(it.totalDurationMs)}ms (threshold: ${maxMs}ms)" }
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

    fun toJson(): String = buildString {
        append("{")
        append("\"durationMs\":$durationMs,")
        append("\"totalRecompositions\":$totalRecompositions,")
        append("\"composables\":{")
        composables.entries.forEachIndexed { i, (key, h) ->
            if (i > 0) append(",")
            append("\"$key\":{")
            append("\"file\":\"${h.file}\",")
            append("\"line\":${h.line},")
            append("\"totalRecompositions\":${h.totalRecompositions},")
            append("\"windowRecompositions\":${h.windowRecompositions},")
            append("\"totalDurationMs\":${h.totalDurationMs},")
            append("\"blamedParams\":[")
            h.blamedParams.forEachIndexed { j, b ->
                if (j > 0) append(",")
                append("{\"name\":\"${b.name}\",\"count\":${b.recompositionCount},\"fraction\":${b.fraction},\"verdict\":\"${b.dominantVerdict::class.simpleName}\"}")
            }
            append("]}")
        }
        append("}}")
    }

    fun printSummary() {
        println("Loupe recomposition report  (${durationMs}ms)")
        println("%-30s %6s  %10s  %-28s %s".format("Composable", "Count", "Cost", "Top blame", "Status"))
        println("─".repeat(88))
        composables.values.sortedByDescending { it.totalRecompositions }.forEach { h ->
            val status = when {
                hotComposables.any { it.key == h.key } -> "HOT"
                warmComposables.any { it.key == h.key } -> "WARM"
                else -> "OK"
            }
            val topBlame = h.blamedParams.firstOrNull()
                ?.let { "${it.name} (${it.dominantVerdict::class.simpleName})" }
                ?: "—"
            println("%-30s %6d  %8.1fms  %-28s %s".format(h.key, h.totalRecompositions, h.totalDurationMs, topBlame, status))
        }
    }
}
