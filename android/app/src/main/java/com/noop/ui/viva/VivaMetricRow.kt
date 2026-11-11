package com.noop.ui.viva

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class VivaTrend { UP, DOWN, FLAT, NONE }

/**
 * One dashboard metric: line icon, uppercase label, and a right-aligned value with its 30-day
 * baseline underneath and a small trend mark beside it. Twin of `VivaMetricRow.swift`.
 */
@Composable
fun VivaMetricRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    baseline: String? = null,
    trend: VivaTrend = VivaTrend.FLAT,
    showsChevron: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 28.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(18.dp))
        }
        VivaCardTitle(label, Modifier.weight(1f), maxLines = 1)
        if (showsChevron) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(18.dp))
        } else {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(value, Modifier.alignByBaseline(), style = VivaFont.valueMedium, color = VivaStyle.textPrimary, maxLines = 1)
                    if (unit != null) {
                        Text(unit, Modifier.alignByBaseline(), style = VivaFont.unit, color = VivaStyle.textPrimary, maxLines = 1)
                    }
                }
                if (baseline != null) {
                    Text(baseline, style = VivaFont.baseline, color = VivaStyle.textTertiary, maxLines = 1)
                }
            }
            Box(Modifier.width(10.dp), contentAlignment = Alignment.Center) { VivaTrendMark(trend) }
        }
    }
}

@Composable
fun VivaTrendMark(trend: VivaTrend) {
    when (trend) {
        VivaTrend.UP, VivaTrend.DOWN -> {
            val up = trend == VivaTrend.UP
            val color = if (up) VivaStyle.positive else VivaStyle.warning
            Canvas(Modifier.size(width = 8.dp, height = 7.dp)) {
                val path = Path().apply {
                    if (up) {
                        moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height)
                    } else {
                        moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height)
                    }
                    close()
                }
                drawPath(path, color)
            }
        }
        VivaTrend.FLAT -> Box(Modifier.size(5.dp).background(VivaStyle.textTertiary, CircleShape))
        VivaTrend.NONE -> Spacer(Modifier.size(5.dp))
    }
}

/** Hairline between rows that share one card. */
@Composable
fun VivaDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(VivaStyle.divider))
}
