package com.wassimbeltaief.loupe.runtime.reporting

import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import com.wassimbeltaief.loupe.runtime.model.ParamSnapshot
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import com.wassimbeltaief.loupe.runtime.model.RecompositionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogcatFormatterTest {

    private fun history(
        window: Int,
        blamed: List<BlamedParam> = emptyList(),
    ) = RecompositionHistory(
        key = "ProductCard",
        file = "ProductCard.kt",
        line = 42,
        records = emptyList(),
        totalRecompositions = 87,
        windowRecompositions = window,
        totalDurationMs = 12.3f,
        blamedParams = blamed,
    )

    @Test
    fun `hot composable gets red emoji and full block`() {
        val lines = LogcatFormatter.summaryLines(
            history(20, listOf(BlamedParam("onClick", 87, 1.0f, ParamVerdict.LambdaIdentity, "wrap in remember {}"))),
            windowSeconds = 5,
        )
        assertTrue(lines[1].startsWith("🔴 ProductCard  20x in 5s  (12.3ms total)"), "got: ${lines[1]}")
        assertTrue(lines.any { it.contains("├─ blame:") })
        assertTrue(lines.any { it.contains("onClick") && it.contains("lambda identity") && it.contains("→") })
        assertEquals(lines.first(), lines.last(), "block is wrapped in rules")
    }

    @Test
    fun `warm composable gets amber emoji`() {
        val lines = LogcatFormatter.summaryLines(history(5), 5)
        assertTrue(lines[1].startsWith("🟠"))
    }

    @Test
    fun `no blame section when nothing blamed`() {
        val lines = LogcatFormatter.summaryLines(history(6), 5)
        assertTrue(lines.none { it.contains("blame:") })
    }

    @Test
    fun `stable param renders with check mark`() {
        val lines = LogcatFormatter.summaryLines(
            history(6, listOf(BlamedParam("title", 0, 0f, ParamVerdict.Unchanged))),
            5,
        )
        assertTrue(lines.any { it.contains("title") && it.contains("stable ✓") })
    }

    @Test
    fun `verbose line lists changed params`() {
        val record = RecompositionRecord(
            key = "Card", file = "Card.kt", line = 1, timestampNs = 0L,
            params = listOf(
                ParamSnapshot("price", "19.99", "21.99", ParamVerdict.Changed),
                ParamSnapshot("title", "a", "a", ParamVerdict.Unchanged),
            ),
            durationNs = 0L, wasForced = false,
        )
        val line = LogcatFormatter.verboseLine(record)
        assertTrue(line.contains("price: 19.99 → 21.99"), "got: $line")
        assertTrue(!line.contains("title"), "unchanged params not listed, got: $line")
    }

    @Test
    fun `verbose line marks forced recompositions`() {
        val record = RecompositionRecord(
            key = "Card", file = "Card.kt", line = 1, timestampNs = 0L,
            params = listOf(ParamSnapshot("a", "1", "1", ParamVerdict.Unchanged)),
            durationNs = 0L, wasForced = true,
        )
        assertTrue(LogcatFormatter.verboseLine(record).contains("[forced]"))
    }

    @Test
    fun `state blame renders as state change`() {
        val lines = LogcatFormatter.summaryLines(
            history(6, listOf(BlamedParam("counter", 5, 1f, ParamVerdict.Changed, isState = true))),
            5,
        )
        assertTrue(lines.any { it.contains("counter") && it.contains("state change") })
    }

    @Test
    fun `verbose line lists changed state`() {
        val record = RecompositionRecord(
            key = "Card", file = "Card.kt", line = 1, timestampNs = 0L,
            params = emptyList(),
            durationNs = 0L,
            wasForced = true,
            stateChanges = listOf(ParamSnapshot("counter", "0", "1", ParamVerdict.Changed)),
        )
        assertTrue(LogcatFormatter.verboseLine(record).contains("counter(state): 0 → 1"))
    }
}
