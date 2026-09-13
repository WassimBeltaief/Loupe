package com.wassimbeltaief.loupe.sample

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wassimbeltaief.loupe.testing.LoupeTestRule
import com.wassimbeltaief.loupe.testing.atMost
import com.wassimbeltaief.loupe.testing.maxRecompositionTimeInMs
import com.wassimbeltaief.loupe.testing.shouldNeverRecompose
import com.wassimbeltaief.loupe.testing.shouldRecompose
import com.wassimbeltaief.loupe.testing.shouldRecomposeOnce
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumRecompositionTest {

    @get:Rule
    val rule = LoupeTestRule()

    private fun launchApp() {
        rule.setContent { LoupeSampleTheme { AlbumApp() } }
        rule.waitForIdle()
    }

    // ── Recomposition budget tests ────────────────────────────────────────────

    // After liking album_2, AlbumCard_1 must not have recomposed at all.
    @Test
    fun untouchedAlbumCardNeverRecomposesWhenOtherIsLiked() {
        launchApp()
        rule.onAllNodesWithText("♡")[1].performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("AlbumCard_1").shouldNeverRecompose()
    }

    // After liking album_1, AlbumCard_1 should have composed at most twice (initial + 1 recompose).
    @Test
    fun likedAlbumCardRecomposesAtMostOnce() {
        launchApp()
        rule.onAllNodesWithText("♡")[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("AlbumCard_1").shouldRecompose(atMost(2))
    }

    // Tapping the first like button should recompose LikeButton_1 exactly once (initial + 1).
    @Test
    fun likeButtonRecomposesExactlyOnce() {
        launchApp()
        rule.onAllNodesWithText("♡")[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("LikeButton_1").shouldRecomposeOnce()
    }

    // LikeButton_1 total composition time must stay under 16 ms.
    @Test
    fun likeButtonIsNotExpensive() {
        launchApp()
        rule.onAllNodesWithText("♡")[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("LikeButton_1").maxRecompositionTimeInMs(16f)
    }

    // ── Interaction tests ─────────────────────────────────────────────────────

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
