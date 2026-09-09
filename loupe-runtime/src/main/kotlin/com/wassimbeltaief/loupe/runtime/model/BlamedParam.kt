package com.wassimbeltaief.loupe.runtime.model

data class BlamedParam(
    val name: String,
    val recompositionCount: Int,
    val fraction: Float,
    val dominantVerdict: ParamVerdict,
    val suggestion: String? = null,
)
