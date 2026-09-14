package com.wassimbeltaief.loupe.sample

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.testing.LoupeTestRule
import com.wassimbeltaief.loupe.testing.atLeast
import com.wassimbeltaief.loupe.testing.atMost
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

    // ── Unhappy path ──────────────────────────────────────────────────────────

    // Documents the sample's deliberately unhealthy comment area: the ViewModel
    // bundles a 1-second "listening now" counter into the same state the
    // comments read, so the whole section recomposes once a second even though
    // the comments never change. Loupe's report blames that `state`.
    @Test
    fun commentsSectionRecomposesWhileTheListenerCounterTicks() {
        launchApp()
        rule.onNodeWithText("Midnight Signals").performClick()
        rule.waitForIdle()

        // Let the viewmodel tick, pumping a frame after each tick so every state
        // update produces its own recomposition instead of being coalesced.
        repeat(3) {
            Thread.sleep(1_100)
            rule.waitForIdle()
        }

        rule.onNodeWithTag("CommentsSection").shouldRecompose(atLeast(3))
    }

    // Clicking like changes `liked`/`likes`, so LikeButton_1 must recompose:
    // exactly once per click. This is a budget test, not a "must not recompose"
    // test — 12 clicks must not cost more than 13 compositions (initial + one
    // per click).
    @Test
    fun likeButtonRecomposesAtMostOncePerClick() {
        launchApp()
        repeat(12) {
            rule.onNodeWithTag("LikeButton_1").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("LikeButton_1").shouldRecompose(atMost(13))
    }

    // Same budget on the detail page: navigating to detail, then rapidly
    // toggling LikeButton_detail twelve times.
    @Test
    fun detailLikeButtonRecomposesAtMostOncePerClick() {
        launchApp()
        rule.onNodeWithText("Midnight Signals").performClick()
        rule.waitForIdle()
        repeat(12) {
            rule.onNodeWithTag("LikeButton_detail").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("LikeButton_detail").shouldRecompose(atMost(13))
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
