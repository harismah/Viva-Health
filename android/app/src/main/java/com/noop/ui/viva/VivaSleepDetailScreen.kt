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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.data.DailyMetric
import com.noop.data.SleepSession
import com.noop.ui.AppViewModel
import com.noop.ui.BottomBarStyleStore
import com.noop.ui.ClockPrefs
import com.noop.ui.SleepModel
import com.noop.ui.buildHostedSleepModel
import com.noop.ui.logicalDayNow
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** The pure rules of the Sleep detail screen, kept apart from its layout so they can be tested. */
internal object VivaSleepDetail {
    /**
     * The night the screen describes: the newest day that carries stage minutes. This is the rule the
     * shared sleep model anchors itself by, restated here only to find that night's session and
     * wake count; every figure on the screen still comes from the model.
     */
    fun anchorDay(days: List<DailyMetric>): String? =
        days.lastOrNull { (it.deepMin ?: 0.0) + (it.remMin ?: 0.0) + (it.lightMin ?: 0.0) > 0.0 }?.day

    /**
     * How many days before [today] the described night is; 0 when it is today's or unknown. The
     * screen titles itself from this, so an older night is never presented as last night.
     */
    fun nightOffset(dayKey: String?, today: LocalDate): Int =
        dayKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { maxOf(java.time.temporal.ChronoUnit.DAYS.between(it, today).toInt(), 0) } ?: 0

    /** Optimal from 85%, sufficient from 70%, poor below. */
    fun level(percent: Double?): VivaLevel? = when {
        percent == null -> null
        percent >= 85 -> VivaLevel.OPTIMAL
        percent >= 70 -> VivaLevel.SUFFICIENT
        else -> VivaLevel.POOR
    }

    /** The descriptive sleep need the Hours vs. Needed figure is scored against: typical sleep, never under 7.5 hours. */
    fun sleepNeedMinutes(typicalTotalMin: Double?): Double = maxOf(450.0, typicalTotalMin ?: 450.0)

    /** A stage's whole-percent share of the night. */
    fun share(minutes: Double, total: Double): Int =
        if (total <= 0) 0 else VivaScoreRing.roundHalfAwayFromZero(minutes / total * 100).toInt()

    /** The band drawn as a stage's typical range: a tenth either side of its typical minutes. */
    fun typicalRange(typicalMin: Double?): ClosedFloatingPointRange<Double>? = typicalMin?.let { (it * 0.9)..(it * 1.1) }

    fun percentText(percent: Double?): String? = percent?.let { "${VivaScoreRing.roundHalfAwayFromZero(it)}%" }
}

/** Everything the screen loads for one night. */
private class VivaSleepNight(
    val model: SleepModel,
    val session: SleepSession?,
    val heartRate: List<Double>,
    val wakeEvents: Int?,
    val dayKey: String?,
)

private suspend fun loadNight(viewModel: AppViewModel, days: List<DailyMetric>, zone: ZoneId): VivaSleepNight? {
    val model = runCatching { buildHostedSleepModel(viewModel.repo, viewModel.activeStrapId, days) }.getOrNull() ?: return null
    val dayKey = VivaSleepDetail.anchorDay(days)
    val date = dayKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val session = date?.let {
        val dayStart = it.atStartOfDay(zone).toEpochSecond()
        val sessions = runCatching {
            viewModel.repo.sleepSessionsUnion(viewModel.activeStrapId, dayStart - 86_400, dayStart + 86_400)
        }.getOrDefault(emptyList())
        VivaHomeMapper.lastSleep(sessions, it, zone)
    }
    val heartRate = session?.let {
        runCatching {
            viewModel.repo.hrBucketsUnion(viewModel.activeStrapId, it.effectiveStartTs, it.endTs, bucketSeconds = 60L)
        }.getOrDefault(emptyList()).map { bucket -> bucket.avgBpm }
    } ?: emptyList()
    return VivaSleepNight(model, session, heartRate, days.lastOrNull { it.day == dayKey }?.disturbances, dayKey)
}

/**
 * Sleep detail: the Sleep Performance ring, what fed it, last night broken down by stage against
 * the wearer's typical night, and the week's trends. Reads the same sleep model the full Sleep
 * screen builds, so every figure here agrees with that screen. Twin of `VivaSleepDetailView.swift`.
 *
 * [handed] is the score the home ring showed; given, the ring here shows that and nothing else.
 */
@Composable
fun VivaSleepDetailScreen(
    viewModel: AppViewModel,
    handed: VivaHandedScore?,
    onBack: () -> Unit,
    onTrend: (VivaTrendMetric) -> Unit,
    onEdit: () -> Unit,
    onCoach: () -> Unit,
) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    val sleepPerformance = rememberSleepPerformance(viewModel, days)
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val locale = Locale.getDefault()
    val is24Hour = ClockPrefs.uses24Hour(context)
    val today = logicalDayNow()

    var night by remember { mutableStateOf<VivaSleepNight?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(days) {
        night = loadNight(viewModel, days, zone)
        loaded = true
    }
    var showInfo by remember { mutableStateOf(false) }

    val model = night?.model
    val score = if (handed != null) handed.value else model?.performance?.latest
    val coachLine = VivaSleepDetail.percentText(score)
        ?.takeIf { BottomBarStyleStore.coachEnabled }
        ?.let { "Sleep Performance $it. Ask what moved it." }
    val onset = night?.session?.let { VivaHomeMapper.clock(it.effectiveStartTs, zone, locale, is24Hour) }
    val wake = night?.session?.let { VivaHomeMapper.clock(it.endTs, zone, locale, is24Hour) }

    // The model describes the newest night that has stage data, which is not always last night.
    val nightOffset = VivaSleepDetail.nightOffset(night?.dayKey, today)
    VivaDetailScaffold(
        title = VivaHomeMapper.dayTitle(nightOffset, today.minusDays(nightOffset.toLong()), locale),
        onBack = onBack, trailing = VivaBarTrailing.INFO, onTrailing = { showInfo = true },
        coachMessage = coachLine, onCoach = onCoach,
    ) {
        if (nightOffset > 0) {
            VivaCallout("Last night has no sleep-stage data yet, so this is your most recent analysed night.")
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            VivaScoreRing(
                value = score, maximum = 100.0, color = VivaStyle.sleep, format = VivaScoreRing.Format.PERCENT,
                size = VivaScoreRing.Size.LARGE, label = "Sleep Performance",
            )
        }
        if (model != null) {
            Contributors(model)
            Box(Modifier.fillMaxWidth().padding(top = VivaStyle.sectionGap - VivaStyle.cardGap)) {
                VivaSectionHeader("Last Night's Sleep", subtitle = "Compared with your typical night")
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .vivaPressable(onEdit)
                        .semantics { role = Role.Button; contentDescription = "Edit sleep" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("EDIT", style = VivaFont.action, color = VivaStyle.textPrimary)
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(13.dp))
                }
            }
            NightCard(model, night?.heartRate.orEmpty(), onset, wake)
            HoursVsNeededCard(model)
            ConsistencyCard(model, onset, wake)
            EfficiencyCard(model)
            night?.wakeEvents?.let { WakeEventsCard(it) }
        } else if (loaded) {
            VivaCallout("No sleep recorded yet. Wear your strap to bed and your night will appear here.")
        }
        VivaSectionHeader("Weekly Trends", Modifier.padding(top = VivaStyle.sectionGap - VivaStyle.cardGap))
        listOf(
            VivaTrendMetric.SLEEP_PERFORMANCE, VivaTrendMetric.HOURS_OF_SLEEP,
            VivaTrendMetric.RESTORATIVE_SLEEP, VivaTrendMetric.SLEEP_EFFICIENCY,
        ).forEach { metric ->
            VivaWeekBarCard(
                title = metric.title, days = metric.weekDays(days, today, locale, sleepPerformance),
                style = metric.style, onClick = { onTrend(metric) },
            )
        }
    }

    if (showInfo) {
        VivaInfoSheet(
            title = "What is Sleep Performance?",
            paragraphs = listOf(
                VivaTrendMetric.SLEEP_PERFORMANCE.summary,
                "Hours vs. Needed is the share of your sleep need you got. Sleep Consistency compares when you went to bed with recent nights. Sleep Efficiency is the share of your time in bed spent asleep.",
                "A score of 85% or more is optimal, 70 to 85% is sufficient, and under 70% is poor.",
            ),
            onDismiss = { showInfo = false },
        )
    }
}

// Contributors

@Composable
private fun Contributors(model: SleepModel) {
    VivaCard {
        Column {
            ContributorRow(Icons.Outlined.Schedule, "Hours vs. Needed", model.hoursVsNeeded.latest)
            VivaDivider()
            ContributorRow(Icons.Outlined.Sync, "Sleep Consistency", model.consistency.latest)
            VivaDivider()
            ContributorRow(Icons.Outlined.Bed, "Sleep Efficiency", model.efficiency.latest)
            VivaDivider()
            ContributorRow(Icons.Outlined.AutoAwesome, "Restorative Sleep", model.restorative.latest)
        }
        Row(
            Modifier.fillMaxWidth().height(32.dp).background(VivaStyle.inset, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            listOf(VivaLevel.POOR to "Poor", VivaLevel.SUFFICIENT to "Sufficient", VivaLevel.OPTIMAL to "Optimal").forEach { (level, name) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(width = 12.dp, height = 3.dp).background(level.color, CircleShape))
                    Text(name, style = VivaFont.caption, color = VivaStyle.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ContributorRow(icon: ImageVector, label: String, percent: Double?) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(18.dp))
        }
        VivaCardTitle(label, Modifier.weight(1f), maxLines = 1)
        val text = VivaSleepDetail.percentText(percent)
        if (text != null) {
            VivaLevelStrip(VivaSleepDetail.level(percent))
            Text(
                text, Modifier.widthIn(min = 58.dp), style = VivaFont.valueMedium, color = VivaStyle.textPrimary,
                textAlign = TextAlign.End, maxLines = 1,
            )
        } else {
            Text("More nights needed", style = VivaFont.caption, color = VivaStyle.textSecondary, maxLines = 1)
        }
    }
}

// Last night

@Composable
private fun NightCard(model: SleepModel, heartRate: List<Double>, onset: String?, wake: String?) {
    val stages = model.stages
    val total = maxOf(stages.total, 1.0)
    VivaCard(title = "Hours of Sleep") {
        VivaValueHeader(
            value = VivaTrendMetric.clock(stages.asleep),
            baseline = model.typicalTotalMin?.let { VivaTrendMetric.clock(it) },
            trend = VivaHomeMapper.trend(stages.asleep, model.typicalTotalMin),
        )
        if (heartRate.size > 1) {
            VivaLineChart(
                values = heartRate, color = VivaStyle.sleep, yLabels = VivaLineChart.axisLabels(heartRate),
                startLabel = onset, endLabel = wake,
            )
        } else if (onset != null && wake != null) {
            val labelStyle = VivaFont.number(10, androidx.compose.ui.text.font.FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth()) {
                Text(onset, style = labelStyle, color = VivaStyle.textPrimary)
                Spacer(Modifier.weight(1f))
                Text(wake, style = labelStyle, color = VivaStyle.textPrimary)
            }
        }
        VivaDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DashedBox()
            VivaLabel("Typical range")
            Spacer(Modifier.weight(1f))
            VivaLabel("Duration")
            Text(VivaTrendMetric.clock(stages.total), style = VivaFont.valueSmall, color = VivaStyle.textPrimary)
        }
        StageRow(VivaStyle.stageAwake, "Awake", stages.awake, total, typical = null)
        StageRow(VivaStyle.stageLight, "Light", stages.light, total, model.typicalLightMin)
        StageRow(VivaStyle.stageDeep, "SWS (Deep)", stages.deep, total, model.typicalDeepMin)
        StageRow(VivaStyle.stageREM, "REM", stages.rem, total, model.typicalRemMin)
        VivaDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(12.dp).background(
                    Brush.linearGradient(listOf(VivaStyle.stageDeep, VivaStyle.stageREM)), RoundedCornerShape(2.dp),
                ),
            )
            VivaCardTitle("Restorative Sleep", Modifier.weight(1f), maxLines = 1)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(VivaTrendMetric.clock(stages.deep + stages.rem), style = VivaFont.valueMedium, color = VivaStyle.textPrimary)
                val deep = model.typicalDeepMin
                val rem = model.typicalRemMin
                if (deep != null && rem != null) {
                    Text(VivaTrendMetric.clock(deep + rem), style = VivaFont.baseline, color = VivaStyle.textTertiary)
                }
            }
        }
    }
}

/** The legend glyph for the dashed typical-range bracket on a stage bar. */
@Composable
private fun DashedBox() {
    Canvas(Modifier.size(width = 13.dp, height = 10.dp)) {
        val dash = 2.5.dp.toPx()
        drawRoundRect(
            VivaStyle.textSecondary, cornerRadius = CornerRadius(1.dp.toPx()),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
        )
    }
}

@Composable
private fun StageRow(color: Color, label: String, minutes: Double, total: Double, typical: Double?) {
    VivaStageRow(
        color = color, label = label, percent = VivaSleepDetail.share(minutes, total),
        duration = VivaTrendMetric.clock(minutes), value = minutes, maximum = total,
        range = VivaSleepDetail.typicalRange(typical),
    )
}

// Cards

@Composable
private fun HoursVsNeededCard(model: SleepModel) {
    val asleep = model.stages.asleep
    val need = VivaSleepDetail.sleepNeedMinutes(model.typicalTotalMin)
    val scale = maxOf(asleep, need, 1.0)
    VivaCard(title = "Hours vs. Needed") {
        VivaValueHeader(
            value = VivaSleepDetail.percentText(model.hoursVsNeeded.latest) ?: "--",
            baseline = VivaSleepDetail.percentText(model.hoursVsNeeded.typical),
            trend = VivaHomeMapper.trend(model.hoursVsNeeded.latest, model.hoursVsNeeded.typical),
        )
        VivaAmountBar("Hours of sleep", VivaTrendMetric.clock(asleep), asleep / scale, VivaStyle.sleep)
        VivaAmountBar("Sleep needed", VivaTrendMetric.clock(need), need / scale, VivaStyle.textTertiary)
    }
}

@Composable
private fun ConsistencyCard(model: SleepModel, onset: String?, wake: String?) {
    VivaCard(title = "Sleep Consistency") {
        val value = model.consistency.latest
        if (value != null) {
            VivaValueHeader(
                value = VivaSleepDetail.percentText(value) ?: "--",
                baseline = VivaSleepDetail.percentText(model.consistency.typical),
                trend = VivaHomeMapper.trend(value, model.consistency.typical),
            )
            if (onset != null && wake != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VivaLabel("Bed")
                    Text(onset, style = VivaFont.number(12), color = VivaStyle.textPrimary)
                    Spacer(Modifier.weight(1f))
                    VivaLabel("Wake")
                    Text(wake, style = VivaFont.number(12), color = VivaStyle.textPrimary)
                }
            }
        } else {
            Text("--%", style = VivaFont.valueLarge, color = VivaStyle.textPrimary)
            VivaCallout("A few more nights are needed to compare your bed times.", fill = VivaStyle.inset)
        }
    }
}

@Composable
private fun EfficiencyCard(model: SleepModel) {
    val stages = model.stages
    val total = maxOf(stages.total, 1.0)
    VivaCard(title = "Sleep Efficiency") {
        VivaValueHeader(
            value = VivaSleepDetail.percentText(model.efficiency.latest) ?: "--",
            baseline = VivaSleepDetail.percentText(model.efficiency.typical),
            trend = VivaHomeMapper.trend(model.efficiency.latest, model.efficiency.typical),
        )
        VivaAmountBar("Asleep", VivaTrendMetric.clock(stages.asleep), stages.asleep / total, VivaStyle.sleep)
        VivaAmountBar("Awake", VivaTrendMetric.clock(stages.awake), stages.awake / total, VivaStyle.stageAwake)
    }
}

@Composable
private fun WakeEventsCard(events: Int) {
    VivaCard(padding = 14.dp) {
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VivaSwatch(VivaStyle.stageAwake, 10.dp)
            VivaCardTitle("Wake events", Modifier.weight(1f))
            Text(events.toString(), style = VivaFont.valueSmall, color = VivaStyle.textPrimary)
        }
    }
}
