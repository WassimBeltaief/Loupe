package com.wassimbeltaief.loupe.sample

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.LoupeRuntime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Basic interaction coverage for the clean album sample. */
@RunWith(AndroidJUnit4::class)
class AlbumRecompositionTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setUp() {
        LoupeRuntime.configure(LoupeConfig(overlayEnabled = false))
    }

    private fun launchApp() {
        rule.setContent { LoupeSampleTheme { AlbumApp() } }
        rule.waitForIdle()
    }

    @Test
    fun gridLikeButtonWorks() {
        launchApp()
        val heartsBefore = rule.onAllNodesWithText("♥").fetchSemanticsNodes().size
        rule.onAllNodesWithText("♡")[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "liking should fill one more heart",
            heartsBefore + 1,
            rule.onAllNodesWithText("♥").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun detailOpensAndLikeButtonWorks() {
        launchApp()
        rule.onNodeWithText("Midnight Signals").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("LIKES").assertIsDisplayed()

        val heartsBefore = rule.onAllNodesWithText("♥").fetchSemanticsNodes().size
        rule.onAllNodesWithText("♡")[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "detail like should fill the heart",
            heartsBefore + 1,
            rule.onAllNodesWithText("♥").fetchSemanticsNodes().size,
        )
    }
}
