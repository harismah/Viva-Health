package com.noop.ui.viva

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Solid is you, hatch is the context": a solid fill for the value over a hatched track, with a
 * dashed bracket marking the typical range when one is known. Twin of `VivaRangeBar.swift`.
 */
object VivaRangeBar {
    data class Layout(val fill: Float, val hatchStart: Float, val hatchWidth: Float)

    /**
     * Bar geometry in the same unit as [width]. `fill` is the solid width; `hatchStart` and
     * `hatchWidth` place the typical-range bracket. Everything is clamped inside [width].
     */
    fun layout(value: Double, range: ClosedFloatingPointRange<Double>?, maximum: Double, width: Float): Layout {
        if (maximum <= 0 || width <= 0) return Layout(0f, 0f, 0f)
        fun x(v: Double): Float {
            if (!v.isFinite()) return 0f
            return (v / maximum).coerceIn(0.0, 1.0).toFloat() * width
        }
        if (range == null) return Layout(x(value), 0f, 0f)
        val start = x(range.start)
        return Layout(x(value), start, x(range.endInclusive) - start)
    }
}

@Composable
fun VivaRangeBar(
    value: Double,
    maximum: Double,
    color: Color,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Double>? = null,
    height: Dp = 16.dp,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val layout = VivaRangeBar.layout(value, range, maximum, size.width)

        // Hatched track: 45-degree lines running bottom-left to top-right, clipped to the bar.
        val track = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(3.dp.toPx()))) }
        clipPath(track) {
            val spacing = 7.dp.toPx()
            val hatchStroke = 2.5.dp.toPx()
            var x = -size.height
            while (x <= size.width) {
                drawLine(VivaStyle.ringTrack, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = hatchStroke)
                x += spacing
            }
        }

        if (layout.fill > 0f) {
            drawRoundRect(
                color = color,
                size = Size(maxOf(layout.fill, 4.dp.toPx()), size.height),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }

        if (layout.hatchWidth > 0f) {
            // The bracket stands 4dp proud of the bar top and bottom, as on iPhone.
            val overhang = 4.dp.toPx()
            val dash = 3.dp.toPx()
            drawRoundRect(
                color = VivaStyle.textSecondary,
                topLeft = Offset(layout.hatchStart, -overhang),
                size = Size(maxOf(layout.hatchWidth, 8.dp.toPx()), size.height + overhang * 2),
                cornerRadius = CornerRadius(1.dp.toPx()),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
            )
        }
    }
}

/** One sleep-stage line: ring swatch, uppercase name, coloured share, duration, and its bar. */
@Composable
fun VivaStageRow(
    color: Color,
    label: String,
    percent: Int?,
    duration: String,
    value: Double,
    maximum: Double,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Double>? = null,
) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(24.dp).border(1.5.dp, VivaStyle.textPrimary, CircleShape))
            VivaCardTitle(label)
            if (percent != null) {
                Text("$percent%", style = VivaFont.number(12, FontWeight.SemiBold), color = color)
            }
            Spacer(Modifier.weight(1f).width(8.dp))
            Text(duration, style = VivaFont.valueSmall, color = VivaStyle.textPrimary)
        }
        VivaRangeBar(value = value, maximum = maximum, color = color, range = range)
    }
}
