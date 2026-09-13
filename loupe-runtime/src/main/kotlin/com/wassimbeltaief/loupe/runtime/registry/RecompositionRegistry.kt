package com.wassimbeltaief.loupe.runtime.registry

import com.wassimbeltaief.loupe.runtime.analysis.ParamDiffer
import com.wassimbeltaief.loupe.runtime.analysis.SuggestionEngine
import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import com.wassimbeltaief.loupe.runtime.model.ParamSnapshot
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.CompositionHistory
import com.wassimbeltaief.loupe.runtime.model.RecompositionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for recomposition events.
 *
 * It keeps two views of the same data:
 * - [state]: one history per composable function, keyed by `File.Function`.
 *   This is the unit of the heatmap and the CI report.
 * - [instances]: one history per instance, keyed by `File.Function#compoundKeyHash`.
 *   This is the unit of the overlay list, so two cards of the same composable
 *   are two rows with their own counts.
 *
 * Each history is a circular buffer, so memory stays bounded during long sessions.
 *
 * @param maxHistoryEntries records kept per composable, newest first
 * @param windowNs length of the rolling window used for the live counts
 * @param timeSource clock source, injectable so tests can control time
 */
class RecompositionRegistry(
    private val maxHistoryEntries: Int = 500,
    private val windowNs: Long = 5_000_000_000L,
    private val timeSource: () -> Long = System::nanoTime,
) {
    companion object {
        /** Sentinel for "recordEnd not yet received". Never a valid measured duration. */
        const val DURATION_UNSET = -1L

        /** Builds the id of one instance from its composable key and key hash. */
        internal fun instanceId(key: String, instance: Int): String = "$key#$instance"
    }

    private data class Entry(
        val records: ArrayDeque<RecompositionRecord> = ArrayDeque(),
        val previousParams: MutableMap<String, Any?> = mutableMapOf(),
        val previousStates: MutableMap<String, Any?> = mutableMapOf(),
        var file: String = "",
        var line: Int = 0,
        var key: String = "",
        var instanceId: String = "",
    )

    /** Aggregate per composable key. */
    private val entries = ConcurrentHashMap<String, Entry>()

    /** Per-instance records, keyed by `key#instanceHash`. */
    private val instanceEntries = ConcurrentHashMap<String, Entry>()

    /** Per-instance records for composables whose root carries a `Modifier.testTag`. Keyed by the tag string. */
    private val taggedInstanceEntries = ConcurrentHashMap<String, Entry>()

    private val _state = MutableStateFlow<Map<String, CompositionHistory>>(emptyMap())

    /** One history per composable function. Used by the heatmap and the CI report. */
    val state: StateFlow<Map<String, CompositionHistory>> = _state.asStateFlow()

    private val _instances = MutableStateFlow<List<CompositionHistory>>(emptyList())

    /** One history per instance. Used by the overlay list. */
    val instances: StateFlow<List<CompositionHistory>> = _instances.asStateFlow()

    /**
     * Adds one recomposition to both views and returns the new record.
     *
     * @param key composable key (`File.Function`)
     * @param instance the Compose compound key hash that identifies this instance
     */
    fun record(
        key: String,
        file: String,
        line: Int,
        params: Array<Pair<String, Any?>>,
        instance: Int = 0,
        instanceTag: String? = null,
    ): RecompositionRecord {
        val aggregate = entries.getOrPut(key) { Entry() }.also {
            it.key = key
            it.instanceId = key
        }

        val created = synchronized(aggregate) { applyRecord(aggregate, key, file, line, params) }

        // Every recomposition is tracked per instance, whether or not the composable
        // carries a testTag. The overlay and heatmap read this view, so a tag must not
        // remove a composable from them.
        val id = instanceId(key, instance)
        val perInstance = instanceEntries.getOrPut(id) { Entry() }.also {
            it.key = key
            it.instanceId = id
        }
        synchronized(perInstance) { applyRecord(perInstance, key, file, line, params) }

        // A testTag adds a second, stable index used by the testing API.
        if (instanceTag != null) {
            val tagged = taggedInstanceEntries.getOrPut(instanceTag) { Entry() }.also {
                it.key = key
                it.instanceId = instanceTag
            }
            synchronized(tagged) { applyRecord(tagged, key, file, line, params) }
        }

        _state.value = snapshot()
        _instances.value = instancesSnapshot()
        return created
    }

    private fun applyRecord(
        entry: Entry,
        key: String,
        file: String,
        line: Int,
        params: Array<Pair<String, Any?>>,
    ): RecompositionRecord {
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
        return record
    }

    /**
     * Called from the compiler-injected finally block wrapping the composable body.
     * Sets [RecompositionRecord.durationNs] on the newest record still lacking one —
     * LIFO pairing keeps recursive composables correct. No-op if no record matches
     * (e.g. reset() or configure() happened mid-composition).
     */
    fun recordEnd(key: String, instance: Int = 0, endNs: Long = timeSource(), instanceTag: String? = null) {
        finish(entries[key], endNs)
        finish(instanceEntries[instanceId(key, instance)], endNs)
        if (instanceTag != null) {
            finish(taggedInstanceEntries[instanceTag], endNs)
        }
        _state.value = snapshot()
        _instances.value = instancesSnapshot()
    }

    private fun finish(entry: Entry?, endNs: Long) {
        if (entry == null) return
        synchronized(entry) {
            val index = entry.records.indexOfFirst { it.durationNs == DURATION_UNSET }
            if (index >= 0) {
                val r = entry.records[index]
                entry.records[index] = r.copy(durationNs = (endNs - r.timestampNs).coerceAtLeast(0))
            }
        }
    }

    /**
     * Called from the compiler-injected code right after a local `MutableState` is
     * created inside an instrumented composable. Attaches the state's value to the
     * newest record as a [ParamSnapshot] so internal state changes (e.g. `counter++`)
     * are visible in the drill-down instead of surfacing only as a forced recomposition.
     */
    fun trackState(
        key: String,
        instance: Int = 0,
        name: String,
        value: Any?,
        instanceTag: String? = null,
    ) {
        applyState(entries[key], name, value)
        applyState(instanceEntries[instanceId(key, instance)], name, value)
        if (instanceTag != null) {
            applyState(taggedInstanceEntries[instanceTag], name, value)
        }
        _state.value = snapshot()
        _instances.value = instancesSnapshot()
    }

    private fun applyState(entry: Entry?, name: String, value: Any?) {
        if (entry == null) return
        synchronized(entry) {
            val index = entry.records.indexOfFirst { it.durationNs == DURATION_UNSET }
            if (index < 0) return
            val record = entry.records[index]
            // A record can carry several state reads; only the first write of a name is a
            // real transition, later reads within the same pass repeat the same value.
            if (record.stateChanges.any { it.name == name }) return
            val hasPrevious = entry.previousStates.containsKey(name)
            val previous = entry.previousStates[name]
            val verdict = when {
                !hasPrevious -> ParamVerdict.FirstComposition
                equalSafely(previous, value) -> ParamVerdict.Unchanged
                else -> ParamVerdict.Changed
            }
            entry.previousStates[name] = value
            entry.records[index] = record.copy(
                stateChanges = record.stateChanges + ParamSnapshot(
                    name = name,
                    previousValue = if (hasPrevious) previous.toString().truncate() else "",
                    currentValue = value.toString().truncate(),
                    verdict = verdict,
                    suggestion = null,
                ),
            )
        }
    }

    private fun equalSafely(a: Any?, b: Any?): Boolean =
        try {
            a == b
        } catch (_: Throwable) {
            false
        }

    private fun Any?.truncate(): String {
        val text = this?.toString() ?: return "null"
        return if (text.length <= 120) text else text.take(117) + "..."
    }

    /** Drops all history and counts, for all views. */
    fun reset() {
        entries.clear()
        instanceEntries.clear()
        taggedInstanceEntries.clear()
        _state.value = emptyMap()
        _instances.value = emptyList()
    }

    /** Per-instance histories for composables that carried a `Modifier.testTag`. Keyed by the tag string. */
    fun taggedSnapshot(): Map<String, CompositionHistory> =
        taggedInstanceEntries.mapValues { (tag, entry) -> historyOf(entry.key, tag, entry) }

    /**
     * Clears history for a specific instance. Called when the instance leaves the
     * composition (navigation away) so it starts fresh when it reappears.
     */
    fun clearInstance(instanceId: String) {
        instanceEntries.remove(instanceId)
        _instances.value = instancesSnapshot()
    }

    /** Aggregated per composable key. One entry per tracked function. */
    fun snapshot(): Map<String, CompositionHistory> =
        entries.mapValues { (key, entry) -> historyOf(key, key, entry) }

    /** One history per on-screen instance (key + compound-key hash). */
    fun instancesSnapshot(): List<CompositionHistory> =
        instanceEntries.values.map { entry -> historyOf(entry.key, entry.instanceId, entry) }

    private fun historyOf(key: String, instanceId: String, entry: Entry): CompositionHistory =
        synchronized(entry) {
            val now = timeSource()
            val windowRecompositions = entry.records.count { now - it.timestampNs <= windowNs }
            // Records still awaiting recordEnd (durationNs == DURATION_UNSET) don't count
            val totalDurationMs = entry.records
                .filter { it.durationNs >= 0 }
                .sumOf { it.durationNs }
                .toFloat() / 1_000_000f
            CompositionHistory(
                key = key,
                instanceId = instanceId,
                file = entry.file,
                line = entry.line,
                records = entry.records.toList(),
                totalCompositions = entry.records.size,
                windowRecompositions = windowRecompositions,
                totalDurationMs = totalDurationMs,
                blamedParams = computeBlame(entry.records.toList()),
            )
        }

    /**
     * Ranks parameters (and local state) by how many recompositions they changed in.
     * Parameters that never changed are added at the end with a count of 0, so the
     * blame bar can show them as stable.
     */
    private fun computeBlame(records: List<RecompositionRecord>): List<BlamedParam> {
        if (records.isEmpty()) return emptyList()
        val counts = mutableMapOf<String, Int>()
        val verdicts = mutableMapOf<String, ParamVerdict>()
        val stateNames = mutableSetOf<String>()
        for (record in records) {
            for (snap in record.params) {
                if (snap.verdict == ParamVerdict.Changed || snap.verdict == ParamVerdict.LambdaIdentity) {
                    counts[snap.name] = (counts[snap.name] ?: 0) + 1
                    verdicts[snap.name] = snap.verdict
                }
            }
            // Local MutableState reads that changed also drove this recomposition.
            for (snap in record.stateChanges) {
                if (snap.verdict == ParamVerdict.Changed) {
                    counts[snap.name] = (counts[snap.name] ?: 0) + 1
                    verdicts[snap.name] = snap.verdict
                    stateNames += snap.name
                }
            }
        }
        val total = counts.values.sum().toFloat().coerceAtLeast(1f)
        val blamed = counts.entries
            .sortedByDescending { it.value }
            .map { (name, count) ->
                val verdict = verdicts[name] ?: ParamVerdict.Changed
                val isState = name in stateNames
                BlamedParam(
                    name = name,
                    recompositionCount = count,
                    fraction = count / total,
                    dominantVerdict = verdict,
                    suggestion = if (isState) null else SuggestionEngine.forBlame(name, verdict, count, count / total),
                    isState = isState,
                )
            }
        // The blame bar also lists params that never contributed, so a stable
        // parameter is visible as "0x" instead of being missing.
        val stable = records.first().params
            .filter { it.name !in counts && it.verdict != ParamVerdict.FirstComposition }
            .map { BlamedParam(it.name, 0, 0f, ParamVerdict.Unchanged) }
        return blamed + stable
    }
}
