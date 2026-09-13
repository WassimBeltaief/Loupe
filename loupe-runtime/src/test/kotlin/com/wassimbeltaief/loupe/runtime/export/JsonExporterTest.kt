package com.wassimbeltaief.loupe.runtime.export

import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import com.wassimbeltaief.loupe.runtime.model.ParamSnapshot
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.CompositionHistory
import com.wassimbeltaief.loupe.runtime.model.RecompositionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonExporterTest {

    private fun history() = CompositionHistory(
        key = "ProductCard",
        file = "ProductCard.kt",
        line = 42,
        records = listOf(
            RecompositionRecord(
                key = "ProductCard",
                file = "ProductCard.kt",
                line = 42,
                timestampNs = 1_000_000_000L,
                params = listOf(
                    ParamSnapshot("price", "19.99", "21.99", ParamVerdict.Changed),
                    ParamSnapshot("onClick", "lambda@1", "lambda@2", ParamVerdict.LambdaIdentity,
                        "onClick's identity changed on every recomposition."),
                ),
                durationNs = 2_000_000L,
                wasForced = false,
            ),
        ),
        totalCompositions = 1,
        windowRecompositions = 1,
        totalDurationMs = 2.0f,
        blamedParams = listOf(
            BlamedParam("onClick", 1, 1.0f, ParamVerdict.LambdaIdentity, "wrap in remember {}"),
        ),
    )

    @Test
    fun `serialises full history structure`() {
        val json = JsonExporter.historyToJson(history())
        assertTrue(json.startsWith("{") && json.endsWith("}"))
        assertTrue(json.contains("\"key\":\"ProductCard\""))
        assertTrue(json.contains("\"file\":\"ProductCard.kt\""))
        assertTrue(json.contains("\"line\":42"))
        assertTrue(json.contains("\"totalCompositions\":1"))
        assertTrue(json.contains("\"blamedParams\":["))
        assertTrue(json.contains("\"records\":["))
        assertTrue(json.contains("\"verdict\":\"LambdaIdentity\""))
        assertTrue(json.contains("\"durationNs\":2000000"))
        assertTrue(json.contains("\"wasForced\":false"))
    }

    @Test
    fun `null suggestion serialised as JSON null`() {
        val json = JsonExporter.historyToJson(history())
        assertTrue(json.contains("\"name\":\"price\""))
        // price has no suggestion
        assertTrue(json.contains("\"suggestion\":null"))
    }

    @Test
    fun `escapes quotes, backslashes and newlines in values`() {
        assertEquals("\\\"", JsonExporter.escape("\""))
        assertEquals("\\\\", JsonExporter.escape("\\"))
        assertEquals("\\n", JsonExporter.escape("\n"))
        assertEquals("a\\\"b\\\\c\\nd", JsonExporter.escape("a\"b\\c\nd"))
    }

    @Test
    fun `escapes control characters as unicode`() {
        assertEquals("\\u0001", JsonExporter.escape("\u0001"))
        assertEquals("\\u001f", JsonExporter.escape("\u001F"))
    }

    @Test
    fun `plain ascii is untouched`() {
        assertEquals("hello world 123", JsonExporter.escape("hello world 123"))
    }

    @Test
    fun `empty records serialise as empty array`() {
        val h = history().copy(records = emptyList())
        val json = JsonExporter.historyToJson(h)
        assertTrue(json.contains("\"records\":[]"))
    }

    @Test
    fun `serialises state blame flag`() {
        val h = history().copy(
            blamedParams = listOf(BlamedParam("counter", 5, 1f, ParamVerdict.Changed, isState = true)),
        )
        val json = JsonExporter.historyToJson(h)
        assertTrue(json.contains("\"name\":\"counter\""))
        assertTrue(json.contains("\"state\":true"))
    }

    @Test
    fun `serialises state changes`() {
        val record = history().records.first().copy(
            stateChanges = listOf(ParamSnapshot("counter", "0", "1", ParamVerdict.Changed)),
        )
        val json = JsonExporter.historyToJson(history().copy(records = listOf(record)))
        assertTrue(json.contains("\"stateChanges\":[{"))
        assertTrue(json.contains("\"name\":\"counter\""))
        assertTrue(json.contains("\"previousValue\":\"0\""))
        assertTrue(json.contains("\"currentValue\":\"1\""))
    }

    @Test
    fun `no trailing commas — valid JSON shape around arrays`() {
        val json = JsonExporter.historyToJson(history())
        assertFalse(json.contains(",]"), "trailing comma before ] in: $json")
        assertFalse(json.contains(",}"), "trailing comma before } in: $json")
    }
}
