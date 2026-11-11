package com.noop.ui.viva

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ui.AppViewModel
import com.noop.ui.logicalDayNow
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/** The span a Trend View shows at once. */
enum class VivaTrendPeriod(val label: String, val days: Int, val noun: String) {
    WEEK("W", 7, "week"),
    MONTH("M", 30, "month"),
    SIX_MONTHS("6M", 182, "6 months"),
}

/**
 * One metric over a week, a month or six months, with a picker to switch metric in place. Twin of
 * `VivaTrendView.swift`, laid out after the reference app: average and change on the left, period
 * switch and date stepper on the right, then the chart.
 */
@Composable
fun VivaTrendScreen(viewModel: AppViewModel, initial: VivaTrendMetric, onBack: () -> Unit) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    var metric by rememberSaveable { mutableStateOf(initial) }
    var period by rememberSaveable { mutableStateOf(VivaTrendPeriod.WEEK) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val today = logicalDayNow()

    val sleepPerformance = rememberSleepPerformance(viewModel, days)

    val points = metric.points(days, period.days, today, page * period.days, sleepPerformance)
    val average = VivaTrendMetric.average(points)
    val previous = VivaTrendMetric.average(metric.points(days, period.days, today, (page + 1) * period.days, sleepPerformance))
    val change = VivaTrendMetric.changePercent(average, previous)
    val hasEarlier = days.firstOrNull()?.day?.let { earliest -> points.firstOrNull()?.key?.let { earliest < it } } ?: false

    VivaDetailScaffold(title = "Trend view", onBack = onBack) {
        MetricPicker(metric, pickerOpen, onToggle = { pickerOpen = !pickerOpen }) {
            metric = it
            page = 0
            pickerOpen = false
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VivaLabel("Average")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        average?.let { metric.text(it, locale).removeSuffix("%") } ?: "---",
                        Modifier.alignByBaseline(), style = VivaFont.number(34), color = VivaStyle.textPrimary, maxLines = 1,
                    )
                    Text(metric.unit, Modifier.alignByBaseline(), style = VivaFont.unit.copy(fontSize = 14.sp), color = VivaStyle.textPrimary)
                }
                if (change != null) ChangeChip(change, period)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PeriodControl(period) {
                    period = it
                    page = 0
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepperButton(Icons.Rounded.ChevronLeft, "Earlier period", hasEarlier) { page += 1 }
                    VivaLabel(rangeText(points, locale), color = VivaStyle.textPrimary)
                    StepperButton(Icons.Rounded.ChevronRight, "Later period", page > 0) { page -= 1 }
                }
            }
        }
        Text(
            metric.summary,
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            style = VivaFont.body, color = VivaStyle.textPrimary,
        )
        VivaTrendChart(metric, points, average = if (period == VivaTrendPeriod.WEEK) null else average, modifier = Modifier.padding(horizontal = 4.dp))
        if (metric == VivaTrendMetric.SLEEP_PERFORMANCE || metric == VivaTrendMetric.RECOVERY) {
            Breakdown(metric, points.mapNotNull { it.value }, Modifier.padding(top = 8.dp))
        }
        VivaCard(Modifier.padding(top = 12.dp)) {
            Text("What is ${metric.title}?", style = VivaFont.bodyStrong, color = VivaStyle.textPrimary)
            Text(metric.summary, style = VivaFont.body, color = VivaStyle.textSecondary)
        }
    }
}

/**
 * Sleep performance by day. It is stored as its own series, not on the day row, so every Viva
 * screen that charts it loads it through this one read: the same resolved series the home ring uses.
 */
@Composable
internal fun rememberSleepPerformance(viewModel: AppViewModel, days: List<com.noop.data.DailyMetric>): Map<String, Double> {
    var series by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(days) {
        series = runCatching {
            viewModel.repo.resolvedSeries("sleep_performance", "my-whoop", "0000-00-00", "9999-99-99", strapDeviceId = viewModel.activeStrapId)
                .values.associate { it.first to it.second }
        }.getOrDefault(emptyMap())
    }
    return series
}

private fun rangeText(points: List<VivaTrendMetric.Point>, locale: Locale): String {
    val first = points.firstOrNull()?.date ?: return ""
    val last = points.last().date
    val short = DateTimeFormatter.ofPattern("MMM d", locale)
    return "${first.format(short)} - ${last.format(DateTimeFormatter.ofPattern("MMM d, yy", locale))}"
}

@Composable
private fun ChangeChip(change: Int, period: VivaTrendPeriod) {
    // Stated as a change, not as good or bad: whether up is welcome depends on the metric.
    val arrow = when {
        change > 0 -> "▲ "
        change < 0 -> "▼ "
        else -> ""
    }
    Box(Modifier.background(VivaStyle.chip, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(
            "$arrow${kotlin.math.abs(change)}% vs. prior ${period.noun}",
            style = VivaFont.number(11, FontWeight.Medium), color = VivaStyle.textSecondary, maxLines = 1,
        )
    }
}

@Composable
private fun MetricPicker(metric: VivaTrendMetric, open: Boolean, onToggle: () -> Unit, onPick: (VivaTrendMetric) -> Unit) {
    Column {
        PickerRow(metric.icon, metric.title, VivaStyle.chip, onToggle, height = 48) {
            Icon(
                if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(22.dp),
            )
        }
        if (open) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp).background(Color.Black, RoundedCornerShape(8.dp)).padding(bottom = 8.dp)) {
                VivaTrendMetric.Group.entries.forEach { group ->
                    Row(Modifier.padding(horizontal = 14.dp).padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        VivaLabel(group.title, color = VivaStyle.textTertiary)
                        Box(Modifier.padding(start = 10.dp).weight(1f).height(1.dp).background(VivaStyle.divider))
                    }
                    VivaTrendMetric.entries.filter { it.group == group }.forEach { option ->
                        PickerRow(
                            option.icon, option.title,
                            fill = if (option == metric) VivaStyle.card else Color.Transparent,
                            onClick = { onPick(option) }, height = 44, selected = option == metric,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    title: String,
    fill: Color,
    onClick: () -> Unit,
    height: Int,
    selected: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(8.dp))
            .vivaPressable(onClick)
            .background(fill)
            .padding(horizontal = 14.dp)
            .semantics(mergeDescendants = true) { role = Role.Button; this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(18.dp))
        }
        VivaCardTitle(title, Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

@Composable
private fun PeriodControl(period: VivaTrendPeriod, onSelect: (VivaTrendPeriod) -> Unit) {
    Row(Modifier.background(VivaStyle.inset, RoundedCornerShape(7.dp)).padding(2.dp)) {
        VivaTrendPeriod.entries.forEach { option ->
            val isSelected = option == period
            Box(
                Modifier
                    .size(width = 46.dp, height = 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .vivaPressable { onSelect(option) }
                    .background(if (isSelected) VivaStyle.chipActive else Color.Transparent)
                    .semantics { role = Role.Tab; selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = if (isSelected) VivaStyle.textPrimary else VivaStyle.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .then(if (enabled) Modifier.vivaPressable(onClick) else Modifier)
            .semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) VivaStyle.textPrimary else VivaStyle.textTertiary, modifier = Modifier.size(18.dp))
    }
}

/** How many days of the period fell in each band, for the two scores that have bands. */
@Composable
private fun Breakdown(metric: VivaTrendMetric, values: List<Double>, modifier: Modifier = Modifier) {
    val recovery = metric == VivaTrendMetric.RECOVERY
    val high = if (recovery) 67.0 else 85.0
    val low = if (recovery) 34.0 else 70.0
    val bands = listOf(
        Triple(if (recovery) "Green (67% and up)" else "Optimal (85% and up)", VivaStyle.positive, values.count { it >= high }),
        Triple(
            if (recovery) "Yellow (34 to 66%)" else "Sufficient (70 to 85%)",
            if (recovery) VivaStyle.recoveryYellow else VivaStyle.textSecondary, values.count { it >= low && it < high },
        ),
        Triple(if (recovery) "Red (under 34%)" else "Poor (under 70%)", if (recovery) VivaStyle.recoveryRed else VivaStyle.warning, values.count { it < low }),
    )
    Column(modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VivaLabel("${metric.title} breakdown (days)", color = VivaStyle.textPrimary)
        Row(Modifier.fillMaxWidth().height(6.dp).background(VivaStyle.ringTrack)) {
            bands.forEach { (_, color, count) ->
                if (count > 0) Box(Modifier.weight(count.toFloat()).height(6.dp).background(color))
            }
        }
        bands.forEach { (label, color, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).background(color))
                Text("${count}x", style = VivaFont.number(12), color = VivaStyle.textPrimary)
                Text(label, style = VivaFont.caption, color = VivaStyle.textSecondary)
            }
        }
    }
}

/**
 * The chart itself. Level metrics (heart rate, variability, breathing, oxygen) draw as points on a
 * line against their own range; totals and scores draw as bars from zero. A day with no reading
 * leaves a gap. [average], when given, is drawn as a dashed line.
 */
@Composable
fun VivaTrendChart(
    metric: VivaTrendMetric,
    points: List<VivaTrendMetric.Point>,
    average: Double?,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val locale = Locale.getDefault()
    val values = points.mapNotNull { it.value }
    val lower: Double
    val upper: Double
    when {
        values.isEmpty() -> { lower = 0.0; upper = 1.0 }
        metric.unit == "%" && !metric.isLevel -> { lower = 0.0; upper = 100.0 }
        metric.chartsFromZero -> { lower = 0.0; upper = values.max() * 1.1 }
        else -> {
            lower = VivaTrendMetric.levelFloor(values)
            upper = values.max() + maxOf((values.max() - values.min()) * 0.3, 1.0)
        }
    }
    val span = maxOf(upper - lower, Double.MIN_VALUE)
    fun axisText(value: Double): String = when (metric) {
        VivaTrendMetric.HOURS_OF_SLEEP, VivaTrendMetric.RESTORATIVE_SLEEP -> VivaTrendMetric.clock(value)
        VivaTrendMetric.RESPIRATORY_RATE, VivaTrendMetric.STRAIN -> String.format(locale, "%.1f", value)
        else -> VivaScoreRing.roundHalfAwayFromZero(value).toString() + if (metric.unit == "%") "%" else ""
    }
    val axisStyle = VivaFont.number(9, FontWeight.Medium).copy(color = VivaStyle.textTertiary)
    val labelStyle = TextStyle(fontSize = 9.sp, color = VivaStyle.textTertiary, textAlign = TextAlign.Center)
    val markStyle = VivaFont.number(10)
    val week = points.size <= 7

    Canvas(modifier.fillMaxWidth().height(232.dp)) {
        val gutter = 34.dp.toPx()
        val plotHeight = 196.dp.toPx()
        val plotTop = 6.dp.toPx()
        val plotWidth = size.width - gutter
        val step = plotWidth / maxOf(points.size, 1)
        fun x(index: Int) = gutter + step * (index + 0.5f)
        fun y(value: Double) = plotTop + plotHeight * (1f - ((value.coerceIn(lower, upper) - lower) / span).toFloat())

        repeat(5) { index ->
            val lineY = plotTop + plotHeight * index / 4f
            drawLine(VivaStyle.divider.copy(alpha = 0.7f), Offset(gutter, lineY), Offset(size.width, lineY), strokeWidth = 0.5.dp.toPx())
            val layout = measurer.measure(axisText(upper - span * index / 4), axisStyle, maxLines = 1)
            drawText(layout, topLeft = Offset(0f, lineY - layout.size.height / 2f))
        }

        if (values.isEmpty()) {
            val layout = measurer.measure("NO DATA", VivaFont.label.copy(color = VivaStyle.textPrimary), maxLines = 1)
            drawText(layout, topLeft = Offset(gutter + (plotWidth - layout.size.width) / 2f, plotTop + (plotHeight - layout.size.height) / 2f))
        }

        fun label(text: String, centre: Offset, color: Color) {
            val layout = measurer.measure(text, markStyle.copy(color = color), maxLines = 1)
            drawText(layout, topLeft = Offset(centre.x - layout.size.width / 2f, maxOf(centre.y - 7.dp.toPx() - layout.size.height, 0f)))
        }

        if (metric.isLevel) {
            // The line breaks across days with no reading instead of bridging them.
            val line = Path()
            var drawing = false
            points.forEachIndexed { index, point ->
                val value = point.value
                if (value == null) {
                    drawing = false
                } else {
                    if (drawing) line.lineTo(x(index), y(value)) else line.moveTo(x(index), y(value))
                    drawing = true
                }
            }
            drawPath(line, metric.color, style = Stroke(1.5.dp.toPx()))
            val lastIndex = points.indexOfLast { it.value != null }
            points.forEachIndexed { index, point ->
                val value = point.value ?: return@forEachIndexed
                if (week || index == lastIndex) {
                    val centre = Offset(x(index), y(value))
                    drawCircle(VivaStyle.canvasBottom, 4.dp.toPx(), centre)
                    drawCircle(metric.markColor(value), 4.dp.toPx(), centre, style = Stroke(1.8.dp.toPx()))
                    label(metric.text(value, locale), centre, metric.markColor(value))
                }
            }
        } else {
            val barWidth = (step * 0.45f).coerceIn(1.5.dp.toPx(), 10.dp.toPx())
            points.forEachIndexed { index, point ->
                val value = point.value ?: return@forEachIndexed
                val top = minOf(y(value), plotTop + plotHeight - 2.dp.toPx())
                drawRect(metric.markColor(value), Offset(x(index) - barWidth / 2f, top), Size(barWidth, plotTop + plotHeight - top))
                if (week) label(metric.text(value, locale), Offset(x(index), top + 4.dp.toPx()), metric.markColor(value))
            }
        }

        if (average != null) {
            val lineY = y(average)
            drawLine(
                VivaStyle.textPrimary, Offset(gutter, lineY), Offset(size.width, lineY), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
            val tag = measurer.measure("AVG.", TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black), maxLines = 1)
            val pad = 3.dp.toPx()
            drawRoundRect(
                VivaStyle.textPrimary, Offset(gutter, lineY - tag.size.height / 2f - pad / 2f),
                Size(tag.size.width + pad * 2, tag.size.height + pad), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
            drawText(tag, topLeft = Offset(gutter + pad, lineY - tag.size.height / 2f))
        }

        // Day labels for a week; every seventh day for a month; month names for six months.
        val labelTop = plotTop + plotHeight + 6.dp.toPx()
        val stride = if (week) 1 else if (points.size <= 31) 7 else 30
        var index = 0
        while (index < points.size) {
            val date = points[index].date
            val text = when {
                week -> date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale) + "\n" + date.dayOfMonth
                points.size <= 31 -> date.month.getDisplayName(DateTextStyle.SHORT, locale) + "\n" + date.dayOfMonth
                else -> date.month.getDisplayName(DateTextStyle.SHORT, locale)
            }
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset((x(index) - layout.size.width / 2f).coerceIn(gutter, size.width - layout.size.width), labelTop))
            index += stride
        }
    }
}
