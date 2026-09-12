package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * #13: draws the heatmap — severity-coloured rounded borders plus a count badge
 * at each tracked composable's top-right corner. Rendered in a full-screen,
 * non-touchable overlay window, so it never intercepts app input.
 */
@Composable
internal fun HeatmapLayer(boxes: List<HeatmapBox>, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Box(modifier = modifier.fillMaxSize()) {
        boxes.forEach { box ->
            HeatmapEntry(box = box, density = density)
        }
    }
}

@Composable
private fun HeatmapEntry(box: HeatmapBox, density: Density) {
    val color = when (box.severity) {
        HeatmapSeverity.Hot -> LoupeColors.Hot
        HeatmapSeverity.Warm -> LoupeColors.Warm
        HeatmapSeverity.Healthy -> LoupeColors.Healthy
    }
    val strokeWidth = when (box.severity) {
        HeatmapSeverity.Hot -> 1.5.dp
        HeatmapSeverity.Warm -> 1.0.dp
        HeatmapSeverity.Healthy -> 0.5.dp
    }

    with(density) {
        val left = box.rect.left.toDp()
        val top = box.rect.top.toDp()
        val width = box.rect.width.toDp()
        val height = box.rect.height.toDp()

        Box(
            modifier = Modifier
                .offset(x = left, y = top)
                .size(width = width, height = height),
        ) {
            // Border
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(strokeWidth, color, RoundedCornerShape(8.dp)),
            )
            // Count badge, anchored to the top-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "${box.count}×",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
