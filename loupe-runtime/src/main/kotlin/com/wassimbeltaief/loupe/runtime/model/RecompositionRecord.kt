package com.wassimbeltaief.loupe.runtime.model

data class RecompositionRecord(
    val key: String,
    val file: String,
    val line: Int,
    val timestampNs: Long,
    val params: List<ParamSnapshot>,
    val durationNs: Long,
    val wasForced: Boolean,
)
