package com.wassimbeltaief.loupe.testing

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.LoupeRuntime
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A JUnit4 [TestRule] and drop-in replacement for [createComposeRule] that integrates
 * Loupe recomposition tracking into the Compose test lifecycle.
 *
 * Before each test it:
 * - Configures Loupe with overlay, heatmap, and logcat disabled (safe for CI)
 * - Resets the registry so each test starts with a clean slate
 *
 * On test failure it prints the full CI summary table to stdout so every failure log
 * includes recomposition data.
 *
 * Usage:
 * ```kotlin
 * @get:Rule val rule = LoupeTestRule()
 *
 * @Test fun myTest() {
 *     rule.setContent { AlbumApp() }
 *     rule.waitForIdle()
 *     rule.onNodeWithTag("AlbumCard").shouldNeverRecompose()
 * }
 * ```
 *
 * Pass a custom [ComposeContentTestRule] when a specific Activity is required:
 * ```kotlin
 * @get:Rule val rule = LoupeTestRule(createAndroidComposeRule<MyActivity>())
 * ```
 */
class LoupeTestRule(
    private val delegate: ComposeContentTestRule = createComposeRule(),
) : ComposeContentTestRule by delegate {

    override fun apply(base: Statement, description: Description): Statement {
        val loupeStatement = object : Statement() {
            override fun evaluate() {
                LoupeRuntime.configure(
                    LoupeConfig(
                        overlayEnabled = false,
                        heatmapEnabled = false,
                        logcatEnabled = false,
                    )
                )
                LoupeRuntime.reset()
                var failure: Throwable? = null
                try {
                    base.evaluate()
                } catch (e: Throwable) {
                    failure = e
                    throw e
                } finally {
                    if (failure != null) {
                        println(CiTablePrinter.format(LoupeRuntime.snapshot()))
                    }
                }
            }
        }
        // Delegate wraps loupeStatement so the Compose Activity lifecycle is still managed
        // by the existing infrastructure. Execution order:
        //   delegate sets up Activity → loupeStatement configures+resets Loupe → test body
        return delegate.apply(loupeStatement, description)
    }
}
