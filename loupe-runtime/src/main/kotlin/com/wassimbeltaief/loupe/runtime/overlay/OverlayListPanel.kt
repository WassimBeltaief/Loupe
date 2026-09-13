package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory

/**
 * Composables list sheet — full width, up to 33% of the screen (window-sized by
 * the manager), scrollable with a scrollbar. Tapping a row opens the fullscreen
 * detail.
 */
@Composable
internal fun OverlayListPanel(
    instances: List<RecompositionHistory>,
    config: LoupeConfig,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onCollapse: () -> Unit,
    onSelect: (RecompositionHistory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Deterministic order: hottest first, then a stable id tie-break so #n labels
    // don't shuffle between updates.
    val ranked = instances
        .sortedWith(
            compareByDescending<RecompositionHistory> { it.totalRecompositions }
                .thenBy { it.instanceId }
        )
    val labels = ranked.groupBy { it.key }.let { byKey ->
        ranked.associate { history ->
            val group = byKey.getValue(history.key)
                .sortedByDescending { it.totalRecompositions }
            val short = history.key.substringAfterLast('.')
            history.instanceId to if (group.size > 1) {
                "$short #${group.indexOf(history) + 1}"
            } else {
                short
            }
        }
    }
    val maxCount = ranked.firstOrNull()?.totalRecompositions?.coerceAtLeast(1) ?: 1

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LoupeSheetInset)
            .loupeSheetSurface(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Loupe",
                color = LoupeColors.OnSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${instances.size} tracked",
                color = LoupeColors.OnSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            // Toggle: green ▶ when paused, yellow ⏸ while recording
            IconButton(
                glyph = if (isPaused) "▶" else "⏸",
                color = if (isPaused) LoupeColors.Healthy else LoupeColors.Warm,
                onClick = onTogglePause,
            )
            IconButton(glyph = "⌄", onClick = onCollapse)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LoupeColors.Divider),
        )

        if (ranked.isEmpty()) {
            Text(
                text = "No composables recorded yet",
                color = LoupeColors.OnSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 8.dp),
                ) {
                    items(items = ranked, key = { it.instanceId }) { history ->
                        ComposableRow(
                            label = labels[history.instanceId]
                                ?: history.key.substringAfterLast('.'),
                            history = history,
                            maxCount = maxCount,
                            config = config,
                            onClick = { onSelect(history) },
                        )
                    }
                }
                Scrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, bottom = 4.dp, end = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun ComposableRow(
    label: String,
    history: RecompositionHistory,
    maxCount: Int,
    config: LoupeConfig,
    onClick: () -> Unit,
) {
    val color = when {
        history.totalRecompositions >= config.hotThreshold -> LoupeColors.Hot
        history.totalRecompositions >= config.warmThreshold -> LoupeColors.Warm
        else -> LoupeColors.Healthy
    }
    val barFraction = history.totalRecompositions.toFloat() / maxCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = LoupeColors.OnSurface,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LoupeColors.Divider),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction)
                    .height(4.dp)
                    .background(color),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${history.totalRecompositions}x",
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${"%.1f".format(java.util.Locale.US, history.totalDurationMs)}ms",
            color = LoupeColors.OnSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun IconButton(
    glyph: String,
    color: androidx.compose.ui.graphics.Color = LoupeColors.OnSurface,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text = glyph, color = color, fontSize = 15.sp)
    }
}

/**
 * Minimal scrollbar: thumb size/offset approximate from the visible item window.
 * Only shown when there is something to scroll.
 */
@Composable
private fun Scrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = state.layoutInfo
    val total = layoutInfo.totalItemsCount
    val visible = layoutInfo.visibleItemsInfo.size
    if (total == 0 || total <= visible) return

    BoxWithConstraints(
        modifier = modifier
            .width(3.dp)
            .fillMaxHeight(),
    ) {
        val trackPx = constraints.maxHeight.toFloat()
        val thumbFraction = (visible.toFloat() / total).coerceIn(0.15f, 1f)
        val thumbPx = trackPx * thumbFraction
        val maxScrollFraction = (total - visible).toFloat().coerceAtLeast(1f)
        val scrollFraction = (state.firstVisibleItemIndex / maxScrollFraction).coerceIn(0f, 1f)
        val offsetPx = (trackPx - thumbPx) * scrollFraction

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(2.dp))
                .background(LoupeColors.Divider),
        ) {
            Box(
                modifier = Modifier
                    .offset(y = with(androidx.compose.ui.platform.LocalDensity.current) { offsetPx.toDp() })
                    .fillMaxWidth()
                    .height(
                        with(androidx.compose.ui.platform.LocalDensity.current) { thumbPx.toDp() },
                    )
                    .clip(RoundedCornerShape(2.dp))
                    .background(LoupeColors.OnSurfaceVariant),
            )
        }
    }
}
