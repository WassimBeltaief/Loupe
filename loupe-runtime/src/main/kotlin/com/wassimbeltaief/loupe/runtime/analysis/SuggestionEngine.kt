package com.wassimbeltaief.loupe.runtime.analysis

import com.wassimbeltaief.loupe.runtime.model.ParamVerdict

/**
 * Turns a verdict into a short, human-readable fix suggestion.
 *
 * Rules are checked in order and the first match wins. Suggestions are shown in
 * the drill-down panel and in Logcat output.
 *
 * One rule is intentionally not here: detecting non-data classes would need
 * reflection or compiler metadata, so it is left for later.
 */
object SuggestionEngine {

    // A lambda rule only makes sense when the pattern repeats. A single identity
    // change is usually a normal closure, not a bug.
    private const val MIN_LAMBDA_BLAME_COUNT = 3

    /**
     * Rule for one changed value, attached to the snapshot suggestion.
     *
     * The checks use the real class, not the type name, so immutable lookalikes
     * such as the list returned by `listOf()` are not flagged, while subclasses
     * like `LinkedHashMap` still are.
     */
    fun forChangedValue(name: String, value: Any?): String? {
        if (value == null) return null
        return when {
            isUnstableList(value) ->
                "$name uses a mutable collection type which is unstable. " +
                    "Replace with ImmutableList or persistentListOf()."
            isUnstableMap(value) ->
                "$name uses a mutable map which is unstable. " +
                    "Replace with ImmutableMap or persistentMapOf()."
            else -> null
        }
    }

    /**
     * True for mutable collections that Compose compares by identity instead of
     * by content. Only real mutable types are counted; immutable lists are not.
     */
    fun isUnstableCollection(value: Any?): Boolean =
        isUnstableList(value) || isUnstableMap(value)

    private fun isUnstableList(value: Any?): Boolean {
        if (value == null) return false
        return value is ArrayList<*> || value is java.util.LinkedList<*> ||
            value.javaClass.simpleName.contains("MutableList")
    }

    private fun isUnstableMap(value: Any?): Boolean {
        if (value == null) return false
        return value is HashMap<*, *> || value is java.util.TreeMap<*, *> ||
            value is java.util.Hashtable<*, *> || value.javaClass.simpleName.contains("MutableMap")
    }

    /**
     * Rule for the whole history, attached to one entry in the blame list.
     *
     * The wording for lambdas stays careful. A lambda that reads changing state
     * is supposed to change identity, so the suggestion never calls it a bug. It
     * only says what to do if the lambda does not need to close over state.
     */
    fun forBlame(
        name: String,
        dominantVerdict: ParamVerdict,
        recompositionCount: Int,
        fraction: Float,
    ): String? {
        if (dominantVerdict != ParamVerdict.LambdaIdentity) return null
        return when {
            // Identity changed on almost every recomposition.
            fraction >= 0.99f ->
                "$name's identity changed on every recomposition. " +
                    "If it doesn't need to close over changing values, wrap it in remember {} " +
                    "or move it to a top-level function. If it does close over state, this is expected."
            recompositionCount > MIN_LAMBDA_BLAME_COUNT ->
                "$name is recreated on many parent recompositions. " +
                    "Wrap the lambda in remember(key) { } where the key is what the lambda closes over."
            else -> null
        }
    }
}
