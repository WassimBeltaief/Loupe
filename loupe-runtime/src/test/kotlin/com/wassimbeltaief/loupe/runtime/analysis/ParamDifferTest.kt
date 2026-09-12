package com.wassimbeltaief.loupe.runtime.analysis

import com.wassimbeltaief.loupe.runtime.model.LambdaRef
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import kotlin.test.Test
import kotlin.test.assertEquals

class ParamDifferTest {

    @Test
    fun `first composition — no previous state`() {
        val snapshots = ParamDiffer.diff(
            previous = emptyMap(),
            current = arrayOf("price" to 9.99, "title" to "Widget"),
        )
        assertEquals(2, snapshots.size)
        snapshots.forEach { assertEquals(ParamVerdict.FirstComposition, it.verdict) }
        assertEquals("", snapshots[0].previousValue)
        assertEquals("9.99", snapshots[0].currentValue)
    }

    @Test
    fun `detects unchanged value`() {
        val snapshot = ParamDiffer.diff(
            previous = mapOf("price" to 9.99),
            current = arrayOf("price" to 9.99),
        ).single()
        assertEquals(ParamVerdict.Unchanged, snapshot.verdict)
    }

    @Test
    fun `detects value change`() {
        val snapshot = ParamDiffer.diff(
            previous = mapOf("price" to 9.99),
            current = arrayOf("price" to 21.99),
        ).single()
        assertEquals(ParamVerdict.Changed, snapshot.verdict)
        assertEquals("9.99", snapshot.previousValue)
        assertEquals("21.99", snapshot.currentValue)
    }

    @Test
    fun `detects lambda identity change`() {
        val snapshot = ParamDiffer.diff(
            previous = mapOf("onClick" to LambdaRef(1)),
            current = arrayOf("onClick" to LambdaRef(2)),
        ).single()
        assertEquals(ParamVerdict.LambdaIdentity, snapshot.verdict)
    }

    @Test
    fun `lambda identity unchanged when hash is the same`() {
        val ref = LambdaRef(42)
        val snapshot = ParamDiffer.diff(
            previous = mapOf("onClick" to ref),
            current = arrayOf("onClick" to ref),
        ).single()
        assertEquals(ParamVerdict.Unchanged, snapshot.verdict)
    }

    @Test
    fun `handles null values`() {
        val snapshot = ParamDiffer.diff(
            previous = mapOf("item" to null),
            current = arrayOf("item" to null),
        ).single()
        assertEquals(ParamVerdict.Unchanged, snapshot.verdict)
        assertEquals("null", snapshot.currentValue)
    }

    @Test
    fun `null to non-null is a change`() {
        val snapshot = ParamDiffer.diff(
            previous = mapOf("item" to null),
            current = arrayOf("item" to "hello"),
        ).single()
        assertEquals(ParamVerdict.Changed, snapshot.verdict)
    }

    @Test
    fun `truncates long values to 120 chars`() {
        val long = "x".repeat(200)
        val snapshot = ParamDiffer.diff(
            previous = mapOf("s" to long),
            current = arrayOf("s" to long),
        ).single()
        assertEquals(120, snapshot.currentValue.length)
    }

    // ── #38: hostile host-app objects must never crash the app ──────────────

    private class ThrowingToString {
        override fun toString(): String = throw RuntimeException("boom")
    }

    @Test
    fun `throwing toString produces placeholder, no crash`() {
        val snapshot = ParamDiffer.diff(
            previous = emptyMap(),
            current = arrayOf("obj" to ThrowingToString()),
        ).single()
        assertEquals("<toString error>", snapshot.currentValue)
    }

    private class ThrowingEquals {
        override fun equals(other: Any?): Boolean = throw RuntimeException("boom")
        override fun hashCode(): Int = 0
    }

    @Test
    fun `throwing equals is treated as changed, no crash`() {
        val snapshot = ParamDiffer.diff(
            previous = mapOf("obj" to ThrowingEquals()),
            current = arrayOf("obj" to ThrowingEquals()),
        ).single()
        assertEquals(ParamVerdict.Changed, snapshot.verdict)
    }

    // ── #20: PII auto-redaction ──────────────────────────────────────────────

    @Test
    fun `email addresses are redacted`() {
        val snapshot = ParamDiffer.diff(
            previous = emptyMap(),
            current = arrayOf("email" to "qa.tester@company.com"),
        ).single()
        assertEquals("[redacted]", snapshot.currentValue)
    }

    @Test
    fun `credit-card-like numbers are redacted`() {
        for (card in listOf("4111111111111111", "4111 1111 1111 1111", "4111-1111-1111-1111")) {
            val snapshot = ParamDiffer.diff(
                previous = emptyMap(),
                current = arrayOf("card" to card),
            ).single()
            assertEquals("[redacted]", snapshot.currentValue, "expected redaction for: $card")
        }
    }

    @Test
    fun `ordinary strings are not redacted`() {
        for (safe in listOf("Widget Pro", "12345", "2026-09-12", "user@host", "42")) {
            val snapshot = ParamDiffer.diff(
                previous = emptyMap(),
                current = arrayOf("s" to safe),
            ).single()
            assertEquals(safe, snapshot.currentValue, "must NOT redact: $safe")
        }
    }

    @Test
    fun `redaction applies to previous value too`() {
        val snapshot = ParamDiffer.diff(
            previous = mapOf("email" to "old@company.com"),
            current = arrayOf("email" to "new@company.com"),
        ).single()
        assertEquals("[redacted]", snapshot.previousValue)
        assertEquals("[redacted]", snapshot.currentValue)
    }
}
