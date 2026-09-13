package com.wassimbeltaief.loupe.runtime.model

/**
 * One parameter at one recomposition.
 *
 * Both values are stored as text (shortened to 120 characters) so the overlay
 * and the JSON export can show them safely, whatever their type.
 */
data class ParamSnapshot(
    val name: String,
    /** How the value looked before this recomposition. */
    val previousValue: String,
    /** How the value looks now. */
    val currentValue: String,
    val verdict: ParamVerdict,
    /** A fix suggestion for this parameter, or null when nothing applies. */
    val suggestion: String? = null,
)
