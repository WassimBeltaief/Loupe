package com.wassimbeltaief.loupe.runtime.analysis

import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionRecord

/**
 * Groups a composable's recomposition records (newest-first) into "bursts":
 * sequences of recompositions within [DEFAULT_BURST_GAP_NS] of each other.
 * Bursts are the collapsed unit of the drill-down timeline (CLAUDE.md spec).
 */
object BurstGrouper {

    const val DEFAULT_BURST_GAP_NS = 200_000_000L // 200ms

    data class Burst(
        /** Records in this burst, newest first. */
        val records: List<RecompositionRecord>,
        /** 1-based recomposition number of the oldest record in the burst. */
        val firstIndex: Int,
        /** 1-based recomposition number of the newest record in the burst. */
        val lastIndex: Int,
    ) {
        val count: Int get() = records.size
        val isSingle: Boolean get() = records.size == 1
        val totalDurationNs: Long get() = records.filter { it.durationNs >= 0 }.sumOf { it.durationNs }

        /** Param that changed most often across the burst — the headline cause. */
        val dominantChangedParam: String?
            get() = records
                .flatMap { it.params }
                .filter { it.verdict == ParamVerdict.Changed || it.verdict == ParamVerdict.LambdaIdentity }
                .groupingBy { it.name }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

        /** Verdict of [dominantChangedParam] — drives its colour (never rank). */
        val dominantChangedVerdict: ParamVerdict
            get() {
                val dominant = dominantChangedParam ?: return ParamVerdict.Unchanged
                return records
                    .flatMap { it.params }
                    .firstOrNull {
                        it.name == dominant &&
                            (it.verdict == ParamVerdict.Changed || it.verdict == ParamVerdict.LambdaIdentity)
                    }
                    ?.verdict ?: ParamVerdict.Unchanged
            }

        /** Names of local `MutableState` reads that changed across the burst. */
        val changedStateNames: List<String>
            get() = records
                .flatMap { it.stateChanges }
                .filter { it.verdict == ParamVerdict.Changed }
                .map { it.name }
                .distinct()
    }

    /**
     * @param records newest-first recomposition records for one composable
     * @param burstGapNs max gap between consecutive records to be one burst
     */
    fun group(
        records: List<RecompositionRecord>,
        burstGapNs: Long = DEFAULT_BURST_GAP_NS,
    ): List<Burst> {
        if (records.isEmpty()) return emptyList()
        val total = records.size
        val bursts = mutableListOf<MutableList<RecompositionRecord>>()
        var current = mutableListOf(records[0])
        for (i in 1 until records.size) {
            val newer = records[i - 1]
            val candidate = records[i]
            // records are newest-first, so newer.timestampNs >= candidate.timestampNs
            if (newer.timestampNs - candidate.timestampNs <= burstGapNs) {
                current += candidate
            } else {
                bursts += current
                current = mutableListOf(candidate)
            }
        }
        bursts += current

        // Recomposition numbers: records are newest-first, so index i has number (total - i).
        // A burst spanning indices [a..b] covers numbers (total - b)..(total - a).
        var index = 0
        return bursts.map { burst ->
            val firstIdx = index + burst.size       // number of oldest record in burst
            val lastIdx = index + 1                 // number of newest record in burst
            index += burst.size
            Burst(
                records = burst,
                firstIndex = total - firstIdx + 1,
                lastIndex = total - lastIdx + 1,
            )
        }
    }
}
