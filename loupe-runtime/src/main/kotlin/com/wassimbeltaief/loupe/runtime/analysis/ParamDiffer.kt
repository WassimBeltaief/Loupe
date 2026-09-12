package com.wassimbeltaief.loupe.runtime.analysis

import com.wassimbeltaief.loupe.runtime.model.LambdaRef
import com.wassimbeltaief.loupe.runtime.model.ParamSnapshot
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict

object ParamDiffer {

    private const val MAX_VALUE_LENGTH = 120

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

    // A throwing toString() on a host-app object must never crash the app (#38).
    // String values pass PII detection first — emails and card-like numbers
    // never reach the registry (#20).
    private fun Any?.truncated(): String {
        val raw = runCatching { toString() }.getOrDefault("<toString error>")
        val redacted = if (this is String) PiiRedactor.redact(raw) else raw
        return redacted.take(MAX_VALUE_LENGTH)
    }

    // A throwing equals() is treated as "changed" — conservative, never crashes (#38)
    private fun safeEquals(a: Any?, b: Any?): Boolean =
        runCatching { a == b }.getOrDefault(false)
}
