package com.wassimbeltaief.loupe.sample.scenarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wassimbeltaief.loupe.runtime.LoupeRuntime
import com.wassimbeltaief.loupe.runtime.model.LambdaRef

@Composable
fun WizardScenario(onBack: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("← Back", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Wizard",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Step ${step + 1} of 3 — multi-composable churn",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Each step is its own composable. Typing causes rapid recompositions of the " +
                "active step. Navigate between steps to see the overlay shift to whichever step is hottest.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                val stepKey = when (step) { 0 -> "WizardStep1"; 1 -> "WizardStep2"; else -> "WizardStep3" }
                Text(
                    text = stepKey,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "Under watch",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                when (step) {
                    0 -> WizardStep1(name = name, onNameChange = { name = it }, onNext = { step = 1 })
                    1 -> WizardStep2(
                        email = email,
                        onEmailChange = { email = it },
                        onNext = { step = 2 },
                        onBack = { step = 0 },
                    )
                    2 -> WizardStep3(
                        notes = notes,
                        onNotesChange = { notes = it },
                        onFinish = { step = 0 },
                        onBack = { step = 1 },
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardStep1(name: String, onNameChange: (String) -> Unit, onNext: () -> Unit) {
    LoupeRuntime.record(
        key = "WizardStep1",
        file = "WizardScenario.kt",
        line = 102,
        params = arrayOf(
            "name" to name,
            "onNameChange" to LambdaRef(System.identityHashCode(onNameChange)),
            "onNext" to LambdaRef(System.identityHashCode(onNext)),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "What's your name?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Full name") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onNext,
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 18.dp),
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WizardStep2(
    email: String,
    onEmailChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    LoupeRuntime.record(
        key = "WizardStep2",
        file = "WizardScenario.kt",
        line = 133,
        params = arrayOf(
            "email" to email,
            "onEmailChange" to LambdaRef(System.identityHashCode(onEmailChange)),
            "onNext" to LambdaRef(System.identityHashCode(onNext)),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "What's your email?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email address") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 18.dp),
            ) {
                Text("Back", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = onNext,
                enabled = email.contains("@"),
                modifier = Modifier.weight(2f),
                contentPadding = PaddingValues(vertical = 18.dp),
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WizardStep3(
    notes: String,
    onNotesChange: (String) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    LoupeRuntime.record(
        key = "WizardStep3",
        file = "WizardScenario.kt",
        line = 169,
        params = arrayOf(
            "notes" to notes,
            "onNotesChange" to LambdaRef(System.identityHashCode(onNotesChange)),
            "onFinish" to LambdaRef(System.identityHashCode(onFinish)),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Any notes?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 18.dp),
            ) {
                Text("Back", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = onFinish,
                modifier = Modifier.weight(2f),
                contentPadding = PaddingValues(vertical = 18.dp),
            ) {
                Text("Finish", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
