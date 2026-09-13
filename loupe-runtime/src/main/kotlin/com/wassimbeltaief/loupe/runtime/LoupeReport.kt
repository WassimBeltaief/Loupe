package com.wassimbeltaief.loupe.runtime

import com.wassimbeltaief.loupe.runtime.model.CompositionHistory

/**
 * A snapshot of everything Loupe recorded during a test.
 *
 * Get one from [LoupeRuntime.snapshot] or [LoupeRuntime.record].
 * Use the `loupe-testing` artifact for assertion DSL methods on composable nodes.
 */
data class LoupeReport(
    val durationMs: Long,
    /** One entry per composable function, keyed by `File.Function`. */
    val composables: Map<String, CompositionHistory>,
    val totalCompositions: Int,
    val hotComposables: List<CompositionHistory>,
    val warmComposables: List<CompositionHistory>,
    /** Per-instance histories for composables that carry a `Modifier.testTag`. Keyed by the tag string. */
    val instances: Map<String, CompositionHistory> = emptyMap(),
)
