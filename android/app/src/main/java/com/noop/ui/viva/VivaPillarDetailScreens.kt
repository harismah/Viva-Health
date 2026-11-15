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
import androidx.compose.ui.platform.LocalContext
import com.noop.analytics.Baselines
import com.noop.data.DailyMetric
import com.noop.ui.NoopPrefs
import com.noop.ui.recoveryCalibrationNights
import com.noop.ui.AppViewModel
import com.noop.ui.BottomBarStyleStore
import com.noop.ui.logicalDayNow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One metric line on a pillar detail screen, already resolved for the day shown. */
/** [carriedDay] is set when [value] was carried from an earlier day; the row then shows that day in place of the baseline. */
internal data class VivaPillarRow(val metric: VivaTrendMetric, val value: Double?, val baseline: Double?, val carriedDay: String? = null)

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
        /** True on today: a vital with no reading of its own shows the carried one the Health Monitor shows. */
        carry: Boolean = false,
        /** Values the screen that opened this one already showed; they win over this screen's own lookup. */
        handed: Map<VivaTrendMetric, VivaHandedScore> = emptyMap(),
    ): List<VivaPillarRow> = metrics.mapNotNull { metric ->
        val given = handed[metric]
        val reading = when {
            given != null -> VivaTrendMetric.Reading(given.value, given.carriedDay)
            carry -> metric.todayOrCarried(days, day, dayKey, sleepPerformance)
            else -> VivaTrendMetric.Reading(day?.let { metric.value(it, sleepPerformance) }, null)
        }
        val baseline = metric.baseline(days, dayKey, sleepPerformance)
        if (reading.value == null && baseline == null) null else VivaPillarRow(metric, reading.value, baseline, reading.carriedDay)
    }

    /** Why the day has no Recovery of its own, naming the input that is actually missing; null when it has one. */
    fun recoveryEmptyNote(day: DailyMetric?, isToday: Boolean, calibrationNights: Int?): String? {
        if (day?.recovery != null) return null
        if (!isToday) return "No Recovery was scored for this day."
        return when (val gap = VivaConsistency.recoveryGap(calibrationNights, day?.totalSleepMin != null, day?.avgHrv != null)) {
            is VivaConsistency.RecoveryGap.Calibrating -> "Learning your baseline, ${gap.nights} of ${Baselines.minNightsSeed} nights."
            VivaConsistency.RecoveryGap.NoSleep -> "No sleep has been recorded for last night yet, so today has no Recovery score."
            VivaConsistency.RecoveryGap.NoHrv -> "Last night recorded no heart rate variability, so Recovery could not be scored. Check the strap was worn overnight and has synced."
            VivaConsistency.RecoveryGap.NotScoredYet -> "Today's Recovery has not been scored yet."
        }
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
    /** Why the day has no score of its own, given its row, whether it is today and the score shown; null when there is nothing to explain. */
    emptyNote: (day: DailyMetric?, isToday: Boolean, score: Double?) -> String?,
    /** Row values the screen that opened this one already showed. */
    handedRows: Map<VivaTrendMetric, VivaHandedScore> = emptyMap(),
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
    val shown = VivaPillarDetail.rows(rows, day, days, key, sleepPerformance, carry = isToday, handed = handedRows)

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
        // A score carried from an earlier night says which, in the words Home used under its ring.
        handed?.carriedDay?.let { VivaLabel(vivaCarriedNote(it), Modifier.align(Alignment.CenterHorizontally)) }
        emptyNote(day, isToday, score)?.let { VivaCallout(it) }
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
                                baseline = row.carriedDay?.let { VivaConsistency.dayLabel(it, locale) } ?: row.baseline?.let { row.metric.text(it, locale) },
                                trend = if (row.carriedDay != null) VivaTrend.FLAT else VivaHomeMapper.trend(row.value, row.baseline),
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
    /** The Sleep ring Home showed, so the Sleep Performance row here equals it. */
    handedSleep: VivaHandedScore?,
    onBack: () -> Unit,
    onTrend: (VivaTrendMetric) -> Unit,
    onCoach: () -> Unit,
    onBehaviorInsights: () -> Unit,
) {
    val baselines by viewModel.chargeBaselines.collectAsStateWithLifecycle()
    // The same epoch-aware night count Home's "calibrating" state uses.
    val calibrating = recoveryCalibrationNights(
        baselines?.hrvHistory?.values.orEmpty(), baselines?.hrvHistory?.dayKeys.orEmpty(), hasRecovery = false,
        hrvBaselineEpoch = NoopPrefs.of(LocalContext.current).getLong(Baselines.hrvBaselineEpochKey, 0L).toDouble(),
    )
    PillarDetail(
        viewModel = viewModel, pillar = VivaTrendMetric.RECOVERY, ringLabel = "Recovery", ringMaximum = 100.0,
        ringFormat = VivaScoreRing.Format.PERCENT, ringColor = { VivaStyle.recoveryColor(it) },
        dayKey = dayKey, handed = handed,
        emptyNote = { day, isToday, _ -> VivaPillarDetail.recoveryEmptyNote(day, isToday, calibrating) },
        handedRows = handedSleep?.let { mapOf(VivaTrendMetric.SLEEP_PERFORMANCE to it) }.orEmpty(),
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
        emptyNote = { _, _, score -> if (score == null) "Wear your strap through the day and your Strain will build here." else null },
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
