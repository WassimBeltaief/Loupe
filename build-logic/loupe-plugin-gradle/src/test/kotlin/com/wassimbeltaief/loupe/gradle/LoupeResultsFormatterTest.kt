package com.wassimbeltaief.loupe.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoupeResultsFormatterTest {

    private val colors = Colors(enabled = false)

    private val failingXml = """
        <?xml version='1.0' encoding='UTF-8' ?>
        <testsuite name="com.example.AlbumRecompositionTest" tests="7" failures="1" errors="0" skipped="0" time="15.082" timestamp="2026-09-14T10:39:12" hostname="localhost">
          <properties/>
          <testcase name="gridLikeButtonWorks" classname="com.example.AlbumRecompositionTest" time="2.268" />
          <testcase name="commentsSectionShouldNotRecomposeWhenCommentsAreUnchanged" classname="com.example.AlbumRecompositionTest" time="4.777">
            <failure>com.wassimbeltaief.loupe.testing.LoupeAssertionError: Loupe assertion failed for 'CommentsSection': expected never recompose, got 4 compositions
        [loupe] composable=CommentsSection status=WARM blame=state:3 total=4 window=4 durationMs=30.5
        at com.wassimbeltaief.loupe.testing.LoupeRecompositionAssertionsKt.loupeFailure(LoupeRecompositionAssertions.kt:136)
        at com.example.AlbumRecompositionTest.commentsSectionShouldNotRecomposeWhenCommentsAreUnchanged(AlbumRecompositionTest.kt:100)</failure>
          </testcase>
          <testcase name="detailOpensAndLikeButtonWorks" classname="com.example.AlbumRecompositionTest" time="1.395" />
          <testcase name="untouchedAlbumCardNeverRecomposesWhenOtherIsLiked" classname="com.example.AlbumRecompositionTest" time="1.33" />
          <testcase name="likedAlbumCardRecomposesAtMostOnce" classname="com.example.AlbumRecompositionTest" time="1.769" />
          <testcase name="likeButtonRecomposesExactlyOnce" classname="com.example.AlbumRecompositionTest" time="1.706" />
          <testcase name="likeButtonIsNotExpensive" classname="com.example.AlbumRecompositionTest" time="1.233" />
        </testsuite>
    """.trimIndent()

    private val nonLoupeFailingXml = """
        <?xml version='1.0' encoding='UTF-8' ?>
        <testsuite name="com.example.OtherTest" tests="1" failures="1" errors="0" skipped="0" time="0.5">
          <testcase name="someOtherTest" classname="com.example.OtherTest" time="0.5">
            <failure>java.lang.AssertionError: boom
        at com.example.OtherTest.someOtherTest(OtherTest.kt:10)</failure>
          </testcase>
        </testsuite>
    """.trimIndent()

    private val passingXml = """
        <?xml version='1.0' encoding='UTF-8' ?>
        <testsuite name="com.example.OtherTest" tests="2" failures="0" errors="0" skipped="0" time="1.0">
          <testcase name="a" classname="com.example.OtherTest" time="0.5" />
          <testcase name="b" classname="com.example.OtherTest" time="0.5" />
        </testsuite>
    """.trimIndent()

    @Test
    fun summaryShowsPassedAndFailed() {
        val table = LoupeResultsFormatter.format(listOf(failingXml), colors)
        assertTrue(table.contains("6 passed"))
        assertTrue(table.contains("1 failed"))
        assertTrue(table.contains("7 tests"))
        // skipped is omitted when zero
        assertFalse(table.contains("skipped"))
    }

    private val hotXml = """
        <?xml version='1.0' encoding='UTF-8' ?>
        <testsuite name="com.example.AlbumRecompositionTest" tests="1" failures="1" errors="0" skipped="0" time="5.0">
          <testcase name="likeButtonShouldStayStableWhenSpammed" classname="com.example.AlbumRecompositionTest" time="5.0">
            <failure>com.wassimbeltaief.loupe.testing.LoupeAssertionError: Loupe assertion failed for 'LikeButton': expected never recompose, got 13 compositions
        [loupe] composable=LikeButton status=HOT blame=liked:12,likes:12 total=13 window=12 durationMs=8.4
        at com.example.AlbumRecompositionTest.likeButtonShouldStayStableWhenSpammed(AlbumRecompositionTest.kt:120)</failure>
          </testcase>
        </testsuite>
    """.trimIndent()

    @Test
    fun singleFailureRendersOneTable() {
        val table = LoupeResultsFormatter.format(listOf(failingXml), colors)
        assertTrue(table.contains("CommentsSection"))
        assertTrue(table.contains("WARM"))
        assertFalse(table.contains("[WARM]"))
        assertTrue(table.contains("4×"))
        assertTrue(table.contains("30.5ms"))
        assertTrue(table.contains("state (3×)"))
        assertTrue(table.contains("┌"))
        assertTrue(table.contains("│ Composable"))
        // only one table boundary
        assertEquals(1, table.count { it == '┌' })
    }

    @Test
    fun hotFailureShowsHotLevel() {
        val table = LoupeResultsFormatter.format(listOf(hotXml), colors)
        assertTrue(table.contains("HOT"))
        assertFalse(table.contains("WARM"))
        assertTrue(table.contains("LikeButton"))
        assertTrue(table.contains("13×"))
        assertTrue(table.contains("8.4ms"))
        assertTrue(table.contains("liked (12×)"))
    }

    @Test
    fun multipleFailuresMergeIntoOneTableSortedHotFirst() {
        val table = LoupeResultsFormatter.format(listOf(failingXml, hotXml), colors)
        // single table
        assertEquals(1, table.count { it == '┌' })
        // HOT row appears before WARM row
        val hotIndex = table.indexOf("HOT")
        val warmIndex = table.indexOf("WARM")
        assertTrue("HOT should appear before WARM", hotIndex < warmIndex)
        // both composables present
        assertTrue(table.contains("LikeButton"))
        assertTrue(table.contains("CommentsSection"))
    }

    @Test
    fun nonLoupeFailureProducesNoTable() {
        val table = LoupeResultsFormatter.format(listOf(nonLoupeFailingXml), colors)
        assertTrue(table.contains("1 failed"))
        // no Loupe marker → no table
        assertFalse(table.contains("┌"))
    }

    @Test
    fun passingRunShowsOneSummaryLine() {
        val table = LoupeResultsFormatter.format(listOf(passingXml), colors)
        assertTrue(table.contains("2 tests passed"))
        assertFalse(table.contains("failed"))
        assertFalse(table.contains("┌"))
    }

    @Test
    fun emptyInputProducesNothing() {
        assertEquals("", LoupeResultsFormatter.format(emptyList(), colors))
    }

    @Test
    fun duplicatesAcrossDevicesAreMergedOnce() {
        // Same suite reported twice (e.g. two attached devices) must not double count.
        val table = LoupeResultsFormatter.format(listOf(failingXml, failingXml), colors)
        assertTrue(table.contains("7 tests"))
        assertTrue(table.contains("6 passed"))
        assertTrue(table.contains("1 failed"))
    }
}
