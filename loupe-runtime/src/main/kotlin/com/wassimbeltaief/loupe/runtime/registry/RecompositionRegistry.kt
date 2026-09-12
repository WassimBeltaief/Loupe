package com.wassimbeltaief.loupe.runtime.registry

import com.wassimbeltaief.loupe.runtime.analysis.ParamDiffer
import com.wassimbeltaief.loupe.runtime.analysis.SuggestionEngine
import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import com.wassimbeltaief.loupe.runtime.model.RecompositionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class RecompositionRegistry(
    private val maxHistoryEntries: Int = 500,
    private val windowNs: Long = 5_000_000_000L,
    private val timeSource: () -> Long = System::nanoTime,
) {
    companion object {
        /** Sentinel for "recordEnd not yet received" — never a valid measured duration. */
        const val DURATION_UNSET = -1L
    }

    private data class Entry(
        val records: ArrayDeque<RecompositionRecord> = ArrayDeque(),
        val previousParams: MutableMap<String, Any?> = mutableMapOf(),
        var file: String = "",
        var line: Int = 0,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    private val _state = MutableStateFlow<Map<String, RecompositionHistory>>(emptyMap())
    val state: StateFlow<Map<String, RecompositionHistory>> = _state.asStateFlow()

    fun record(key: String, file: String, line: Int, params: Array<Pair<String, Any?>>) {
        val entry = entries.getOrPut(key) { Entry() }
        synchronized(entry) {
            entry.file = file
            entry.line = line

            val snapshots = ParamDiffer.diff(entry.previousParams, params)
            entry.previousParams.clear()
            params.forEach { (name, value) -> entry.previousParams[name] = value }

            val wasForced = entry.records.isNotEmpty() &&
                snapshots.isNotEmpty() &&
                snapshots.all { it.verdict == ParamVerdict.Unchanged }

            val record = RecompositionRecord(
                key = key,
                file = file,
                line = line,
                timestampNs = timeSource(),
                params = snapshots,
                durationNs = DURATION_UNSET,
                wasForced = wasForced,
            )

            entry.records.addFirst(record)
            while (entry.records.size > maxHistoryEntries) {
                entry.records.removeLast()
            }
        }
        _state.value = snapshot()
    }

    /**
     * Called from the compiler-injected finally block wrapping the composable body.
     * Sets [RecompositionRecord.durationNs] on the newest record still lacking one —
     * LIFO pairing keeps recursive composables correct. No-op if no record matches
     * (e.g. reset() or configure() happened mid-composition).
     */
    fun recordEnd(key: String, endNs: Long = timeSource()) {
        val entry = entries[key] ?: return
        synchronized(entry) {
            val index = entry.records.indexOfFirst { it.durationNs == DURATION_UNSET }
            if (index >= 0) {
                val r = entry.records[index]
                entry.records[index] = r.copy(durationNs = (endNs - r.timestampNs).coerceAtLeast(0))
            }
        }
        _state.value = snapshot()
    }

    fun reset() {
        entries.clear()
        _state.value = emptyMap()
    }

    fun snapshot(): Map<String, RecompositionHistory> =
        entries.mapValues { (key, entry) ->
            synchronized(entry) {
                val now = timeSource()
                val windowRecompositions = entry.records.count { now - it.timestampNs <= windowNs }
                // Records still awaiting recordEnd (durationNs == DURATION_UNSET) don't count
                val totalDurationMs = entry.records
                    .filter { it.durationNs >= 0 }
                    .sumOf { it.durationNs }
                    .toFloat() / 1_000_000f
                RecompositionHistory(
                    key = key,
                    file = entry.file,
                    line = entry.line,
                    records = entry.records.toList(),
                    totalRecompositions = entry.records.size,
                    windowRecompositions = windowRecompositions,
                    totalDurationMs = totalDurationMs,
                    blamedParams = computeBlame(entry.records.toList()),
                )
            }
        }

    private fun computeBlame(records: List<RecompositionRecord>): List<BlamedParam> {
        if (records.isEmpty()) return emptyList()
        val counts = mutableMapOf<String, Int>()
        val verdicts = mutableMapOf<String, ParamVerdict>()
        for (record in records) {
            for (snap in record.params) {
                if (snap.verdict == ParamVerdict.Changed || snap.verdict == ParamVerdict.LambdaIdentity) {
                    counts[snap.name] = (counts[snap.name] ?: 0) + 1
                    verdicts[snap.name] = snap.verdict
                }
            }
        }
        val total = counts.values.sum().toFloat().coerceAtLeast(1f)
        val blamed = counts.entries
            .sortedByDescending { it.value }
            .map { (name, count) ->
                val verdict = verdicts[name] ?: ParamVerdict.Changed
                BlamedParam(
                    name = name,
                    recompositionCount = count,
                    fraction = count / total,
                    dominantVerdict = verdict,
                    suggestion = SuggestionEngine.forBlame(name, verdict, count, count / total),
                )
            }
        // Spec: the blame bar also shows params that never contributed ("title · 0x stable")
        val stable = records.first().params
            .filter { it.name !in counts && it.verdict != ParamVerdict.FirstComposition }
            .map { BlamedParam(it.name, 0, 0f, ParamVerdict.Unchanged) }
        return blamed + stable
    }
}
