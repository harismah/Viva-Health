package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A seven-day chart card: one column per day, today's column lifted on a lighter band with its
 * value printed above the mark. Bars for totals, dots for vitals, stacks for split totals. Twin of
 * `VivaWeekBarCard.swift`.
 */
object VivaWeekBarCard {
    enum class Style { BAR, DOT, STACKED }

    data class Segment(val value: Double, val color: Color)

    data class Day(
        val label: String,
        val date: Int,
        val segments: List<Segment>,
        val valueText: String?,
        val isToday: Boolean,
    ) {
        val total: Double get() = segments.sumOf { maxOf(it.value, 0.0) }
    }

    /** Tallest day total, the top of the chart's scale. */
    fun peak(days: List<Day>): Double = days.maxOfOrNull { it.total } ?: 0.0

    /** True when no day carries a value, so the card shows its no-data state instead of a flat axis. */
    fun isEmpty(days: List<Day>): Boolean = days.all { it.segments.isEmpty() }
}

private val ChartHeight = 150.dp
private val ValueHeadroom = 16.dp

@Composable
fun VivaWeekBarCard(
    title: String,
    days: List<VivaWeekBarCard.Day>,
    modifier: Modifier = Modifier,
    style: VivaWeekBarCard.Style = VivaWeekBarCard.Style.BAR,
    legend: List<Pair<String, Color>> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    VivaCard(
        modifier = modifier,
        title = title,
        accessory = if (onClick == null) VivaCardAccessory.NONE else VivaCardAccessory.CHEVRON,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (legend.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    legend.forEach { (name, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(7.dp).background(color))
                            VivaLabel(name, color = VivaStyle.textPrimary)
                        }
                    }
                }
            }
            if (VivaWeekBarCard.isEmpty(days)) {
                Column(
                    Modifier.fillMaxWidth().height(ChartHeight + 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Icon(Icons.Outlined.BarChart, contentDescription = null, tint = VivaStyle.textTertiary, modifier = Modifier.size(22.dp))
                    VivaLabel("No data", color = VivaStyle.textPrimary)
                }
            } else {
                WeekChart(days, style)
            }
        }
    }
}

@Composable
private fun WeekChart(days: List<VivaWeekBarCard.Day>, style: VivaWeekBarCard.Style) {
    val peak = maxOf(VivaWeekBarCard.peak(days), Double.MIN_VALUE)
    Box(Modifier.fillMaxWidth()) {
        GridLines()
        Row(Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(ChartHeight), contentAlignment = Alignment.BottomCenter) {
                        if (day.isToday) {
                            Box(Modifier.width(22.dp).fillMaxSize().background(VivaStyle.chip.copy(alpha = 0.75f)))
                        }
                        DayColumn(day, peak, style)
                    }
                    Spacer(Modifier.height(6.dp))
                    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal)
                    val labelColor = if (day.isToday) VivaStyle.textPrimary else VivaStyle.textTertiary
                    Text(day.label, style = labelStyle, color = labelColor, maxLines = 1)
                    Spacer(Modifier.height(1.dp))
                    Text(day.date.toString(), style = labelStyle, color = labelColor, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun GridLines() {
    Column(Modifier.fillMaxWidth().height(ChartHeight), verticalArrangement = Arrangement.SpaceBetween) {
        repeat(5) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(VivaStyle.divider.copy(alpha = 0.7f)))
        }
    }
}

@Composable
private fun DayColumn(day: VivaWeekBarCard.Day, peak: Double, style: VivaWeekBarCard.Style) {
    val usable = ChartHeight - ValueHeadroom
    val markHeight = usable * (day.total / peak).toFloat()
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (day.valueText != null && day.segments.isNotEmpty()) {
            Text(
                day.valueText,
                style = VivaFont.number(9),
                color = day.segments.last().color,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(3.dp))
        }
        when (style) {
            VivaWeekBarCard.Style.DOT -> if (day.segments.isNotEmpty()) {
                Box(Modifier.size(6.dp).border(1.5.dp, day.segments.first().color, CircleShape))
                Spacer(Modifier.height((markHeight - 6.dp).coerceAtLeast(0.dp)))
            }
            VivaWeekBarCard.Style.BAR, VivaWeekBarCard.Style.STACKED -> Column(Modifier.width(10.dp)) {
                // First segment sits on the axis, so the stack is drawn last-to-first from the top.
                day.segments.asReversed().forEach { segment ->
                    val share = usable * (maxOf(segment.value, 0.0) / peak).toFloat()
                    val floor = if (segment.value > 0) 2.dp else 0.dp
                    Box(Modifier.fillMaxWidth().height(maxOf(share, floor)).background(segment.color))
                }
            }
        }
    }
}
