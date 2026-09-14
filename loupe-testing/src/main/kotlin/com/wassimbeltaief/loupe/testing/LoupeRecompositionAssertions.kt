package com.wassimbeltaief.loupe.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import com.wassimbeltaief.loupe.runtime.LoupeRuntime
import com.wassimbeltaief.loupe.runtime.model.CompositionHistory
import java.util.Locale

/**
 * Asserts that this composable was composed exactly once — never recomposed.
 *
 * Composition count includes the initial composition, so passing means
 * [CompositionHistory.totalCompositions] == 1.
 *
 * The node must carry a [Modifier.testTag][androidx.compose.ui.Modifier] whose value
 * matches the composable's function name (or its full `FileName.FunctionName` Loupe key).
 *
 * On failure it throws a [LoupeAssertionError] carrying the blame and cost for this
 * composable only. [LoupeRunListener] turns it into the end-of-run report.
 */
fun SemanticsNodeInteraction.shouldNeverRecompose() {
    val (name, history) = resolveHistory()
    if (history.totalCompositions != 1) {
        throw loupeFailure(
            name = name,
            history = history,
            expected = "never recompose",
            actual = "${history.totalCompositions} compositions",
        )
    }
}

/**
 * Asserts that this composable was composed exactly twice: once initially and once recomposed.
 *
 * Shorthand for `shouldRecompose(times(2))`.
 */
fun SemanticsNodeInteraction.shouldRecomposeOnce() = shouldRecompose(times(2))

/**
 * Asserts that the total composition count (initial + recompositions) satisfies [matcher].
 *
 * Examples:
 * ```kotlin
 * rule.onNodeWithTag("AlbumCard").shouldRecompose(times(2))   // initial + 1 recompose
 * rule.onNodeWithTag("AlbumCard").shouldRecompose(atMost(3))  // initial + at most 2
 * rule.onNodeWithTag("AlbumCard").shouldRecompose(atLeast(2)) // recomposed at least once
 * ```
 */
fun SemanticsNodeInteraction.shouldRecompose(matcher: CompositionMatcher) {
    val (name, history) = resolveHistory()
    if (!matcher.matches(history.totalCompositions)) {
        throw loupeFailure(
            name = name,
            history = history,
            expected = matcher.describe(),
            actual = "${history.totalCompositions} compositions",
        )
    }
}

/**
 * Asserts that the total measured duration of all compositions for this composable
 * does not exceed [maxMs] milliseconds.
 */
fun SemanticsNodeInteraction.maxRecompositionTimeInMs(maxMs: Float) {
    val (name, history) = resolveHistory()
    if (history.totalDurationMs > maxMs) {
        throw loupeFailure(
            name = name,
            history = history,
            expected = "total duration ≤ ${trim(maxMs)}ms",
            actual = "${trim(history.totalDurationMs)}ms",
        )
    }
}

// ── Internal ──────────────────────────────────────────────────────────────────

/**
 * Resolves the node's [TestTag][SemanticsProperties.TestTag] to a [CompositionHistory].
 *
 * Resolution order:
 * 1. Exact match on the full key
 * 2. Suffix match for `FileName.FunctionName` keys (matches `".$tag"` suffix)
 * 3. Zero matches → error with the recorded key list
 * 4. Multiple suffix matches → error with the ambiguous key list and a hint
 *
 * Lookup failures happen before any history is known, so they throw a plain
 * [AssertionError] with no blame data.
 */
internal fun SemanticsNodeInteraction.resolveHistory(): Pair<String, CompositionHistory> {
    val config = fetchSemanticsNode().config
    val tag = if (SemanticsProperties.TestTag in config) config[SemanticsProperties.TestTag]
    else throw AssertionError(
        "The SemanticsNode has no TestTag. " +
            "Add Modifier.testTag(\"YourFunctionName\") to the composable under test."
    )

    val snap = LoupeRuntime.snapshot()
    val composables = snap.composables

    // 0. Per-instance tag match — highest priority (zero-instrumentation modifier tracking)
    snap.instances[tag]?.let { return tag to it }

    // 1. Exact match on aggregate key
    composables[tag]?.let { return tag to it }

    // 2. Suffix match — key is "FileName.FunctionName", tag is "FunctionName"
    val suffixMatches = composables.entries.filter { (key, _) -> key.endsWith(".$tag") }
    when (suffixMatches.size) {
        1 -> return suffixMatches.first().key to suffixMatches.first().value
        0 -> { /* fall through */ }
        else -> throw AssertionError(
            "Ambiguous: test tag '$tag' matches multiple composable keys:\n" +
                suffixMatches.joinToString("\n") { "  ${it.key}" } +
                "\nUse the full key (e.g. \"FileName.$tag\") as the testTag value."
        )
    }

    throw AssertionError(
        "No Loupe data found for composable tagged '$tag'. " +
            "Recorded composables: ${composables.keys.sorted()}. " +
            "Verify the Loupe compiler plugin is applied to the module under test."
    )
}

/** Builds a [LoupeAssertionError] with the active thresholds and the resolved blame. */
private fun loupeFailure(
    name: String,
    history: CompositionHistory,
    expected: String,
    actual: String,
): LoupeAssertionError {
    val snap = LoupeRuntime.snapshot()
    return LoupeAssertionError(
        message = "Loupe assertion failed for '$name': expected $expected, got $actual",
        composableKey = name,
        expected = expected,
        actual = actual,
        blamedParams = history.blamedParams,
        totalCompositions = history.totalCompositions,
        windowRecompositions = history.windowRecompositions,
        totalDurationMs = history.totalDurationMs,
        hotThreshold = snap.hotThreshold,
        warmThreshold = snap.warmThreshold,
    )
}

private fun trim(value: Float): String = "%.1f".format(Locale.US, value)
