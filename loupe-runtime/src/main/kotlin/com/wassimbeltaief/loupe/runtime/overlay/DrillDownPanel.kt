package com.wassimbeltaief.loupe.runtime.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.analysis.BurstGrouper
import com.wassimbeltaief.loupe.runtime.export.JsonExporter
import com.wassimbeltaief.loupe.runtime.model.BlamedParam
import com.wassimbeltaief.loupe.runtime.model.ParamSnapshot
import com.wassimbeltaief.loupe.runtime.model.ParamVerdict
import com.wassimbeltaief.loupe.runtime.model.RecompositionHistory

/**
 * The fullscreen detail for one instance.
 *
 * It shows the recomposition timeline (grouped into bursts, each with verdict
 * chips and fix suggestions), the parameter blame bar, and the Share and Copy
 * JSON buttons.
 *
 * @param history the instance to inspect
 * @param config used for the severity colours of the timeline
 * @param onBack called when the user closes the panel
 */
@Composable
internal fun DrillDownPanel(
    history: RecompositionHistory,
    config: LoupeConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LoupeSheetInset)
            .loupeSheetSurface(cornerRadius = 20.dp),
    ) {
        // Header: back · name + file:line · close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderAction(glyph = "←", onClick = onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.key.substringAfterLast('.'),
                    color = LoupeColors.OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "${history.file} : ${history.line}",
                    color = LoupeColors.OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Text(
            text = "${history.totalRecompositions}x total · " +
                "${"%.1f".format(java.util.Locale.US, history.totalDurationMs)}ms · " +
                "${history.windowRecompositions} in window",
            color = LoupeColors.OnSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        Divider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel("RECOMPOSITION HISTORY — NEWEST FIRST")
            val bursts = remember(history.records) { BurstGrouper.group(history.records) }
            // Frozen "now": recompute only when the newest record changes, never on
            // expand/collapse — otherwise tapping changes every row's "ago" value.
            val referenceNs = remember(history.records.firstOrNull()?.timestampNs) { System.nanoTime() }
            var expandedBurst by remember { mutableStateOf<Int?>(null) }
            bursts.forEachIndexed { index, burst ->
                BurstBlock(
                    burst = burst,
                    config = config,
                    referenceNs = referenceNs,
                    expanded = expandedBurst == index,
                    onToggle = { expandedBurst = if (expandedBurst == index) null else index },
                )
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            SectionLabel("PARAMETER BLAME")
            BlameBar(history.blamedParams)

            Spacer(Modifier.height(12.dp))
            Divider()
            ExportButtons(history)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeaderAction(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = glyph, color = LoupeColors.OnSurface, fontSize = 17.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = LoupeColors.OnSurfaceVariant,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LoupeColors.Divider),
    )
}

// ── Timeline bursts ──────────────────────────────────────────────────────────

@Composable
private fun BurstBlock(
    burst: BurstGrouper.Burst,
    config: LoupeConfig,
    referenceNs: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Severity by recomposition ordinal: 1 green · warm..hot amber · ≥hot red
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ordinalColor(burst.lastIndex, config)),
            )
            Spacer(Modifier.width(8.dp))
            val label = when {
                burst.isSingle && burst.firstIndex == 1 -> "recomposition 1 · initial composition"
                burst.isSingle -> "recomposition ${burst.firstIndex}"
                else -> "recompositions ${burst.firstIndex}–${burst.lastIndex} · ${burst.count}x"
            }
            Text(
                text = "${ago(burst.records.first().timestampNs, referenceNs)}  $label",
                color = LoupeColors.OnSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            val stateNames = burst.changedStateNames
            when {
                stateNames.isNotEmpty() -> {
                    Chip("state", LoupeColors.VerdictUnstable)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stateNames.joinToString(", "),
                        color = LoupeColors.VerdictUnstable,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
                burst.records.isNotEmpty() && burst.records.all { it.wasForced } -> {
                    Chip("state / parent", LoupeColors.Warm)
                    Spacer(Modifier.width(6.dp))
                }
            }
            burst.dominantChangedParam?.let {
                Text(
                    text = it,
                    color = verdictColor(burst.dominantChangedVerdict),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (expanded) {
            if (burst.changedStateNames.isEmpty() &&
                burst.records.any { it.wasForced }
            ) {
                SuggestionRow(
                    "No parameter changed, but it recomposed — the trigger is something Loupe " +
                        "does not track as a parameter: internal state (e.g. `counter++` on a " +
                        "`remember { mutableStateOf() }`), a non-restartable parent, or " +
                        "`invalidate()`."
                )
            }
            burst.records.forEach { record ->
                // Only surface what actually changed; unchanged rows are noise.
                record.stateChanges.filterNot { it.verdict is ParamVerdict.Unchanged }.forEach { StateRow(it) }
                record.params.filterNot { it.verdict is ParamVerdict.Unchanged }.forEach { ParamRow(it) }
            }
        }
    }
}

@Composable
private fun ParamRow(param: ParamSnapshot) {
    Column(modifier = Modifier.padding(start = 15.dp, top = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = param.name,
                color = LoupeColors.OnSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(96.dp),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            VerdictChip(param)
        }
        // Show values on separate lines for clarity when there's a change
        when (param.verdict) {
            is ParamVerdict.FirstComposition -> {
                ValueLine(prefix = null, value = param.currentValue)
            }
            is ParamVerdict.Unchanged -> {}
            else -> {
                ValueLine(prefix = "was", value = param.previousValue, color = LoupeColors.OnSurfaceVariant)
                ValueLine(prefix = "now", value = param.currentValue, color = verdictColor(param.verdict))
            }
        }
        param.suggestion?.let { SuggestionRow(it) }
    }
}

@Composable
private fun ValueLine(prefix: String?, value: String, color: Color = LoupeColors.OnSurfaceVariant) {
    Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp)) {
        if (prefix != null) {
            Text(
                text = "$prefix: ",
                color = LoupeColors.OnSurfaceVariant,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = value,
            color = color,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
        )
    }
}

@Composable
private fun StateRow(state: ParamSnapshot) {
    Column(modifier = Modifier.padding(start = 15.dp, top = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.name,
                color = LoupeColors.OnSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(96.dp),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            val (chipLabel, chipColor) = when (state.verdict) {
                is ParamVerdict.Changed -> "state" to LoupeColors.VerdictUnstable
                is ParamVerdict.LambdaIdentity -> "state" to LoupeColors.VerdictLambda
                else -> "unchanged" to LoupeColors.VerdictUnchanged
            }
            Chip(chipLabel, chipColor)
        }
        when (state.verdict) {
            is ParamVerdict.FirstComposition -> {
                ValueLine(prefix = null, value = state.currentValue)
            }
            is ParamVerdict.Unchanged -> {}
            else -> {
                ValueLine(prefix = "was", value = state.previousValue, color = LoupeColors.OnSurfaceVariant)
                ValueLine(prefix = "now", value = state.currentValue, color = verdictColor(state.verdict))
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text = "💡 $suggestion",
        color = LoupeColors.Warm,
        fontSize = 10.sp,
        maxLines = if (expanded) Int.MAX_VALUE else 1,
        modifier = Modifier
            .clickable { expanded = !expanded }
            .padding(vertical = 2.dp),
    )
}

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
        param.isState -> "state" to LoupeColors.VerdictUnstable
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
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = label, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Blame bar: width = contribution rank, colour = verdict type ─────────────

@Composable
private fun BlameBar(blamedParams: List<BlamedParam>) {
    val max = blamedParams.maxOfOrNull { it.recompositionCount }?.coerceAtLeast(1) ?: 1
    Column {
        blamedParams.forEach { param ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = param.name,
                    color = LoupeColors.OnSurface,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(96.dp),
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LoupeColors.Divider),
                ) {
                    val fraction = param.recompositionCount.toFloat() / max
                    val color = if (param.recompositionCount == 0) {
                        LoupeColors.VerdictUnchanged
                    } else {
                        verdictColor(param.dominantVerdict)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(color),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${param.recompositionCount}x",
                    color = LoupeColors.OnSurfaceVariant,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(8.dp))
                BlameChip(param)
            }
        }
    }
}

// ── Export ───────────────────────────────────────────────────────────────────

@Composable
private fun ExportButtons(history: RecompositionHistory) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
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
            context.startActivity(
                Intent.createChooser(send, "Share Loupe report")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        PanelButton(label = "Copy JSON", modifier = Modifier.weight(1f)) {
            val json = JsonExporter.historyToJson(history)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Loupe report — ${history.key}", json))
        }
    }
}

@Composable
private fun PanelButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(LoupeColors.SurfaceRaised)
            .border(1.dp, LoupeColors.Outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = LoupeColors.OnSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun ago(timestampNs: Long, referenceNs: Long): String {
    val elapsedNs = (referenceNs - timestampNs).coerceAtLeast(0)
    return "%.1fs".format(java.util.Locale.US, elapsedNs / 1_000_000_000.0)
}

/**
 * Timeline severity by recomposition ordinal:
 * 1 (initial composition) green · warmThreshold..hotThreshold amber · ≥ hotThreshold red.
 */
private fun ordinalColor(ordinal: Int, config: LoupeConfig): Color = when {
    ordinal <= 1 -> LoupeColors.Healthy
    ordinal >= config.hotThreshold -> LoupeColors.Hot
    ordinal >= config.warmThreshold -> LoupeColors.Warm
    else -> LoupeColors.Healthy
}

/**
 * Verdict colour grammar — one source of truth so a changed value is always the
 * same colour, whether it is a parameter or local state.
 */
private fun verdictColor(verdict: ParamVerdict): Color = when (verdict) {
    is ParamVerdict.LambdaIdentity -> LoupeColors.VerdictLambda
    is ParamVerdict.Changed -> LoupeColors.VerdictUnstable
    else -> LoupeColors.VerdictUnchanged
}
