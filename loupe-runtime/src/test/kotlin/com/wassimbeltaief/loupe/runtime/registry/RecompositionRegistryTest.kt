package com.wassimbeltaief.loupe.runtime.registry

import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecompositionRegistryTest {

    private fun registry(
        maxEntries: Int = 500,
        windowNs: Long = 5_000_000_000L,
        time: () -> Long = System::nanoTime,
    ) = RecompositionRegistry(maxEntries, windowNs, time)

    @Test
    fun `circular buffer caps at maxHistoryEntries`() {
        val reg = registry(maxEntries = 3)
        repeat(5) { i ->
            reg.record("Card", "Card.kt", 1, arrayOf("n" to i))
        }
        assertEquals(3, reg.snapshot()["Card"]!!.records.size)
    }

    @Test
    fun `records are newest first`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 2))
        val records = reg.snapshot()["Card"]!!.records
        assertEquals(2, (records[0].params[0].currentValue).toInt())
        assertEquals(1, (records[1].params[0].currentValue).toInt())
    }

    @Test
    fun `rolling window counting`() {
        var nowNs = 0L
        val reg = registry(windowNs = 5_000_000_000L, time = { nowNs })

        nowNs = 0L
        reg.record("A", "A.kt", 1, arrayOf("x" to 1))        // t=0

        nowNs = 2_000_000_000L
        reg.record("A", "A.kt", 1, arrayOf("x" to 2))        // t=2s

        nowNs = 7_000_000_000L
        reg.record("A", "A.kt", 1, arrayOf("x" to 3))        // t=7s

        // window [2s, 7s] → records at t=2s and t=7s inside, t=0 outside → 2
        val history = reg.snapshot()["A"]!!
        assertEquals(3, history.totalRecompositions)
        assertEquals(2, history.windowRecompositions)
    }

    @Test
    fun `reset clears all history`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        reg.reset()
        assertTrue(reg.snapshot().isEmpty())
    }

    @Test
    fun `wasForced is true when all params unchanged on recomposition`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        val latest = reg.snapshot()["Card"]!!.records.first()
        assertTrue(latest.wasForced)
    }

    @Test
    fun `wasForced is false on first composition`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        val latest = reg.snapshot()["Card"]!!.records.first()
        assertTrue(!latest.wasForced)
    }

    @Test
    fun `blame ranks params by change count`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, arrayOf("a" to 1, "b" to 10))
        reg.record("Card", "Card.kt", 1, arrayOf("a" to 2, "b" to 11))
        reg.record("Card", "Card.kt", 1, arrayOf("a" to 2, "b" to 12))
        val blame = reg.snapshot()["Card"]!!.blamedParams
        assertEquals("b", blame[0].name)
        assertEquals(2, blame[0].recompositionCount)
        assertEquals("a", blame[1].name)
        assertEquals(1, blame[1].recompositionCount)
        assertEquals(ParamVerdict.Changed, blame[0].dominantVerdict)
    }

    @Test
    fun `state flow emits updated snapshot after record`() {
        val reg = registry()
        assertTrue(reg.state.value.isEmpty())
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        assertEquals(1, reg.state.value["Card"]!!.totalRecompositions)
    }
}
