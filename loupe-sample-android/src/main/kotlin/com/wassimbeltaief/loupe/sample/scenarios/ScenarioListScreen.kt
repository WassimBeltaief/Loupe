package com.wassimbeltaief.loupe.sample.scenarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ScenarioListScreen(onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Loupe",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "Recomposition Debugger",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pick a scenario. Watch the overlay heat up in real time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))

        ScenarioCard(
            title = "Unstable List",
            subtitle = "MutableList churn",
            description = "ProductCard receives a new MutableList on every recomposition. Watch it go red.",
            key = "unstable_list",
            onSelect = onSelect,
        )
        ScenarioCard(
            title = "Lambda Identity",
            subtitle = "Inline lambda churn",
            description = "onClick is recreated inline on every parent recomposition. The overlay shows amber lambda blame.",
            key = "lambda_identity",
            onSelect = onSelect,
        )
        ScenarioCard(
            title = "Stable",
            subtitle = "The healthy baseline",
            description = "ProfileCard with stable, immutable params. Stays green no matter how many times the parent triggers.",
            key = "stable",
            onSelect = onSelect,
        )
        ScenarioCard(
            title = "Wizard",
            subtitle = "Multi-composable churn",
            description = "Three-step form. Each step is its own composable. Type to see which step is hottest.",
            key = "wizard",
            onSelect = onSelect,
        )
    }
}

@Composable
private fun ScenarioCard(
    title: String,
    subtitle: String,
    description: String,
    key: String,
    onSelect: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = { onSelect(key) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open scenario", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
