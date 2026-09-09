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
                value == prev -> ParamVerdict.Unchanged
                else -> ParamVerdict.Changed
            }
            ParamSnapshot(name, previousStr, currentStr, verdict)
        }
    }

    private fun Any?.truncated(): String =
        toString().take(MAX_VALUE_LENGTH)
}
