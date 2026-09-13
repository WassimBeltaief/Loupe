package com.wassimbeltaief.loupe.runtime.analysis

import com.wassimbeltaief.loupe.runtime.model.LambdaRef
import com.wassimbeltaief.loupe.runtime.model.ParamSnapshot
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict

/**
 * Compares the parameters of a composable with the values from the previous
 * recomposition and produces one [ParamSnapshot] per parameter.
 *
 * Two rules make the result match how Compose really behaves:
 * - Lambdas are compared by identity, never by value. Two lambdas that do the
 *   same thing are still different objects.
 * - Mutable collections are compared by identity too, because Compose cannot
 *   skip them when the instance changes.
 *
 * A host app object with a broken `toString()` or `equals()` must never crash
 * the app, so both calls are guarded.
 */
object ParamDiffer {

    private const val MAX_VALUE_LENGTH = 120

    /**
     * @param previous values from the last recomposition, keyed by parameter name
     * @param current values captured in this recomposition
     */
    fun diff(
        previous: Map<String, Any?>,
        current: Array<Pair<String, Any?>>,
    ): List<ParamSnapshot> = current.map { (name, value) ->
        val currentStr = value.truncated()
        if (name !in previous) {
            ParamSnapshot(name, "", currentStr, ParamVerdict.FirstComposition)
        } else {
            val prev = previous[name]
            val previousStr = prev.truncated()
            val verdict = when {
                value is LambdaRef -> if (value != prev) ParamVerdict.LambdaIdentity else ParamVerdict.Unchanged
                // Compose compares unstable collections by identity, so a fresh
                // list with the same content is still a change to it.
                SuggestionEngine.isUnstableCollection(value) ->
                    if (value !== prev) ParamVerdict.Changed else ParamVerdict.Unchanged
                safeEquals(value, prev) -> ParamVerdict.Unchanged
                else -> ParamVerdict.Changed
            }
            val suggestion = if (verdict == ParamVerdict.Changed) {
                SuggestionEngine.forChangedValue(name, value)
            } else {
                null
            }
            ParamSnapshot(name, previousStr, currentStr, verdict, suggestion)
        }
    }

    // A throwing toString() must never crash the app. Strings pass PII detection
    // first, so emails and card-like numbers never reach the registry.
    private fun Any?.truncated(): String {
        val raw = runCatching { toString() }.getOrDefault("<toString error>")
        val redacted = if (this is String) PiiRedactor.redact(raw) else raw
        return redacted.take(MAX_VALUE_LENGTH)
    }

    // A throwing equals() is treated as "changed". Conservative, and it never crashes.
    private fun safeEquals(a: Any?, b: Any?): Boolean =
        runCatching { a == b }.getOrDefault(false)
}
