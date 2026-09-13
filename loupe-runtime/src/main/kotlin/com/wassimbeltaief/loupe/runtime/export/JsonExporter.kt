package com.wassimbeltaief.loupe.runtime.export

import com.wassimbeltaief.loupe.runtime.model.ParamSnapshot
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory

/**
 * Serialises a full [RecompositionHistory] (records included) to JSON for the
 * drill-down panel's Share / Copy actions. Hand-rolled (no serialization
 * dependency in the runtime) with proper string escaping.
 */
object JsonExporter {

    fun historyToJson(history: RecompositionHistory): String = buildString {
        append("{")
        field("key", history.key); comma()
        field("file", history.file); comma()
        field("line", history.line); comma()
        field("totalRecompositions", history.totalRecompositions); comma()
        field("windowRecompositions", history.windowRecompositions); comma()
        field("totalDurationMs", history.totalDurationMs); comma()
        append("\"blamedParams\":[")
        history.blamedParams.forEachIndexed { i, b ->
            if (i > 0) comma()
            append("{")
            field("name", b.name); comma()
            field("recompositionCount", b.recompositionCount); comma()
            field("fraction", b.fraction); comma()
            field("dominantVerdict", b.dominantVerdict::class.simpleName ?: "Unknown"); comma()
            field("state", b.isState); comma()
            nullableField("suggestion", b.suggestion)
            append("}")
        }
        append("]"); comma()
        append("\"records\":[")
        history.records.forEachIndexed { i, r ->
            if (i > 0) comma()
            append("{")
            field("timestampNs", r.timestampNs); comma()
            field("durationNs", r.durationNs); comma()
            field("wasForced", r.wasForced); comma()
            append("\"params\":")
            snapshotArray(r.params)
            comma()
            append("\"stateChanges\":")
            snapshotArray(r.stateChanges)
            append("}")
        }
        append("]")
        append("}")
    }

    private fun StringBuilder.snapshotArray(snapshots: List<ParamSnapshot>) {
        append("[")
        snapshots.forEachIndexed { j, p ->
            if (j > 0) comma()
            append("{")
            field("name", p.name); comma()
            field("previousValue", p.previousValue); comma()
            field("currentValue", p.currentValue); comma()
            field("verdict", p.verdict::class.simpleName ?: "Unknown"); comma()
            nullableField("suggestion", p.suggestion)
            append("}")
        }
        append("]")
    }

    private fun StringBuilder.comma() = append(",")
    private fun StringBuilder.field(name: String, value: String) {
        append("\""); append(name); append("\":\""); append(escape(value)); append("\"")
    }
    private fun StringBuilder.field(name: String, value: Number) {
        append("\""); append(name); append("\":").append(value)
    }
    private fun StringBuilder.field(name: String, value: Boolean) {
        append("\""); append(name); append("\":").append(value)
    }
    private fun StringBuilder.nullableField(name: String, value: String?) {
        append("\""); append(name); append("\":")
        if (value == null) append("null") else { append("\""); append(escape(value)); append("\"") }
    }

    internal fun escape(value: String): String = buildString(value.length + 16) {
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }
}
