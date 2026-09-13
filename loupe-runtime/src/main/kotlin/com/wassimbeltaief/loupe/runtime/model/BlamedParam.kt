package com.wassimbeltaief.loupe.runtime.model

data class BlamedParam(
    val name: String,
    val recompositionCount: Int,
    val fraction: Float,
    val dominantVerdict: ParamVerdict,
    val suggestion: String? = null,
    /** True when this entry comes from a local `MutableState` read rather than a parameter. */
    val isState: Boolean = false,
)
