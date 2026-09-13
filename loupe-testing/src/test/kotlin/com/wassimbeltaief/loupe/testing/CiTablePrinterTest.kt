package com.wassimbeltaief.loupe.testing

import com.wassimbeltaief.loupe.runtime.LoupeReport
import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.CompositionHistory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class CiTablePrinterTest {

    private fun history(key: String, total: Int, durationMs: Float = 0f) = CompositionHistory(
        key = key,
        instanceId = key,
        file = "$key.kt",
        line = 1,
        records = emptyList(),
        totalCompositions = total,
        windowRecompositions = total,
        totalDurationMs = durationMs,
        blamedParams = emptyList(),
    )

    @Test
    fun tableContainsHeader() {
        val report = LoupeReport(
            durationMs = 100,
            composables = mapOf("AlbumCard" to history("AlbumCard", 3, 4.2f)),
            totalCompositions = 3,
            hotComposables = emptyList(),
            warmComposables = emptyList(),
        )
        val table = CiTablePrinter.format(report)
        assertContains(table, "Composable")
        assertContains(table, "Count")
        assertContains(table, "Cost(ms)")
        assertContains(table, "Top blame")
        assertContains(table, "Status")
    }

    @Test
    fun tableContainsComposableName() {
        val report = LoupeReport(
            durationMs = 50,
            composables = mapOf("ProductCard" to history("ProductCard", 5, 2.0f)),
            totalCompositions = 5,
            hotComposables = emptyList(),
            warmComposables = emptyList(),
        )
        assertContains(CiTablePrinter.format(report), "ProductCard")
    }

    @Test
    fun rowsSortedByTotalRecompositionsDesc() {
        val report = LoupeReport(
            durationMs = 200,
            composables = mapOf(
                "Cheap" to history("Cheap", 1),
                "Expensive" to history("Expensive", 20),
            ),
            totalCompositions = 21,
            hotComposables = emptyList(),
            warmComposables = emptyList(),
        )
        val table = CiTablePrinter.format(report)
        assertTrue(table.indexOf("Expensive") < table.indexOf("Cheap"))
    }

    @Test
    fun statusIsHotWhenRecompositionCountIsHigh() {
        val report = LoupeReport(
            durationMs = 500,
            composables = mapOf("HotNode" to history("HotNode", 18)),
            totalCompositions = 18,
            hotComposables = emptyList(),
            warmComposables = emptyList(),
        )
        assertContains(CiTablePrinter.format(report), "HOT")
    }

    @Test
    fun blameColumnShowsTopParam() {
        val blamedParam = BlamedParam(
            name = "onClick",
            recompositionCount = 10,
            fraction = 1f,
            dominantVerdict = ParamVerdict.LambdaIdentity,
            suggestion = null,
            isState = false,
        )
        val h = history("MyComp", 11).copy(blamedParams = listOf(blamedParam))
        val report = LoupeReport(
            durationMs = 100,
            composables = mapOf("MyComp" to h),
            totalCompositions = 11,
            hotComposables = emptyList(),
            warmComposables = emptyList(),
        )
        assertContains(CiTablePrinter.format(report), "onClick")
    }
}
