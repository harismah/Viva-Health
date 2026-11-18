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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The small chart pieces the detail screens share. Twin of Strand/Screens/Detail/VivaCharts.swift.

/**
 * A stretch of a line chart's width, as fractions from 0 to 1, marked by a tinted band, a bar across
 * its top and an icon over it: a night's sleep or a logged activity on the stress trace.
 */
data class VivaChartSpan(val start: Float, val end: Float, val color: Color, val icon: ImageVector)

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

/**
 * A plain line over a labelled y-axis: the night heart-rate trace and the day stress trace. [brush]
 * colours the line by height in place of [color]; [spans] mark stretches of the width with a band
 * and an icon, which the stress trace uses for sleep and activities. [nowMarker] ends
 * the chart in a dashed line with a dot at its foot, marking the newest reading. [ticks] are extra
 * x-axis marks (share of the width, label), each with a faint vertical gridline.
 */
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
    brush: Brush? = null,
    spans: List<VivaChartSpan> = emptyList(),
    stroke: Dp = 1.2.dp,
    nowMarker: Boolean = false,
    ticks: List<Pair<Float, String>> = emptyList(),
    /** Stretches (0..1 of the width) where the line is drawn in [highlightColor] over a shaded column. */
    highlights: List<ClosedFloatingPointRange<Float>> = emptyList(),
    highlightColor: Color = color,
) {
    val axisStyle = VivaFont.number(9, FontWeight.Medium)
    Column(modifier.fillMaxWidth().clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The span icons sit in a strip above the plot, which the plot makes room for.
        val iconStrip = if (spans.isEmpty()) 0.dp else 18.dp
        Row(Modifier.padding(top = iconStrip), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        drawLine(VivaStyle.gridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    ticks.forEach { (at, _) ->
                        drawLine(VivaStyle.gridLine, Offset(size.width * at, 0f), Offset(size.width * at, size.height), strokeWidth = 1.dp.toPx())
                    }
                    spans.forEach {
                        drawRect(
                            it.color.copy(alpha = 0.16f),
                            topLeft = Offset(size.width * it.start, 0f),
                            size = Size(size.width * (it.end - it.start), size.height),
                        )
                        // A bar caps the span, as on the reference chart.
                        drawLine(it.color, Offset(size.width * it.start, 0f), Offset(size.width * it.end, 0f), strokeWidth = 2.dp.toPx())
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
                        val column = Brush.verticalGradient(listOf(highlightColor.copy(alpha = 0.06f), highlightColor.copy(alpha = 0.3f)))
                        highlights.forEach {
                            drawRect(column, Offset(size.width * it.start, 0f), Size(size.width * (it.endInclusive - it.start), size.height))
                        }
                        drawPath(line, brush ?: SolidColor(color), style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        highlights.forEach {
                            clipRect(left = size.width * it.start, right = size.width * it.endInclusive) {
                                drawPath(line, highlightColor, style = Stroke(stroke.toPx() * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }
                    if (nowMarker && values.size > 1) {
                        val dash = 3.dp.toPx()
                        drawLine(
                            VivaStyle.textPrimary, Offset(size.width, 0f), Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                        )
                        drawCircle(VivaStyle.textPrimary, 2.5.dp.toPx(), Offset(size.width, size.height))
                    }
                }
                if (values.size < 2) VivaLabel("No data", color = VivaStyle.textPrimary)
                spans.forEach { span ->
                    // A box twice as wide as the middle's distance from the nearer edge centres the icon on the span.
                    val middle = (span.start + span.end) / 2f
                    val nearStart = middle <= 0.5f
                    Box(
                        Modifier.align(if (nearStart) Alignment.TopStart else Alignment.TopEnd).offset(y = -iconStrip)
                            .fillMaxWidth(2f * if (nearStart) middle else 1f - middle).height(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(span.icon, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.requiredSize(14.dp))
                    }
                }
            }
        }
        if (startLabel != null || endLabel != null) {
            val labelStyle = VivaFont.number(10, FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().padding(start = if (yLabels.isEmpty()) 0.dp else 24.dp)) {
                Text(startLabel.orEmpty(), Modifier.align(Alignment.CenterStart), style = labelStyle, color = VivaStyle.textPrimary)
                ticks.forEach { (at, label) ->
                    // A box twice as wide as the tick's distance from the nearer edge centres the label on the tick.
                    val nearStart = at <= 0.5f
                    Text(
                        label,
                        Modifier.fillMaxWidth(2f * if (nearStart) at else 1f - at).align(if (nearStart) Alignment.CenterStart else Alignment.CenterEnd),
                        style = labelStyle, color = VivaStyle.textTertiary, textAlign = TextAlign.Center, maxLines = 1,
                    )
                }
                Text(endLabel.orEmpty(), Modifier.align(Alignment.CenterEnd), style = labelStyle, color = VivaStyle.textPrimary)
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
