package com.wassimbeltaief.loupe.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wassimbeltaief.loupe.runtime.LoupeHeatmapHost
import com.wassimbeltaief.loupe.runtime.LoupeRuntime
import com.wassimbeltaief.loupe.sample.scenarios.LambdaIdentityScenario
import com.wassimbeltaief.loupe.sample.scenarios.ScenarioListScreen
import com.wassimbeltaief.loupe.sample.scenarios.StableScenario
import com.wassimbeltaief.loupe.sample.scenarios.UnstableListScenario
import com.wassimbeltaief.loupe.sample.scenarios.WizardScenario

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Opt-in heatmap host (#13) — draws borders + count badges over tracked
            // composables. This is the one feature that needs a code change.
            LoupeHeatmapHost {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        var current by remember { mutableStateOf<String?>(null) }
                        when (current) {
                            "unstable_list"    -> UnstableListScenario(onBack = { current = null })
                            "lambda_identity"  -> LambdaIdentityScenario(onBack = { current = null })
                            "stable"           -> StableScenario(onBack = { current = null })
                            "wizard"           -> WizardScenario(onBack = { current = null })
                            else               -> ScenarioListScreen(onSelect = {
                                LoupeRuntime.reset()
                                current = it
                            })
                        }
                    }
                }
            }
        }
    }
}
