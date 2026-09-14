package com.wassimbeltaief.loupe.sample

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wassimbeltaief.loupe.runtime.LoupeConfig
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
    val rule = LoupeTestRule(
        // Same bands as SampleApplication, so the CI table agrees with the overlay.
        config = LoupeConfig(
            overlayEnabled = false,
            heatmapEnabled = false,
            logcatEnabled = false,
            hotThreshold = 10,
            warmThreshold = 3,
        ),
    )

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

    // ── Unhappy path ──────────────────────────────────────────────────────────

    // The comments are identical on every recomposition, so the section should
    // never recompose while the screen stays open.
    //
    // This test FAILS on purpose: the ViewModel bundles a 1-second "listening
    // now" counter into the same state the comments read, so the whole section
    // recomposes once a second. Loupe's failure report blames that state.
    @Test
    fun commentsSectionShouldNotRecomposeWhenCommentsAreUnchanged() {
        launchApp()
        rule.onNodeWithText("Midnight Signals").performClick()
        rule.waitForIdle()

        // Let the viewmodel tick, pumping a frame after each tick so every state
        // update produces its own recomposition instead of being coalesced.
        repeat(3) {
            Thread.sleep(1_100)
            rule.waitForIdle()
        }

        rule.onNodeWithTag("CommentsSection").shouldNeverRecompose()
    }

    // This test FAILS on purpose: rapidly toggling LikeButton_1 twelve times
    // drives it above the HOT threshold (10×/window). Loupe blames the `liked`
    // and `likes` params that genuinely change on every click.
    @Test
    fun likeButtonShouldStayStableWhenSpammed() {
        launchApp()
        repeat(12) {
            rule.onNodeWithTag("LikeButton_1").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("LikeButton_1").shouldNeverRecompose()
    }

    // This test FAILS on purpose: same spam pattern as the grid like button,
    // but on the detail page. Navigating to detail then rapidly toggling
    // LikeButton_detail twelve times pushes it above the HOT threshold.
    @Test
    fun detailLikeButtonShouldStayStableWhenSpammed() {
        launchApp()
        rule.onNodeWithText("Midnight Signals").performClick()
        rule.waitForIdle()
        repeat(12) {
            rule.onNodeWithTag("LikeButton_detail").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("LikeButton_detail").shouldNeverRecompose()
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
