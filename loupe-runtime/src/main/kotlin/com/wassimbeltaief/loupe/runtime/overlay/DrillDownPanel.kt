package com.wassimbeltaief.loupe.runtime.overlay

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wassimbeltaief.loupe.runtime.analysis.BurstGrouper
import com.wassimbeltaief.loupe.runtime.export.JsonExporter
import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import com.wassimbeltaief.loupe.runtime.model.ParamSnapshot
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory
import kotlinx.coroutines.launch

/**
 * #16/#17: drill-down panel — full recomposition timeline with burst grouping,
 * verdict tag chips, suggestions, parameter blame bar, and JSON export (#19).
 *
 * Rendered as a bottom sheet (bottom 70% of the screen). Does NOT close on
 * outside tap (the window lets touches pass through) — only via the ✕ button
 * or a downward drag past the dismiss threshold (#18).
 */
@Composable
internal fun DrillDownPanel(
    history: RecompositionHistory,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 140.dp.toPx() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .graphicsLayer { translationY = offsetY.value }
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(LoupeColors.Surface),
    ) {
        // ── Drag handle + header (draggable, swipe down to dismiss) ─────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (offsetY.value > dismissThresholdPx) {
                                    onClose()
                                } else {
                                    offsetY.animateTo(0f, spring())
                                }
                            }
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LoupeColors.OnSurface.copy(alpha = 0.3f)),
            )
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = history.key,
                        color = LoupeColors.OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "${history.file} : ${history.line}",
                        color = LoupeColors.OnSurface.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "${history.totalRecompositions}x · " +
                            "${"%.1f".format(history.totalDurationMs)}ms total · " +
                            "${history.windowRecompositions} in window",
                        color = LoupeColors.OnSurface.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
                Text(
                    text = "✕",
                    color = LoupeColors.OnSurface,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(8.dp),
                )
            }
        }

        Divider()

        // ── Timeline ─────────────────────────────────────────────────────────
        Text(
            text = "TIMELINE",
            color = LoupeColors.OnSurface.copy(alpha = 0.5f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        val bursts = remember(history.records) { BurstGrouper.group(history.records) }
        var expandedBurst by remember { mutableStateOf<Int?>(null) }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        ) {
            items(bursts.size) { index ->
                BurstRow(
                    burst = bursts[index],
                    expanded = expandedBurst == index,
                    onToggle = { expandedBurst = if (expandedBurst == index) null else index },
                )
            }
        }

        Divider()

        // ── Parameter blame bar (#17) ────────────────────────────────────────
        Text(
            text = "PARAMETER BLAME",
            color = LoupeColors.OnSurface.copy(alpha = 0.5f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        BlameBar(history.blamedParams)

        Divider()

        // ── Export (#19) ─────────────────────────────────────────────────────
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PanelButton(label = "Share report", modifier = Modifier.weight(1f)) {
                val json = JsonExporter.historyToJson(history)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, json)
                    putExtra(Intent.EXTRA_SUBJECT, "Loupe report — ${history.key}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(send, "Share Loupe report")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
            PanelButton(label = "Copy JSON", modifier = Modifier.weight(1f)) {
                val json = JsonExporter.historyToJson(history)
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("Loupe report — ${history.key}", json)
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LoupeColors.Divider),
    )
}

@Composable
private fun PanelButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LoupeColors.Divider)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = LoupeColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Timeline bursts ──────────────────────────────────────────────────────────

@Composable
private fun BurstRow(
    burst: BurstGrouper.Burst,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(LoupeColors.Warm),
            )
            Spacer(Modifier.width(8.dp))
            val label = when {
                burst.isSingle && burst.firstIndex == 1 -> "recomposition 1 · initial composition"
                burst.isSingle -> "recomposition ${burst.firstIndex}"
                else -> "recompositions ${burst.firstIndex}–${burst.lastIndex} · ${burst.count}x"
            }
            Text(
                text = "${ago(burst.records.first().timestampNs)}  $label",
                color = LoupeColors.OnSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            burst.dominantChangedParam?.let {
                Text(
                    text = it,
                    color = LoupeColors.Warm,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            // #25: forced recompositions (body ran with all params unchanged) are
            // a distinct class of problem — disambiguate from param-driven churn
            if (burst.records.isNotEmpty() && burst.records.all { it.wasForced }) {
                Spacer(Modifier.width(6.dp))
                Chip("forced", LoupeColors.Warm)
            }
        }
        if (expanded) {
            if (burst.records.any { it.wasForced }) {
                SuggestionRow(
                    "recomposed despite no parameter changes. " +
                        "Its parent may be non-restartable or calling invalidate() directly."
                )
            }
            burst.records.forEach { record ->
                record.params.forEach { param ->
                    ParamRow(param)
                }
            }
        }
    }
}

@Composable
private fun ParamRow(param: ParamSnapshot) {
    Column(modifier = Modifier.padding(start = 14.dp, top = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = param.name,
                color = LoupeColors.OnSurface,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(90.dp),
                maxLines = 1,
            )
            val valueText = when (param.verdict) {
                is ParamVerdict.FirstComposition -> param.currentValue
                is ParamVerdict.Unchanged -> "unchanged"
                else -> "${param.previousValue} → ${param.currentValue}"
            }
            Text(
                text = valueText,
                color = LoupeColors.OnSurface.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            VerdictChip(param)
        }
        param.suggestion?.let { SuggestionRow(it) }
    }
}

@Composable
private fun SuggestionRow(suggestion: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clickable { expanded = !expanded }
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = "💡 $suggestion",
            color = LoupeColors.Warm,
            fontSize = 9.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
        )
    }
}

/**
 * Verdict tag chips (CLAUDE.md vocabulary). Colour encodes verdict type, never rank.
 * "changed" (red, no suggestion) marks a genuine value change that drove this
 * recomposition; "MutableList"/"unstable" mark recognised unstable types.
 */
@Composable
private fun VerdictChip(param: ParamSnapshot) {
    val (label, color) = when (param.verdict) {
        is ParamVerdict.FirstComposition -> return
        is ParamVerdict.Unchanged -> "unchanged" to LoupeColors.VerdictUnchanged
        is ParamVerdict.LambdaIdentity -> "lambda" to LoupeColors.VerdictLambda
        is ParamVerdict.Changed -> when {
            param.suggestion?.contains("collection") == true -> "MutableList" to LoupeColors.VerdictUnstable
            param.suggestion != null -> "unstable" to LoupeColors.VerdictUnstable
            else -> "changed" to LoupeColors.VerdictUnstable
        }
    }
    Chip(label, color)
}

@Composable
private fun BlameChip(param: BlamedParam) {
    val (label, color) = when {
        param.recompositionCount == 0 -> "stable" to LoupeColors.Healthy
        param.dominantVerdict is ParamVerdict.LambdaIdentity -> "lambda" to LoupeColors.VerdictLambda
        param.suggestion != null -> "unstable" to LoupeColors.VerdictUnstable
        else -> "changed" to LoupeColors.VerdictUnstable
    }
    Chip(label, color)
}

@Composable
private fun Chip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.25f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text = label, color = color, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Blame bar (#17): width = contribution rank, colour = verdict type ───────

@Composable
private fun BlameBar(blamedParams: List<BlamedParam>) {
    val max = blamedParams.maxOfOrNull { it.recompositionCount }?.coerceAtLeast(1) ?: 1
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
        blamedParams.forEach { param ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = param.name,
                    color = LoupeColors.OnSurface,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(90.dp),
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(LoupeColors.Divider),
                ) {
                    val fraction = param.recompositionCount.toFloat() / max
                    val color = when {
                        param.recompositionCount == 0 -> LoupeColors.VerdictUnchanged
                        param.dominantVerdict is ParamVerdict.LambdaIdentity -> LoupeColors.VerdictLambda
                        else -> LoupeColors.VerdictUnstable
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(color),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${param.recompositionCount}x",
                    color = LoupeColors.OnSurface.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
                Spacer(Modifier.width(6.dp))
                BlameChip(param)
            }
        }
    }
}

private fun ago(timestampNs: Long): String {
    val elapsedNs = (System.nanoTime() - timestampNs).coerceAtLeast(0)
    return "%.1fs".format(elapsedNs / 1_000_000_000.0)
}
