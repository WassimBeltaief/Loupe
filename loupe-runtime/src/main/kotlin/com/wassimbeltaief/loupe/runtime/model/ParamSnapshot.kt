package com.wassimbeltaief.loupe.runtime.model

data class ParamSnapshot(
    val name: String,
    val previousValue: String,
    val currentValue: String,
    val verdict: ParamVerdict,
    val suggestion: String? = null,
)
