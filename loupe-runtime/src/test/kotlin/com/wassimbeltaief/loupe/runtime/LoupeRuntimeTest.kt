package com.wassimbeltaief.loupe.runtime

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LoupeRuntimeTest {

    @BeforeTest
    fun setUp() {
        LoupeRuntime.reset()
        LoupeRuntime.configure(LoupeConfig())
    }

    @Test
    fun `record stores recomposition`() {
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        assertEquals(1, LoupeRuntime.snapshot().totalRecompositions)
    }

    @Test
    fun `pause stops recording`() {
        LoupeRuntime.pause()
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        assertEquals(0, LoupeRuntime.snapshot().totalRecompositions)
    }

    @Test
    fun `resume restarts recording after pause`() {
        LoupeRuntime.pause()
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        LoupeRuntime.resume()
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf("n" to 2))
        assertEquals(1, LoupeRuntime.snapshot().totalRecompositions)
    }

    @Test
    fun `reset clears history`() {
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        LoupeRuntime.reset()
        assertEquals(0, LoupeRuntime.snapshot().totalRecompositions)
    }

    @Test
    fun `ignoreList skips matching composables`() {
        LoupeRuntime.configure(LoupeConfig(ignoreList = listOf("Cursor", "Blink*")))
        LoupeRuntime.record("Cursor", "Cursor.kt", 1, arrayOf())
        LoupeRuntime.record("BlinkingDot", "BlinkingDot.kt", 1, arrayOf())
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf())
        assertEquals(1, LoupeRuntime.snapshot().totalRecompositions)
    }

    @Test
    fun `hot and warm composables classified by threshold`() {
        LoupeRuntime.configure(LoupeConfig(hotThreshold = 5, warmThreshold = 2, windowSeconds = 60))
        repeat(6) { LoupeRuntime.record("Hot", "Hot.kt", 1, arrayOf("n" to it)) }
        repeat(3) { LoupeRuntime.record("Warm", "Warm.kt", 1, arrayOf("n" to it)) }
        repeat(1) { LoupeRuntime.record("Cool", "Cool.kt", 1, arrayOf("n" to it)) }
        val report = LoupeRuntime.snapshot()
        assertTrue(report.hotComposables.any { it.key == "Hot" })
        assertTrue(report.warmComposables.any { it.key == "Warm" })
        assertTrue(report.hotComposables.none { it.key == "Cool" })
        assertTrue(report.warmComposables.none { it.key == "Cool" })
    }

    @Test
    fun `assertMaxRecompositions throws on violation`() {
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf("n" to 2))
        val report = LoupeRuntime.snapshot()
        assertFailsWith<AssertionError> { report.assertMaxRecompositions(1) }
    }

    @Test
    fun `assertStable throws when composable recomposed`() {
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf("n" to 1))
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf("n" to 2))
        assertFailsWith<AssertionError> { LoupeRuntime.snapshot().assertStable("Card") }
    }

    @Test
    fun `record block API resets then captures`() {
        LoupeRuntime.record("Before", "Before.kt", 1, arrayOf("x" to 1))
        val report = LoupeRuntime.record {
            LoupeRuntime.record("Inside", "Inside.kt", 1, arrayOf("x" to 1))
        }
        assertEquals(setOf("Inside"), report.composables.keys)
    }

    @Test
    fun `toJson produces valid structure`() {
        LoupeRuntime.record("Card", "Card.kt", 1, arrayOf("n" to 42))
        val json = LoupeRuntime.snapshot().toJson()
        assertTrue("Card" in json)
        assertTrue("totalRecompositions" in json)
    }
}
