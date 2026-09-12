package com.wassimbeltaief.loupe.sample

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.LoupeRuntime
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.sample.scenarios.LambdaIdentityScenario
import com.wassimbeltaief.loupe.sample.scenarios.StableScenario
import com.wassimbeltaief.loupe.sample.scenarios.UnstableListScenario
import com.wassimbeltaief.loupe.sample.scenarios.WizardScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #22: end-to-end proof of the whole pipeline — the compiler plugin instruments
 * this module's debug build, so LoupeRuntime.record {} captures REAL injected
 * records from real composables. Keys are qualified per #37.
 *
 * Thresholds are lowered so a handful of clicks crosses them.
 */
@RunWith(AndroidJUnit4::class)
class ScenarioRecompositionTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setUp() {
        LoupeRuntime.configure(
            LoupeConfig(overlayEnabled = false, hotThreshold = 3, warmThreshold = 1)
        )
    }

    @Test
    fun unstableList_productCardBlamedForMutableListItems() {
        val report = LoupeRuntime.record {
            rule.setContent { UnstableListScenario(onBack = {}) }
            rule.waitForIdle()
            repeat(5) {
                rule.onNodeWithText("Trigger Recomposition").performClick()
                rule.waitForIdle()
            }
        }

        val card = report.composables["UnstableListScenario.ProductCard"]
        assertNotNull("ProductCard should be tracked", card)
        assertTrue(
            "expected ≥5 recompositions, got ${card!!.totalRecompositions}",
            card.totalRecompositions >= 5,
        )
        assertTrue(
            "ProductCard should be hot, got ${report.hotComposables.map { it.key }}",
            report.hotComposables.any { it.key == "UnstableListScenario.ProductCard" },
        )
        val itemsBlame = card.blamedParams.firstOrNull { it.name == "items" }
        assertNotNull("items param should be blamed", itemsBlame)
        assertTrue(
            "expected ImmutableList suggestion, got: ${itemsBlame!!.suggestion}",
            itemsBlame.suggestion?.contains("ImmutableList") == true,
        )
    }

    @Test
    fun stable_profileCardSkippedWhenParamsUnchanged() {
        // #40 end-to-end: re-invoked but skipped composables record nothing
        val report = LoupeRuntime.record {
            rule.setContent { StableScenario(onBack = {}) }
            rule.waitForIdle()
            repeat(5) {
                rule.onNodeWithText("Trigger Parent Recomposition").performClick()
                rule.waitForIdle()
            }
        }

        // assertStable throws if ProfileCard recomposed more than its initial composition
        report.assertStable("StableScenario.ProfileCard")
    }

    @Test
    fun lambdaIdentity_onClickBlamedAsLambda() {
        val report = LoupeRuntime.record {
            rule.setContent { LambdaIdentityScenario(onBack = {}) }
            rule.waitForIdle()
            repeat(5) {
                rule.onNodeWithText("Tap me").performClick()
                rule.waitForIdle()
            }
        }

        val button = report.composables["LambdaIdentityScenario.ButtonWithCallback"]
        assertNotNull("ButtonWithCallback should be tracked", button)
        val lambdaBlame = button!!.blamedParams.firstOrNull { it.name == "onClick" }
        assertNotNull("onClick should be blamed", lambdaBlame)
        assertEquals(ParamVerdict.LambdaIdentity, lambdaBlame!!.dominantVerdict)
    }

    @Test
    fun wizard_typingBlamesTheEditedField() {
        val report = LoupeRuntime.record {
            rule.setContent { WizardScenario(onBack = {}) }
            rule.waitForIdle()
            rule.onNodeWithText("Full name").performTextInput("QA Tester")
            rule.waitForIdle()
        }

        val step = report.composables["WizardScenario.WizardStep1"]
        assertNotNull("WizardStep1 should be tracked", step)
        val nameBlame = step!!.blamedParams.firstOrNull { it.name == "name" }
        assertNotNull("name param should be blamed after typing", nameBlame)
    }
}
