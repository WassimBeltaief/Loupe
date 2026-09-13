package com.wassimbeltaief.loupe.runtime.model

/**
 * One recomposition event for one composable instance.
 *
 * A record is created by the compiler-injected `record()` call and closed by
 * `recordEnd()`, which fills in [durationNs].
 */
data class RecompositionRecord(
    val key: String,
    /** Source file where the composable is declared. */
    val file: String,
    /** Source line of the composable declaration. */
    val line: Int,
    val timestampNs: Long,
    /** Parameters captured at the start of this recomposition. */
    val params: List<ParamSnapshot>,
    /** Time the composable body took, or -1 while it is still running. */
    val durationNs: Long,
    /** True when every parameter was unchanged, so the recomposition was forced. */
    val wasForced: Boolean,
    /** Local `remember { mutableStateOf(...) }` values that changed in this pass. */
    val stateChanges: List<ParamSnapshot> = emptyList(),
)
