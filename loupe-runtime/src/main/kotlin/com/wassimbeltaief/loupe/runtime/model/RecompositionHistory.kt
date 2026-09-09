package com.wassimbeltaief.loupe.runtime.model

data class RecompositionHistory(
    val key: String,
    val file: String,
    val line: Int,
    val records: List<RecompositionRecord>,
    val totalRecompositions: Int,
    val windowRecompositions: Int,
    val totalDurationMs: Float,
    val blamedParams: List<BlamedParam>,
)
