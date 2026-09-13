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
        assertEquals(3, history.totalCompositions)
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
        assertEquals(1, reg.state.value["Card"]!!.totalCompositions)
    }

    // ── #36: duration measurement ────────────────────────────────────────────

    @Test
    fun `recordEnd sets duration on the matching record`() {
        var now = 1_000_000_000L
        val reg = registry(time = { now })
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        now += 2_500_000L // body took 2.5ms
        reg.recordEnd("Card")

        val history = reg.snapshot()["Card"]!!
        assertEquals(2_500_000L, history.records[0].durationNs)
        assertEquals(2.5f, history.totalDurationMs, 0.001f)
    }

    @Test
    fun `recordEnd pairs LIFO for recursive composables`() {
        var now = 1_000_000_000L
        val reg = registry(time = { now })
        reg.record("Tree", "Tree.kt", 1, arrayOf("d" to 0))   // outer
        now += 100_000L
        reg.record("Tree", "Tree.kt", 1, arrayOf("d" to 1))   // inner (recursive)
        now += 100_000L
        reg.recordEnd("Tree")                                  // inner ends first
        now += 100_000L
        reg.recordEnd("Tree")                                  // then outer

        val records = reg.snapshot()["Tree"]!!.records
        // records are newest-first: [inner, outer]
        assertEquals(100_000L, records[0].durationNs, "inner duration")
        assertEquals(300_000L, records[1].durationNs, "outer duration")
    }

    @Test
    fun `recordEnd without a matching record is a no-op`() {
        val reg = registry()
        reg.recordEnd("Ghost")           // key never recorded
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        reg.recordEnd("Card")
        reg.recordEnd("Card")            // double-end: second one has no unset record
        assertEquals(1, reg.snapshot()["Card"]!!.totalCompositions)
    }

    @Test
    fun `records without recordEnd are excluded from total duration`() {
        var now = 1_000_000_000L
        val reg = registry(time = { now })
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        now += 1_000_000L
        reg.recordEnd("Card")
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 2))   // never ended (exception in finally? registry swap?)

        val history = reg.snapshot()["Card"]!!
        assertEquals(2, history.totalCompositions)
        assertEquals(1.0f, history.totalDurationMs, 0.001f, "only the ended record contributes")
    }

    // ── per-instance tracking ────────────────────────────────────────────────

    @Test
    fun `same composable in two instances gets two histories and one aggregate`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1), instance = 100)
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1), instance = 200)

        val instances = reg.instances.value
        assertEquals(2, instances.size)
        assertTrue(instances.all { it.key == "Card" })
        assertEquals(2, instances.map { it.instanceId }.distinct().size)
        assertTrue(instances.all { it.totalCompositions == 1 }, "each instance counts its own recompositions")

        // Aggregate still combines them for the heatmap / CI report
        assertEquals(2, reg.snapshot()["Card"]!!.totalCompositions)
    }

    @Test
    fun `recordEnd updates the matching instance only`() {
        var now = 1_000_000_000L
        val reg = registry(time = { now })
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1), instance = 7)
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1), instance = 9)
        now += 3_000_000L
        reg.recordEnd("Card", instance = 7)

        val instances = reg.instances.value.associateBy { it.instanceId }
        assertEquals(3_000_000L, instances.getValue("Card#7").records[0].durationNs)
        assertEquals(RecompositionRegistry.DURATION_UNSET, instances.getValue("Card#9").records[0].durationNs)
    }

    @Test
    fun `reset clears per-instance histories`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1), instance = 1)
        assertTrue(reg.instances.value.isNotEmpty())
        reg.reset()
        assertTrue(reg.instances.value.isEmpty())
    }

    @Test
    fun `instances keep independent totals including the initial composition`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1), instance = 1)   // initial
        repeat(2) { reg.record("Card", "Card.kt", 1, arrayOf("n" to it), instance = 1) }
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1), instance = 2)   // initial only

        val byId = reg.instances.value.associateBy { it.instanceId }
        assertEquals(3, byId.getValue("Card#1").totalCompositions)
        assertEquals(1, byId.getValue("Card#2").totalCompositions)
    }

    @Test
    fun `tagged instance also appears in the overlay instance view`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, arrayOf("n" to 1), instance = 100, instanceTag = "Card_1")

        // A testTag indexes the instance for the testing API…
        assertEquals(1, reg.taggedSnapshot().getValue("Card_1").totalCompositions)
        // …and must not remove it from the overlay/heatmap view.
        assertTrue(reg.instances.value.any { it.instanceId == "Card#100" })
    }

    @Test
    fun `recordEnd and trackState update a tagged instance`() {
        var now = 1_000_000_000L
        val reg = registry(time = { now })
        reg.record("Card", "Card.kt", 1, emptyArray(), instance = 100, instanceTag = "Card_1")
        reg.trackState("Card", instance = 100, name = "counter", value = 0, instanceTag = "Card_1")
        now += 3_000_000L
        reg.recordEnd("Card", instance = 100, instanceTag = "Card_1")

        val tagged = reg.taggedSnapshot().getValue("Card_1")
        assertEquals(3_000_000L, tagged.records[0].durationNs)
        assertEquals(1, tagged.records[0].stateChanges.size)
    }

    // ── local state tracking ─────────────────────────────────────────────────

    @Test
    fun `trackState marks first composition then a changed value`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, emptyArray())
        reg.trackState("Card", name = "counter", value = 0)
        reg.record("Card", "Card.kt", 1, emptyArray())
        reg.trackState("Card", name = "counter", value = 1)

        val records = reg.snapshot()["Card"]!!.records
        assertEquals(ParamVerdict.FirstComposition, records[1].stateChanges.single().verdict)
        assertEquals(ParamVerdict.Changed, records[0].stateChanges.single().verdict)
        assertEquals("0", records[0].stateChanges.single().previousValue)
        assertEquals("1", records[0].stateChanges.single().currentValue)
    }

    @Test
    fun `trackState marks unchanged value on recomposition`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, emptyArray())
        reg.trackState("Card", name = "counter", value = 5)
        reg.record("Card", "Card.kt", 1, emptyArray())
        reg.trackState("Card", name = "counter", value = 5)

        val latest = reg.snapshot()["Card"]!!.records.first()
        assertEquals(ParamVerdict.Unchanged, latest.stateChanges.single().verdict)
    }

    @Test
    fun `trackState updates the matching instance only`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, emptyArray(), instance = 7)
        reg.trackState("Card", instance = 7, name = "counter", value = 1)
        reg.record("Card", "Card.kt", 1, emptyArray(), instance = 9)

        val byId = reg.instances.value.associateBy { it.instanceId }
        assertEquals(1, byId.getValue("Card#7").records.first().stateChanges.size)
        assertTrue(byId.getValue("Card#9").records.first().stateChanges.isEmpty())
    }

    @Test
    fun `blame includes changed local state and keeps stable params`() {
        val reg = registry()
        reg.record("Card", "Card.kt", 1, arrayOf("title" to "x"))
        reg.trackState("Card", name = "counter", value = 0)
        repeat(2) { i ->
            reg.record("Card", "Card.kt", 1, arrayOf("title" to "x"))
            reg.trackState("Card", name = "counter", value = i + 1)
        }

        val blame = reg.snapshot()["Card"]!!.blamedParams
        val counter = blame.first { it.name == "counter" }
        assertEquals(2, counter.recompositionCount)
        assertTrue(counter.isState)
        assertTrue(blame.any { it.name == "title" && it.recompositionCount == 0 })
    }
}
