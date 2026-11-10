package com.noop.ui.viva

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * The stress dial: an open arc with a short needle tick at the current value, the number in the
 * middle, and the scale ends printed under the arc's feet. Twin of `VivaHalfDial.swift`.
 */
object VivaHalfDial {
    /** Lower-left foot, in degrees clockwise from 3 o'clock. */
    internal const val START_ANGLE = 135f
    internal const val SWEEP = 270f

    fun fraction(value: Double?, maximum: Double): Double {
        if (value == null || maximum <= 0 || !value.isFinite()) return 0.0
        return (value / maximum).coerceIn(0.0, 1.0)
    }

    internal fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
}

private val DialSize = 250.dp

@Composable
fun VivaHalfDial(
    value: Double?,
    maximum: Double,
    caption: String,
    modifier: Modifier = Modifier,
    time: String? = null,
) {
    val fraction = VivaHalfDial.fraction(value, maximum).toFloat()
    val number = value?.let(VivaHalfDial::oneDecimal) ?: "--"
    // The arc's open foot leaves the bottom of the square empty, so the box stops just under the
    // scale labels instead of reserving the full 250dp.
    Box(
        modifier.size(width = DialSize, height = 234.dp).clearAndSetSemantics { contentDescription = "$caption $number" },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val side = DialSize.toPx()
            val centre = Offset(size.width / 2f, side / 2f)
            val radius = side / 2f - stroke / 2f
            val arcTopLeft = Offset(centre.x - radius, centre.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            // Rotated so the gradient's seam (angle 0) sits at the arc's start. The stops after
            // 0.75 fade back to the track colour so the round cap at the start does not pick up
            // the bright end of the sweep.
            rotate(VivaHalfDial.START_ANGLE, centre) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to VivaStyle.ringTrack,
                        0.375f to VivaStyle.textSecondary.copy(alpha = 0.6f),
                        0.75f to VivaStyle.textPrimary,
                        0.77f to VivaStyle.textPrimary,
                        0.97f to VivaStyle.ringTrack,
                        1f to VivaStyle.ringTrack,
                        center = centre,
                    ),
                    startAngle = 0f, sweepAngle = VivaHalfDial.SWEEP, useCenter = false,
                    topLeft = arcTopLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            if (value != null) {
                val scale = side / 250f
                val needleStart = centre.x + 83f * scale
                val needleLength = 44f * scale
                rotate(VivaHalfDial.START_ANGLE + VivaHalfDial.SWEEP * fraction, centre) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, VivaStyle.textPrimary),
                            startX = needleStart, endX = needleStart + needleLength,
                        ),
                        topLeft = Offset(needleStart, centre.y - stroke / 2f),
                        size = Size(needleLength, stroke),
                        cornerRadius = CornerRadius(stroke / 2f),
                    )
                }
            }
        }
        Column(
            Modifier.size(DialSize),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Text(number, style = VivaFont.number(52), color = VivaStyle.textPrimary, maxLines = 1)
            Text(caption.uppercase(), style = VivaFont.barTitle, color = VivaStyle.textPrimary)
            if (time != null) Text(time, style = VivaFont.caption, color = VivaStyle.textSecondary)
        }
        Row(Modifier.padding(top = 218.dp).width(210.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val scaleStyle = VivaFont.number(11, FontWeight.Medium)
            Text("0.0", style = scaleStyle, color = VivaStyle.textTertiary)
            Text(VivaHalfDial.oneDecimal(maximum), style = scaleStyle, color = VivaStyle.textTertiary)
        }
    }
}
