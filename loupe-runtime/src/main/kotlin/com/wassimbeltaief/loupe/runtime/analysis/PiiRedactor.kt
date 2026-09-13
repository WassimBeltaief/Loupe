package com.wassimbeltaief.loupe.runtime.analysis

/**
 * A safety net for personal data in string parameter values.
 *
 * Emails and card-like numbers are replaced with "[redacted]" before they reach
 * the registry, the overlay or the JSON export.
 *
 * It only checks top-level strings. Personal data hidden inside another type's
 * `toString()` output is not caught here, so that type should use `@LoupeRedact`.
 */
internal object PiiRedactor {

    const val REDACTED = "[redacted]"

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // 13 to 19 digits, spaces and dashes allowed as separators.
    private val CARD_LIKE = Regex("^[\\d][\\d -]{11,21}[\\d]$")

    /** Returns the value unchanged, or [REDACTED] when it looks like personal data. */
    fun redact(value: String): String {
        val trimmed = value.trim()
        if (EMAIL.matches(trimmed)) return REDACTED
        if (CARD_LIKE.matches(trimmed) && trimmed.count { it.isDigit() } in 13..19) return REDACTED
        return value
    }
}
