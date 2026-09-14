package com.wassimbeltaief.loupe.testing

import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import java.util.Locale

/**
 * The [AssertionError] raised by the Loupe assertion DSL.
 *
 * Besides the human-readable message (a single concise line, no tables), it
 * carries the structured recomposition data for the composable under test. That
 * lets [LoupeRunListener] build the end-of-run report without re-reading any
 * global state, and without touching the other composables in the report.
 *
 * The message ends with a compact, machine-readable `[loupe]` line. It survives
 * into AGP's connected-test result XML, where the `printLoupeTestResults` Gradle
 * task parses it to build the host-side summary. See `LoupeResultsFormatter`.
 */
class LoupeAssertionError(
    message: String,
    /** Resolved composable key, e.g. `AlbumScreens.CommentsSection`. */
    val composableKey: String,
    /** What the assertion expected, in human terms. */
    val expected: String,
    /** What was actually observed, in human terms. */
    val actual: String,
    /** Ranked parameters that drove the recompositions. */
    val blamedParams: List<BlamedParam>,
    val totalCompositions: Int,
    val windowRecompositions: Int,
    val totalDurationMs: Float,
    val hotThreshold: Int,
    val warmThreshold: Int,
) : AssertionError(
    message + "\n" + loupeMarker(
        composableKey = composableKey,
        blamedParams = blamedParams,
        totalCompositions = totalCompositions,
        windowRecompositions = windowRecompositions,
        totalDurationMs = totalDurationMs,
        hotThreshold = hotThreshold,
        warmThreshold = warmThreshold,
    ),
)

/**
 * `[loupe] composable=CommentsSection status=WARM blame=state:3 total=4 window=4 durationMs=30.5`
 *
 * Kept on one line with no spaces inside values, so it can be parsed with a
 * simple `key=value` split.
 */
private fun loupeMarker(
    composableKey: String,
    blamedParams: List<BlamedParam>,
    totalCompositions: Int,
    windowRecompositions: Int,
    totalDurationMs: Float,
    hotThreshold: Int,
    warmThreshold: Int,
): String {
    val status = when {
        windowRecompositions >= hotThreshold -> "HOT"
        windowRecompositions >= warmThreshold -> "WARM"
        else -> "OK"
    }
    val blame = blamedParams
        .filter { it.recompositionCount > 0 }
        .joinToString(",") { "${it.name}:${it.recompositionCount}" }
        .ifEmpty { "-" }
    val duration = "%.1f".format(Locale.US, totalDurationMs)
    return "[loupe] composable=$composableKey status=$status blame=$blame " +
        "total=$totalCompositions window=$windowRecompositions durationMs=$duration"
}