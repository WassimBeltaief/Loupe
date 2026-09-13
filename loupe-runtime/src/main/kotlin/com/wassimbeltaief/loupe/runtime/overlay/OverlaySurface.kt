package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cream sheet surface: elevation + hairline outline so it is clearly visible on
 * light backgrounds (the app theme is often near-white).
 */
internal fun Modifier.loupeSheetSurface(cornerRadius: Dp = 18.dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(elevation = 16.dp, shape = shape, clip = false)
        .clip(shape)
        .background(LoupeColors.Surface)
        .border(1.dp, LoupeColors.Outline, shape)
}

/** Margin kept around floating overlay sheets so all four corners are visible. */
internal val LoupeSheetInset: Dp = 12.dp
