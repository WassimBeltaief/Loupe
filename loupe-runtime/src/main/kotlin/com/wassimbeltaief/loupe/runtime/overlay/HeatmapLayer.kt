package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Draws the heatmap: a severity-coloured dotted border and a count badge in the
 * top-right corner of each tracked composable. It is drawn in a full-screen,
 * non-touchable window, so it never blocks app input.
 *
 * Badge stacking: when two nested composables have badges at almost the same
 * position, the outer (larger) box's badge is moved down and left, and made a
 * little transparent, so both stay visible.
 */
@Composable
internal fun HeatmapLayer(boxes: List<HeatmapBox>, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val badgeOffsets = remember(boxes) { computeBadgeOffsets(boxes) }
    Box(modifier = modifier.fillMaxSize()) {
        boxes.forEach { box ->
            val offset = badgeOffsets[box]
            HeatmapEntry(
                box = box,
                density = density,
                badgeOffsetX = offset?.first ?: 0,
                badgeOffsetY = offset?.second ?: 0,
                badgeAlpha = if (offset != null) 0.85f else 1f,
            )
        }
    }
}

/**
 * Computes diagonal offsets (x, y) for badges that would otherwise overlap.
 * Larger (outer) boxes get pushed down and left when their top-right corner
 * is close to a smaller (inner) box's corner.
 */
private fun computeBadgeOffsets(boxes: List<HeatmapBox>): Map<HeatmapBox, Pair<Int, Int>> {
    val threshold = 40 // pixels — badges within this distance need offsetting
    val offsetStep = 24 // pixels to offset diagonally (~130% of badge size)
    val offsets = mutableMapOf<HeatmapBox, Pair<Int, Int>>()

    for (box in boxes) {
        var level = 0
        for (other in boxes) {
            if (box === other) continue
            val dx = kotlin.math.abs(box.rect.right - other.rect.right)
            val dy = kotlin.math.abs(box.rect.top - other.rect.top)
            if (dx <= threshold && dy <= threshold) {
                val areaBox = box.rect.width.toLong() * box.rect.height
                val areaOther = other.rect.width.toLong() * other.rect.height
                if (areaBox > areaOther) {
                    level = (level + 1).coerceAtMost(3)
                }
            }
        }
        if (level > 0) {
            // Offset diagonally: left (negative X) and down (positive Y)
            offsets[box] = Pair(-offsetStep * level, offsetStep * level)
        }
    }
    return offsets
}

@Composable
private fun HeatmapEntry(
    box: HeatmapBox,
    density: Density,
    badgeOffsetX: Int = 0,
    badgeOffsetY: Int = 0,
    badgeAlpha: Float = 1f,
) {
    val color = when (box.severity) {
        HeatmapSeverity.Hot -> LoupeColors.Hot
        HeatmapSeverity.Warm -> LoupeColors.Warm
        HeatmapSeverity.Healthy -> LoupeColors.Healthy
    }
    val strokeWidthPx = when (box.severity) {
        HeatmapSeverity.Hot -> 4f
        HeatmapSeverity.Warm -> 3f
        HeatmapSeverity.Healthy -> 2f
    }
    // Dotted pattern: dash length, gap length
    val dashPattern = floatArrayOf(8f, 6f)

    with(density) {
        val left = box.rect.left.toDp()
        val top = box.rect.top.toDp()
        val width = box.rect.width.toDp()
        val height = box.rect.height.toDp()
        val cornerRadius = 8.dp.toPx()

        Box(
            modifier = Modifier
                .offset(x = left, y = top)
                .size(width = width, height = height),
        ) {
            // Dotted border with transparency
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRoundRect(
                            color = color.copy(alpha = 0.7f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                            style = Stroke(
                                width = strokeWidthPx,
                                pathEffect = PathEffect.dashPathEffect(dashPattern, 0f),
                            ),
                        )
                    },
            )
            // Count badge with transparency, offset diagonally if overlapping
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = badgeOffsetX.toDp(), y = badgeOffsetY.toDp())
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = badgeAlpha))
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
