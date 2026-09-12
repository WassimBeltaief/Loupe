package com.wassimbeltaief.loupe.runtime.analysis

/**
 * Safety net for PII in captured String parameter values (#20). Email addresses
 * and credit-card-like numeric sequences are replaced with "[redacted]" before
 * they ever reach the registry, overlay, or JSON export.
 *
 * Deliberately conservative: only top-level String params are scanned. PII
 * embedded in other types' toString() output needs @LoupeRedact on the type.
 */
internal object PiiRedactor {

    const val REDACTED = "[redacted]"

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // 13–19 digits allowing spaces/dashes as separators (card-like sequences)
    private val CARD_LIKE = Regex("^[\\d][\\d -]{11,21}[\\d]$")

    fun redact(value: String): String {
        val trimmed = value.trim()
        if (EMAIL.matches(trimmed)) return REDACTED
        if (CARD_LIKE.matches(trimmed) && trimmed.count { it.isDigit() } in 13..19) return REDACTED
        return value
    }
}
