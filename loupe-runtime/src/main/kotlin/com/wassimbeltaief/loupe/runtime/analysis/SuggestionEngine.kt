package com.wassimbeltaief.loupe.runtime.analysis

import com.wassimbeltaief.loupe.runtime.model.ParamVerdict

/**
 * Maps parameter verdicts to human-readable fix suggestions (CLAUDE.md rules table).
 * Rules are checked in order; first match wins.
 *
 * Not implemented here: the "non-data class" rule — reliably detecting data classes
 * requires kotlin-reflect or compiler-plugin type metadata (future work). The
 * "wasForced with all params unchanged" rule is record-level, handled with #24/#25.
 */
object SuggestionEngine {

    // Lambda rules apply only when the count is meaningful — one-off identity
    // changes are usually legitimate closure captures.
    private const val MIN_LAMBDA_BLAME_COUNT = 3

    /**
     * Stateless per-snapshot rules, attached to [ParamSnapshot.suggestion]
     * on a [ParamVerdict.Changed] verdict. [value] is the raw captured value.
     *
     * instanceof checks (not name matching) so immutable lookalikes such as
     * `java.util.Arrays$ArrayList` (what `listOf()` compiles to) are NOT flagged,
     * while subclasses like LinkedHashMap still are.
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
     * Mutable collections Compose treats as unstable: compared by *identity*, not
     * `equals`. `listOf()` (an immutable `Arrays$ArrayList`) is deliberately excluded —
     * only genuinely mutable types are flagged.
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
     * History-level rules, attached to [BlamedParam.suggestion].
     * Lambda wording deliberately acknowledges ambiguity (CLAUDE.md "Lambda
     * Identity Ambiguity" section) — a lambda that captures changing state is
     * supposed to change identity, so we never assert it's definitely a bug.
     */
    fun forBlame(
        name: String,
        dominantVerdict: ParamVerdict,
        recompositionCount: Int,
        fraction: Float,
    ): String? {
        if (dominantVerdict != ParamVerdict.LambdaIdentity) return null
        return when {
            // Identity changed on (nearly) every recomposition
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
