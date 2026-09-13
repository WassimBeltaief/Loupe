package com.wassimbeltaief.loupe.runtime.model

/**
 * One entry in the parameter blame list: a parameter (or local state) and how
 * much it contributed to recompositions.
 *
 * Used by the blame bar and by the CI report, so a failure points at the exact
 * thing that needs fixing.
 */
data class BlamedParam(
    val name: String,
    /** How many recompositions this parameter changed in. */
    val recompositionCount: Int,
    /** Share of the total blame, from 0.0 to 1.0. */
    val fraction: Float,
    val dominantVerdict: ParamVerdict,
    /** A fix suggestion, or null when the parameter looks fine. */
    val suggestion: String? = null,
    /** True when this came from a local `MutableState` read, not a parameter. */
    val isState: Boolean = false,
)
