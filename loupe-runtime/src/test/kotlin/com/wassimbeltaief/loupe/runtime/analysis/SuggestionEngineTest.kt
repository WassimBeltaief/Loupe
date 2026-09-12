package com.wassimbeltaief.loupe.runtime.analysis

import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestionEngineTest {

    // ── Per-value rules ──────────────────────────────────────────────────────

    @Test
    fun `suggests ImmutableList for MutableList`() {
        val s = SuggestionEngine.forChangedValue("items", mutableListOf<String>())
        assertTrue(s!!.contains("ImmutableList"), "expected ImmutableList suggestion, got: $s")
        assertTrue(s.contains("items"))
    }

    @Test
    fun `suggests ImmutableList for ArrayList`() {
        val s = SuggestionEngine.forChangedValue("items", arrayListOf(1, 2))
        assertTrue(s!!.contains("ImmutableList"), "expected ImmutableList suggestion, got: $s")
    }

    @Test
    fun `suggests ImmutableMap for HashMap`() {
        val s = SuggestionEngine.forChangedValue("lookup", hashMapOf("a" to 1))
        assertTrue(s!!.contains("ImmutableMap"), "expected ImmutableMap suggestion, got: $s")
    }

    @Test
    fun `no suggestion for stable values`() {
        assertNull(SuggestionEngine.forChangedValue("title", "hello"))
        assertNull(SuggestionEngine.forChangedValue("price", 19.99))
        assertNull(SuggestionEngine.forChangedValue("items", listOf(1, 2)))
        assertNull(SuggestionEngine.forChangedValue("nothing", null))
    }

    // ── Blame-level lambda rules ─────────────────────────────────────────────

    @Test
    fun `lambda changed on every recomposition gets ambiguity-aware wording`() {
        val s = SuggestionEngine.forBlame("onClick", ParamVerdict.LambdaIdentity, 87, 1.0f)
        assertTrue(s!!.contains("every recomposition"))
        assertTrue(s.contains("remember {}"), "should suggest remember, got: $s")
        // Must acknowledge ambiguity — never a false alarm
        assertTrue(s.contains("this is expected"), "must acknowledge legitimate closure capture, got: $s")
    }

    @Test
    fun `lambda changed on some recompositions gets remember-key wording`() {
        val s = SuggestionEngine.forBlame("onClick", ParamVerdict.LambdaIdentity, 10, 0.5f)
        assertTrue(s!!.contains("remember(key)"), "expected remember(key) suggestion, got: $s")
    }

    @Test
    fun `low-count lambda churn gets no suggestion`() {
        assertNull(SuggestionEngine.forBlame("onClick", ParamVerdict.LambdaIdentity, 2, 0.5f))
    }

    @Test
    fun `non-lambda verdicts get no blame suggestion`() {
        assertNull(SuggestionEngine.forBlame("price", ParamVerdict.Changed, 50, 1.0f))
        assertNull(SuggestionEngine.forBlame("title", ParamVerdict.Unchanged, 50, 1.0f))
    }

    // ── Wiring: suggestion flows through ParamDiffer into ParamSnapshot ─────

    @Test
    fun `differ attaches suggestion to changed mutable-list param`() {
        val snapshot = ParamDiffer.diff(
            previous = mapOf("items" to mutableListOf("a")),
            current = arrayOf("items" to mutableListOf("a", "b")),
        ).single()
        assertEquals(ParamVerdict.Changed, snapshot.verdict)
        assertTrue(snapshot.suggestion!!.contains("ImmutableList"))
    }

    @Test
    fun `differ leaves suggestion null for unchanged and stable params`() {
        val snapshot = ParamDiffer.diff(
            previous = mapOf("title" to "a"),
            current = arrayOf("title" to "b"),
        ).single()
        assertEquals(ParamVerdict.Changed, snapshot.verdict)
        assertNull(snapshot.suggestion)
    }
}
