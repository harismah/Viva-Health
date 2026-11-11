package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The purple note shown while a score is still calibrating, with a segmented strip for how many
 * nights are in. Twin of `VivaCallout.swift`.
 */
@Composable
fun VivaCallout(
    text: String,
    modifier: Modifier = Modifier,
    filled: Int = 0,
    total: Int = 0,
    fill: Color = VivaStyle.callout,
) {
    Column(
        modifier.fillMaxWidth().background(fill, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = VivaFont.caption, color = VivaStyle.textPrimary)
        if (total > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(total) { index ->
                    val color = if (index < filled) VivaStyle.magenta else VivaStyle.textTertiary.copy(alpha = 0.45f)
                    Box(Modifier.size(width = 28.dp, height = 3.dp).background(color, CircleShape))
                }
            }
        }
    }
}

/** Three-step quality strip (poor / sufficient / optimal) beside a contributor value. */
enum class VivaLevel {
    POOR, SUFFICIENT, OPTIMAL;

    val color: Color
        get() = when (this) {
            POOR -> VivaStyle.warning
            SUFFICIENT -> VivaStyle.textSecondary
            OPTIMAL -> VivaStyle.positive
        }
}

@Composable
fun VivaLevelStrip(level: VivaLevel?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val color = if (level != null && index == level.ordinal) level.color else VivaStyle.ringTrack
            Box(Modifier.size(width = 20.dp, height = 3.dp).background(color, CircleShape))
        }
    }
}
