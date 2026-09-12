package com.wassimbeltaief.loupe.runtime.analysis

import com.wassimbeltaief.loupe.runtime.model.ParamSnapshot
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BurstGrouperTest {

    private fun record(
        timestampNs: Long,
        vararg changedParams: String,
        durationNs: Long = 1_000_000L,
    ) = RecompositionRecord(
        key = "Card",
        file = "Card.kt",
        line = 1,
        timestampNs = timestampNs,
        params = changedParams.map {
            ParamSnapshot(it, "0", "1", if (it.startsWith("on")) ParamVerdict.LambdaIdentity else ParamVerdict.Changed)
        },
        durationNs = durationNs,
        wasForced = false,
    )

    @Test
    fun `empty history produces no bursts`() {
        assertTrue(BurstGrouper.group(emptyList()).isEmpty())
    }

    @Test
    fun `records within 200ms collapse into one burst`() {
        // newest-first
        val records = listOf(
            record(1_300_000_000L, "price"),
            record(1_200_000_000L, "price"),
            record(1_050_000_000L, "onClick"),
        )
        val bursts = BurstGrouper.group(records)
        assertEquals(1, bursts.size)
        assertEquals(3, bursts[0].count)
        assertEquals(1, bursts[0].firstIndex, "oldest in burst = recomposition 1")
        assertEquals(3, bursts[0].lastIndex, "newest in burst = recomposition 3")
    }

    @Test
    fun `gap over 200ms starts a new burst, newest first`() {
        val records = listOf(
            record(5_000_000_000L, "price"),       // recomposition 3
            record(4_900_000_000L, "onClick"),     // recomposition 2 — same burst as 3
            record(1_000_000_000L, "price"),       // recomposition 1 — 3.9s gap → own burst
        )
        val bursts = BurstGrouper.group(records)
        assertEquals(2, bursts.size)
        assertEquals(2, bursts[0].count)
        assertEquals(2, bursts[0].firstIndex)
        assertEquals(3, bursts[0].lastIndex)
        assertEquals(1, bursts[1].count)
        assertEquals(1, bursts[1].firstIndex)
        assertEquals(1, bursts[1].lastIndex)
    }

    @Test
    fun `dominant changed param is the most frequent across the burst`() {
        val records = listOf(
            record(1_300_000_000L, "onClick", "price"),
            record(1_200_000_000L, "onClick"),
            record(1_100_000_000L, "onClick", "price"),
        )
        assertEquals("onClick", BurstGrouper.group(records)[0].dominantChangedParam)
    }

    @Test
    fun `burst of unchanged-only records has no dominant param`() {
        val records = listOf(record(1_000_000_000L))
        assertNull(BurstGrouper.group(records)[0].dominantChangedParam)
    }

    @Test
    fun `total duration sums only measured records`() {
        val records = listOf(
            record(1_200_000_000L, "a", durationNs = 2_000_000L),
            record(1_100_000_000L, "a", durationNs = -1L), // DURATION_UNSET
        )
        assertEquals(2_000_000L, BurstGrouper.group(records)[0].totalDurationNs)
    }
}
