package com.noop.ui.viva

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The small chart pieces the detail screens share. Twin of Strand/Screens/Detail/VivaCharts.swift.

object VivaLineChart {
    /**
     * The value range the line is drawn against: [range] when the caller fixes one, otherwise the
     * data's own range padded by a tenth, or 0 to 1 when the data is flat or missing.
     */
    fun bounds(values: List<Double>, range: ClosedFloatingPointRange<Double>? = null): ClosedFloatingPointRange<Double> {
        if (range != null) return range
        val low = values.minOrNull() ?: return 0.0..1.0
        val high = values.maxOrNull() ?: return 0.0..1.0
        if (high <= low) return 0.0..1.0
        val pad = (high - low) * 0.1
        return (low - pad)..(high + pad)
    }

    /** Four axis labels from the highest value down to the lowest; none when the data is flat. */
    fun axisLabels(values: List<Double>): List<String> {
        val low = values.minOrNull() ?: return emptyList()
        val high = values.maxOrNull() ?: return emptyList()
        if (high <= low) return emptyList()
        return (0..3).map { step -> VivaScoreRing.roundHalfAwayFromZero(high - (high - low) * step / 3).toString() }
    }
}

/** A plain line over a labelled y-axis: the night heart-rate trace and the day stress trace. */
@Composable
fun VivaLineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = VivaStyle.sleep,
    range: ClosedFloatingPointRange<Double>? = null,
    yLabels: List<String> = emptyList(),
    startLabel: String? = null,
    endLabel: String? = null,
    height: Dp = 130.dp,
    fill: Boolean = false,
) {
    val axisStyle = VivaFont.number(9, FontWeight.Medium)
    Column(modifier.fillMaxWidth().clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (yLabels.isNotEmpty()) {
                Column(Modifier.height(height), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
                    yLabels.forEach { Text(it, style = axisStyle, color = VivaStyle.textTertiary, maxLines = 1) }
                }
            }
            Box(Modifier.weight(1f).height(height), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val bounds = VivaLineChart.bounds(values, range)
                    val span = maxOf(bounds.endInclusive - bounds.start, Double.MIN_VALUE)
                    repeat(4) { index ->
                        val y = size.height * index / 3f
                        drawLine(VivaStyle.divider.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
                    }
                    if (values.size > 1) {
                        val line = Path()
                        values.forEachIndexed { index, value ->
                            val x = size.width * index / (values.size - 1)
                            val y = size.height * (1f - ((value.coerceIn(bounds.start, bounds.endInclusive) - bounds.start) / span).toFloat())
                            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                        }
                        if (fill) {
                            val area = Path().apply {
                                addPath(line)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0f))))
                        }
                        drawPath(line, color, style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
                    }
                }
                if (values.size < 2) VivaLabel("No data", color = VivaStyle.textPrimary)
            }
        }
        if (startLabel != null || endLabel != null) {
            val labelStyle = VivaFont.number(10, FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(start = if (yLabels.isEmpty()) 0.dp else 24.dp)) {
                Text(startLabel.orEmpty(), style = labelStyle, color = VivaStyle.textPrimary)
                Spacer(Modifier.weight(1f))
                Text(endLabel.orEmpty(), style = labelStyle, color = VivaStyle.textPrimary)
            }
        }
    }
}

/** A big value with a small grey baseline under it and a trend mark beside it: the head of a detail card. */
@Composable
fun VivaValueHeader(value: String, modifier: Modifier = Modifier, baseline: String? = null, trend: VivaTrend = VivaTrend.FLAT) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = VivaFont.valueLarge, color = VivaStyle.textPrimary, maxLines = 1)
            VivaTrendMark(trend)
        }
        if (baseline != null) Text(baseline, style = VivaFont.baseline, color = VivaStyle.textTertiary, maxLines = 1)
    }
}

/** A labelled horizontal amount bar (`HOURS OF SLEEP ▬▬▬ 6:57`). */
@Composable
fun VivaAmountBar(label: String, value: String, fraction: Double, color: Color, modifier: Modifier = Modifier) {
    val share = if (fraction.isFinite()) fraction.coerceIn(0.0, 1.0).toFloat() else 0f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VivaLabel(label)
            Spacer(Modifier.weight(1f))
            Text(value, style = VivaFont.number(12), color = VivaStyle.textPrimary, maxLines = 1)
        }
        val shape = RoundedCornerShape(3.dp)
        Box(Modifier.fillMaxWidth().height(9.dp).background(VivaStyle.ringTrack, shape)) {
            if (share > 0f) Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(color, shape))
        }
    }
}

/** A square colour swatch beside a legend line or a totals row. */
@Composable
fun VivaSwatch(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(size).height(size).background(color, RoundedCornerShape(2.dp)))
}

/** A bottom sheet of plain explanation, opened from the info button on a detail screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VivaInfoSheet(title: String, paragraphs: List<String>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VivaStyle.canvasBottom) {
        VivaInfoSheetContent(title, paragraphs)
    }
}
