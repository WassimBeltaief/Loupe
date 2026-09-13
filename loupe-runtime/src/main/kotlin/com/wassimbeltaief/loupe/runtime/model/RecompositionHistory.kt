package com.wassimbeltaief.loupe.runtime.model

/**
 * All we know about one composable instance: its recent records, its counts and
 * its ranked blame.
 *
 * The overlay shows one row per instance. The aggregate view (one row per
 * composable function) also uses this type, with [instanceId] equal to [key].
 */
data class RecompositionHistory(
    val key: String,
    /** Unique id for one instance (`key#compoundKeyHash`). Equals [key] for aggregates. */
    val instanceId: String = key,
    val file: String,
    val line: Int,
    /** Records for this instance, newest first, capped by the circular buffer. */
    val records: List<RecompositionRecord>,
    val totalRecompositions: Int,
    /** Recompositions in the last `windowSeconds` seconds. */
    val windowRecompositions: Int,
    val totalDurationMs: Float,
    /** Parameters ranked by how many recompositions they caused. */
    val blamedParams: List<BlamedParam>,
)
