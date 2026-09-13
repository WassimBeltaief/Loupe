package com.wassimbeltaief.loupe.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import com.wassimbeltaief.loupe.runtime.LoupeRuntime
import com.wassimbeltaief.loupe.runtime.model.CompositionHistory

/**
 * Asserts that this composable was composed exactly once — never recomposed.
 *
 * Composition count includes the initial composition, so passing means
 * [CompositionHistory.totalCompositions] == 1.
 *
 * The node must carry a [Modifier.testTag][androidx.compose.ui.Modifier] whose value
 * matches the composable's function name (or its full `FileName.FunctionName` Loupe key).
 *
 * Throws [AssertionError] with a CI table on failure.
 */
fun SemanticsNodeInteraction.shouldNeverRecompose() {
    val (name, history) = resolveHistory()
    val count = history.totalCompositions
    if (count != 1) {
        val table = CiTablePrinter.format(LoupeRuntime.snapshot())
        throw AssertionError(
            "shouldNeverRecompose() failed for '$name': " +
            "composition count is $count (expected 1 — initial only)\n$table"
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
    val count = history.totalCompositions
    if (!matcher.matches(count)) {
        val table = CiTablePrinter.format(LoupeRuntime.snapshot())
        throw AssertionError(
            "shouldRecompose(${matcher.describe()}) failed for '$name': " +
            "composition count is $count\n$table"
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
        val table = CiTablePrinter.format(LoupeRuntime.snapshot())
        throw AssertionError(
            "maxRecompositionTimeInMs($maxMs) failed for '$name': " +
            "took ${"%.1f".format(java.util.Locale.US, history.totalDurationMs)}ms\n$table"
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
 * 3. Zero matches → error with recorded key list + CI table
 * 4. Multiple suffix matches → error with ambiguous key list + disambiguation hint
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
            "\nUse the full key (e.g. \"FileName.$tag\") as the testTag value.\n" +
            CiTablePrinter.format(snap)
        )
    }

    // 3. No match
    throw AssertionError(
        "No Loupe data found for composable tagged '$tag'.\n" +
        "Recorded composables: ${composables.keys.sorted()}\n" +
        "Verify the Loupe compiler plugin is applied to the module under test.\n" +
        CiTablePrinter.format(snap)
    )
}
