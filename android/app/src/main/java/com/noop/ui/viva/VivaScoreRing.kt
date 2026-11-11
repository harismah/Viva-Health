package com.noop.ui.viva

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * The pillar score ring: a thick rounded arc over a dark track with the value centred. `SMALL` is
 * the home trio; `LARGE` is the detail hero and carries the wordmark and label inside. Twin of
 * `VivaScoreRing.swift`.
 */
object VivaScoreRing {
    enum class Format { PERCENT, DECIMAL1 }

    enum class Size(val diameter: Dp, val stroke: Dp) {
        SMALL(87.dp, 5.5.dp),
        LARGE(260.dp, 16.dp),
        MINI(22.dp, 2.5.dp),
    }

    /** Share of the ring to fill, clamped so an out-of-range score never over- or under-draws. */
    fun fraction(value: Double?, maximum: Double): Double {
        if (value == null || maximum <= 0 || !value.isFinite()) return 0.0
        return (value / maximum).coerceIn(0.0, 1.0)
    }

    /** The centred number without its unit; a missing score reads `--`, never `0`. */
    fun text(value: Double?, format: Format): String {
        if (value == null || !value.isFinite()) return "--"
        return when (format) {
            Format.PERCENT -> roundHalfAwayFromZero(value).toString()
            Format.DECIMAL1 -> String.format(Locale.US, "%.1f", value)
        }
    }

    /** Swift's `rounded()`: halves go away from zero. Kotlin's `round` is half-even, so not that. */
    internal fun roundHalfAwayFromZero(value: Double): Long =
        if (value < 0) -Math.round(-value) else Math.round(value)
}

@Composable
fun VivaScoreRing(
    value: Double?,
    maximum: Double,
    color: Color,
    format: VivaScoreRing.Format,
    size: VivaScoreRing.Size,
    modifier: Modifier = Modifier,
    label: String? = null,
    wordmark: String? = "VIVA",
) {
    val fraction = VivaScoreRing.fraction(value, maximum).toFloat()
    val number = VivaScoreRing.text(value, format)
    val spoken = buildString {
        if (label != null) append(label).append(' ')
        append(number)
        if (format == VivaScoreRing.Format.PERCENT && value != null) append('%')
    }
    Box(
        modifier = modifier.size(size.diameter).clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.stroke.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = VivaStyle.ringTrack, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke),
            )
            if (fraction > 0f) {
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        when (size) {
            VivaScoreRing.Size.MINI -> Unit
            VivaScoreRing.Size.SMALL ->
                RingNumber(number, showsPercent(format, value), VivaFont.ringSmall, VivaFont.number(17))
            VivaScoreRing.Size.LARGE -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (wordmark != null) {
                    Text(wordmark, style = VivaFont.wordmark, color = VivaStyle.textSecondary)
                }
                RingNumber(number, showsPercent(format, value), VivaFont.ringLarge, VivaFont.number(36))
                if (label != null) {
                    Text(
                        label.uppercase(),
                        modifier = Modifier.widthIn(max = 150.dp),
                        style = VivaFont.barTitle,
                        color = VivaStyle.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun showsPercent(format: VivaScoreRing.Format, value: Double?) =
    format == VivaScoreRing.Format.PERCENT && value != null

@Composable
private fun RingNumber(number: String, percent: Boolean, style: TextStyle, unitStyle: TextStyle) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(number, modifier = Modifier.alignByBaseline(), style = style, color = VivaStyle.textPrimary, maxLines = 1, softWrap = false)
        if (percent) {
            Text("%", modifier = Modifier.alignByBaseline(), style = unitStyle, color = VivaStyle.textPrimary, maxLines = 1)
        }
    }
}
