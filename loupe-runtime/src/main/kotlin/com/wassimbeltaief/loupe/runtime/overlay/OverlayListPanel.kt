package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory

private val ColorHot = Color(0xFFE24B4A)
private val ColorWarm = Color(0xFFEF9F27)
private val ColorHealthy = Color(0xFF1D9E75)
private val ColorSurface = Color(0xF0121212)
private val ColorOnSurface = Color(0xFFEEEEEE)
private val ColorDivider = Color(0xFF2A2A2A)

@Composable
internal fun OverlayListPanel(
    composables: Map<String, RecompositionHistory>,
    config: LoupeConfig,
    onPause: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranked = composables.values
        .sortedByDescending { it.windowRecompositions }
        .take(6)
    val maxWindow = ranked.firstOrNull()?.windowRecompositions?.coerceAtLeast(1) ?: 1
    // Session-scoped: once dismissed, the hint does not reappear
    var hintVisible by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .width(280.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ColorSurface)
            .padding(bottom = 6.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorDivider)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Loupe",
                color = ColorOnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${config.windowSeconds}s · ${composables.size} tracked",
                color = ColorOnSurface.copy(alpha = 0.6f),
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "⏸",
                color = ColorOnSurface,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onPause)
                    .padding(horizontal = 4.dp),
            )
            Text(
                text = "✕",
                color = ColorOnSurface,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.height(4.dp))

        if (ranked.isEmpty()) {
            Text(
                text = "No composables recorded yet",
                color = ColorOnSurface.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        } else {
            ranked.forEach { history ->
                ComposableRow(
                    history = history,
                    maxWindow = maxWindow,
                    config = config,
                )
            }
        }

        // Hint strip — dismissible, does not reappear once closed
        if (hintVisible) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ColorDivider),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "long-press any row to inspect history",
                    color = ColorOnSurface.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(
                    text = "✕",
                    color = ColorOnSurface.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clickable { hintVisible = false }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ComposableRow(
    history: RecompositionHistory,
    maxWindow: Int,
    config: LoupeConfig,
) {
    val color = when {
        history.windowRecompositions >= config.hotThreshold -> ColorHot
        history.windowRecompositions >= config.warmThreshold -> ColorWarm
        else -> ColorHealthy
    }
    val barFraction = history.windowRecompositions.toFloat() / maxWindow

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Severity dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))

        // Name
        Text(
            text = history.key,
            color = ColorOnSurface,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))

        // Proportional bar
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ColorDivider),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction)
                    .height(3.dp)
                    .background(color),
            )
        }
        Spacer(Modifier.width(6.dp))

        // Count + cost
        Text(
            text = "${history.windowRecompositions}x",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${"%.1f".format(history.totalDurationMs)}ms",
            color = ColorOnSurface.copy(alpha = 0.5f),
            fontSize = 10.sp,
        )
    }
}
