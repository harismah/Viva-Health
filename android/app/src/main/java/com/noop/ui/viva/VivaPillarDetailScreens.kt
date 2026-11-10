package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import com.noop.ui.BottomBarStyleStore
import com.noop.ui.logicalDayNow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One metric line on a pillar detail screen, already resolved for the day shown. */
internal data class VivaPillarRow(val metric: VivaTrendMetric, val value: Double?, val baseline: Double?)

internal object VivaPillarDetail {
    /**
     * The rows worth showing. A row with neither a value that day nor a baseline has nothing to say,
     * so it is left out rather than shown as dashes beside a home tile that found the figure another way.
     */
    fun rows(
        metrics: List<VivaTrendMetric>,
        day: DailyMetric?,
        days: List<DailyMetric>,
        dayKey: String,
        sleepPerformance: Map<String, Double> = emptyMap(),
    ): List<VivaPillarRow> = metrics.mapNotNull { metric ->
        val value = day?.let { metric.value(it, sleepPerformance) }
        val baseline = metric.baseline(days, dayKey, sleepPerformance)
        if (value == null && baseline == null) null else VivaPillarRow(metric, value, baseline)
    }

    /** `Today`, `Yesterday` or the date, for the day a detail screen was opened on. */
    fun title(date: LocalDate, today: LocalDate, locale: Locale): String =
        VivaHomeMapper.dayTitle(ChronoUnit.DAYS.between(date, today).toInt().coerceAtLeast(0), date, locale)
}

/**
 * The shared shape of the Recovery and Strain detail screens: the pillar ring, the metrics that
 * feed it compared with the last 30 days, and a week of trends. Twin of `VivaPillarDetailView.swift`.
 *
 * [handed] is the value the home ring showed. When the home pushes this screen it hands that value
 * over, so the two rings cannot disagree; opened on its own, the screen reads the day's stored score.
 */
@Composable
private fun PillarDetail(
    viewModel: AppViewModel,
    pillar: VivaTrendMetric,
    ringLabel: String,
    ringMaximum: Double,
    ringFormat: VivaScoreRing.Format,
    ringColor: (Double?) -> Color,
    dayKey: String?,
    handed: VivaHandedScore?,
    emptyNote: String,
    rows: List<VivaTrendMetric>,
    weekly: List<VivaTrendMetric>,
    infoTitle: String,
    onBack: () -> Unit,
    onTrend: (VivaTrendMetric) -> Unit,
    onCoach: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit,
) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    val todayRow by viewModel.today.collectAsStateWithLifecycle()
    val sleepPerformance = rememberSleepPerformance(viewModel, days)
    val locale = Locale.getDefault()
    val today = logicalDayNow()
    var showInfo by remember { mutableStateOf(false) }

    // The home hands over the key of the row it showed; with none, this is today's row.
    val key = dayKey ?: todayRow?.day ?: today.toString()
    val date = runCatching { LocalDate.parse(key) }.getOrDefault(today)
    val isToday = key == (todayRow?.day ?: today.toString())
    val day = todayRow?.takeIf { it.day == key } ?: days.lastOrNull { it.day == key }
    val score = if (handed != null) handed.value else day?.let { pillar.value(it, sleepPerformance) }
    val shown = VivaPillarDetail.rows(rows, day, days, key, sleepPerformance)

    VivaDetailScaffold(
        title = if (isToday) "Today" else VivaPillarDetail.title(date, today, locale),
        onBack = onBack, trailing = VivaBarTrailing.INFO, onTrailing = { showInfo = true },
        coachMessage = if (BottomBarStyleStore.coachEnabled && score != null) "$ringLabel ${pillar.text(score, locale)}. Ask what moved it." else null,
        onCoach = onCoach,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            VivaScoreRing(
                value = score, maximum = ringMaximum, color = ringColor(score), format = ringFormat,
                size = VivaScoreRing.Size.LARGE, label = ringLabel,
            )
        }
        if (score == null) VivaCallout(emptyNote)
        if (shown.isNotEmpty()) {
            VivaCard {
                Column {
                    shown.forEachIndexed { index, row ->
                        Box(
                            Modifier.fillMaxWidth().height(56.dp).vivaPressable { onTrend(row.metric) },
                            contentAlignment = Alignment.Center,
                        ) {
                            VivaMetricRow(
                                icon = row.metric.icon, label = row.metric.title, value = row.metric.text(row.value, locale),
                                baseline = row.baseline?.let { row.metric.text(it, locale) },
                                trend = VivaHomeMapper.trend(row.value, row.baseline),
                            )
                        }
                        if (index < shown.lastIndex) VivaDivider()
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(30.dp).background(VivaStyle.inset, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VivaTrendMark(VivaTrend.UP)
                    VivaTrendMark(VivaTrend.DOWN)
                    Text(if (isToday) "Today" else "This day", style = VivaFont.caption, color = VivaStyle.textPrimary)
                    Text("vs. last 30 days", style = VivaFont.caption, color = VivaStyle.textSecondary)
                }
            }
        }
        extra()
        VivaSectionHeader("Weekly Trends", Modifier.padding(top = VivaStyle.sectionGap - VivaStyle.cardGap))
        weekly.forEach { metric ->
            VivaWeekBarCard(
                title = metric.title, days = metric.weekDays(days, date, locale, sleepPerformance),
                style = metric.style, onClick = { onTrend(metric) },
            )
        }
    }

    if (showInfo) {
        VivaInfoSheet(
            title = infoTitle,
            paragraphs = listOf(pillar.summary) + shown.map { "${it.metric.title}: ${it.metric.summary}" },
            onDismiss = { showInfo = false },
        )
    }
}

@Composable
fun VivaRecoveryDetailScreen(
    viewModel: AppViewModel,
    dayKey: String?,
    handed: VivaHandedScore?,
    onBack: () -> Unit,
    onTrend: (VivaTrendMetric) -> Unit,
    onCoach: () -> Unit,
    onBehaviorInsights: () -> Unit,
) {
    PillarDetail(
        viewModel = viewModel, pillar = VivaTrendMetric.RECOVERY, ringLabel = "Recovery", ringMaximum = 100.0,
        ringFormat = VivaScoreRing.Format.PERCENT, ringColor = { VivaStyle.recoveryColor(it) },
        dayKey = dayKey, handed = handed,
        emptyNote = "Viva needs a few nights of sleep to learn your baseline before it can score Recovery.",
        rows = listOf(VivaTrendMetric.HRV, VivaTrendMetric.RESTING_HR, VivaTrendMetric.RESPIRATORY_RATE, VivaTrendMetric.SLEEP_PERFORMANCE),
        weekly = listOf(
            VivaTrendMetric.RECOVERY, VivaTrendMetric.HRV, VivaTrendMetric.RESTING_HR,
            VivaTrendMetric.RESPIRATORY_RATE, VivaTrendMetric.SLEEP_PERFORMANCE,
        ),
        infoTitle = "What is Recovery?", onBack = onBack, onTrend = onTrend, onCoach = onCoach,
    ) {
        VivaCard(onClick = onBehaviorInsights) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Behavior insights", style = VivaFont.bodyStrong, color = VivaStyle.textPrimary)
                    Text("See which of your logged habits move your Recovery.", style = VivaFont.caption, color = VivaStyle.textSecondary)
                    VivaLinkLabel("See impacts")
                }
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = VivaStyle.textTertiary, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
fun VivaStrainDetailScreen(
    viewModel: AppViewModel,
    dayKey: String?,
    handed: VivaHandedScore?,
    onBack: () -> Unit,
    onTrend: (VivaTrendMetric) -> Unit,
    onCoach: () -> Unit,
    onAddActivity: () -> Unit,
    onWorkouts: () -> Unit,
) {
    PillarDetail(
        viewModel = viewModel, pillar = VivaTrendMetric.STRAIN, ringLabel = "Strain", ringMaximum = 21.0,
        ringFormat = VivaScoreRing.Format.DECIMAL1, ringColor = { VivaStyle.strain },
        dayKey = dayKey, handed = handed,
        emptyNote = "Wear your strap through the day and your Strain will build here.",
        rows = listOf(VivaTrendMetric.STEPS, VivaTrendMetric.CALORIES),
        weekly = listOf(VivaTrendMetric.STRAIN, VivaTrendMetric.CALORIES, VivaTrendMetric.STEPS),
        infoTitle = "What is Strain?", onBack = onBack, onTrend = onTrend, onCoach = onCoach,
    ) {
        VivaCard(title = "Activities") {
            Text(
                "Log a workout to see its time in each heart-rate zone and its share of the day's Strain.",
                style = VivaFont.caption, color = VivaStyle.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VivaStyle.cardGap)) {
                VivaCardButton("Add activity", Modifier.weight(1f), icon = Icons.Rounded.Add, onClick = onAddActivity)
                VivaCardButton("All workouts", Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.List, onClick = onWorkouts)
            }
        }
    }
}
