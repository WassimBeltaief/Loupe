package com.wassimbeltaief.loupe.runtime.analysis

import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionRecord

/**
 * Groups recomposition records into bursts.
 *
 * A burst is a run of recompositions that happen close together in time. This
 * is how the drill-down timeline stays readable: instead of showing 40 rows for
 * one fast scroll, it shows one burst of 40.
 */
object BurstGrouper {

    /** Records closer than this are treated as one burst. */
    const val DEFAULT_BURST_GAP_NS = 200_000_000L // 200ms

    /**
     * One burst of recompositions, plus the details the timeline shows for it.
     */
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

        /** The parameter that changed most often in this burst, or null if none. */
        val dominantChangedParam: String?
            get() = records
                .flatMap { it.params }
                .filter { it.verdict == ParamVerdict.Changed || it.verdict == ParamVerdict.LambdaIdentity }
                .groupingBy { it.name }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

        /** The verdict of [dominantChangedParam]. It decides the colour, not the rank. */
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

        /** Names of local `MutableState` reads that changed in this burst. */
        val changedStateNames: List<String>
            get() = records
                .flatMap { it.stateChanges }
                .filter { it.verdict == ParamVerdict.Changed }
                .map { it.name }
                .distinct()
    }

    /**
     * Splits a composable's records into bursts.
     *
     * @param records records for one composable, newest first
     */
    fun group(records: List<RecompositionRecord>): List<Burst> {
        if (records.isEmpty()) return emptyList()
        val total = records.size
        val bursts = mutableListOf<MutableList<RecompositionRecord>>()
        var current = mutableListOf(records[0])
        for (i in 1 until records.size) {
            val newer = records[i - 1]
            val candidate = records[i]
            // The list is newest first, so newer.timestampNs >= candidate.timestampNs.
            if (newer.timestampNs - candidate.timestampNs <= DEFAULT_BURST_GAP_NS) {
                current += candidate
            } else {
                bursts += current
                current = mutableListOf(candidate)
            }
        }
        bursts += current

        // Recomposition numbers: index i has number (total - i), because the list is
        // newest first. A burst over indices [a. .b] covers numbers (total - b). .(total - a).
        var index = 0
        return bursts.map { burst ->
            val firstIdx = index + burst.size       // number of the oldest record
            val lastIdx = index + 1                 // number of the newest record
            index += burst.size
            Burst(
                records = burst,
                firstIndex = total - firstIdx + 1,
                lastIndex = total - lastIdx + 1,
            )
        }
    }
}
