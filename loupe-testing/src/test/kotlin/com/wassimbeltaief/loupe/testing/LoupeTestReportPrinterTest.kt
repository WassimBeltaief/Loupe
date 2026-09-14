package com.wassimbeltaief.loupe.testing

import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class LoupeTestReportPrinterTest {

    private fun blame(name: String, count: Int) = BlamedParam(
        name = name,
        recompositionCount = count,
        fraction = 1f,
        dominantVerdict = ParamVerdict.Changed,
        isState = name == "state",
    )

    private fun loupeFailure(
        window: Int = 3,
        hot: Int = 16,
        warm: Int = 4,
    ) = LoupeTestReportPrinter.Failure(
        testName = "AlbumRecompositionTest#commentsSectionShouldNotRecomposeWhenCommentsAreUnchanged",
        expected = "never recompose (1 composition)",
        actual = "composed 4×",
        composableKey = "AlbumScreens.CommentsSection",
        blamedParams = listOf(blame("state", 3)),
        totalCompositions = 4,
        windowRecompositions = window,
        totalDurationMs = 35.8f,
        hotThreshold = hot,
        warmThreshold = warm,
    )

    @Test
    fun summaryShowsRunCountsAndDuration() {
        val text = LoupeTestReportPrinter.format(
            LoupeTestReportPrinter.RunReport(
                total = 7,
                passed = 6,
                failed = 1,
                skipped = 0,
                durationMs = 15_600,
                failures = listOf(loupeFailure()),
            ),
        )
        assertContains(text, "7 tests")
        assertContains(text, "6 passed")
        assertContains(text, "1 failed")
        assertContains(text, "0 skipped")
        assertContains(text, "15.6s")
    }

    @Test
    fun failureBlockShowsBlameAndCost() {
        val text = LoupeTestReportPrinter.format(
            LoupeTestReportPrinter.RunReport(
                total = 1,
                passed = 0,
                failed = 1,
                skipped = 0,
                durationMs = 4_000,
                failures = listOf(loupeFailure()),
            ),
        )
        assertContains(text, "FAIL  AlbumRecompositionTest#commentsSectionShouldNotRecompose")
        assertContains(text, "AlbumScreens.CommentsSection")
        assertContains(text, "state (3×)")
        assertContains(text, "35.8ms")
        assertContains(text, "4 total")
    }

    @Test
    fun statusUsesTheActiveThresholds() {
        // Same 3 compositions: OK at 16/8, WARM at 10/3.
        val ok = LoupeTestReportPrinter.format(report(loupeFailure(window = 3, hot = 16, warm = 8)))
        val warm = LoupeTestReportPrinter.format(report(loupeFailure(window = 3, hot = 10, warm = 3)))
        assertContains(ok, "OK")
        assertContains(warm, "WARM")
    }

    @Test
    fun passingRunHasNoFailureBlock() {
        val text = LoupeTestReportPrinter.format(
            LoupeTestReportPrinter.RunReport(
                total = 3,
                passed = 3,
                failed = 0,
                skipped = 0,
                durationMs = 1_200,
                failures = emptyList(),
            ),
        )
        assertContains(text, "3 passed")
        assertFalse(text.contains("FAIL"))
    }

    @Test
    fun nonLoupeFailureShowsItsMessage() {
        val failure = LoupeTestReportPrinter.Failure(
            testName = "AlbumRecompositionTest#someOtherTest",
            expected = null,
            actual = "java.lang.AssertionError: boom",
            composableKey = null,
            blamedParams = emptyList(),
            totalCompositions = 0,
            windowRecompositions = 0,
            totalDurationMs = 0f,
        )
        val text = LoupeTestReportPrinter.format(report(failure))
        assertContains(text, "java.lang.AssertionError: boom")
    }

    private fun report(failure: LoupeTestReportPrinter.Failure) =
        LoupeTestReportPrinter.RunReport(
            total = 1,
            passed = 0,
            failed = 1,
            skipped = 0,
            durationMs = 4_000,
            failures = listOf(failure),
        )
}