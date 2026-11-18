package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.noop.ui.workoutsAllSources
import com.noop.ui.AppViewModel
import com.noop.ui.ClockPrefs
import com.noop.ui.StressLoadCancellation
import com.noop.ui.StressModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Time spent in each stress band over [points], as three bars sharing one scale. */
@Composable
private fun BandCard(title: String, caption: String, points: List<VivaStress.Point>) {
    if (points.isEmpty()) return
    val minutes = VivaStress.bandMinutes(points)
    val total = minutes.sum().toDouble()
    VivaCard(title = title) {
        listOf("Low" to VivaStyle.sleep, "Medium" to VivaStyle.recoveryGreen, "High" to VivaStyle.recoveryYellow).forEachIndexed { index, (label, color) ->
            VivaAmountBar(label, VivaHomeMapper.duration(minutes[index] * 60L), minutes[index] / total, color)
        }
        Text(caption, style = VivaFont.caption, color = VivaStyle.textSecondary)
    }
}

/**
 * Stress Monitor: the stress level right now on the 0-3 dial, the last 24 hours, the time spent in
 * each band awake and asleep, the 14-day trend of the daily score, and the breathing sessions. The
 * dial and the graph read [vivaStressNow], the same reading as the home tile and card; the daily
 * `StressModel` score is the fallback when the strap has banked no heart rate in the last day.
 */
@Suppress("UNUSED_PARAMETER") // onFullAnalysis: the upstream screen scores stress by another model, so it is not linked from here
@Composable
fun VivaStressMonitorScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onBreathe: () -> Unit,
    onFullAnalysis: () -> Unit,
    onCoach: () -> Unit,
) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    var model by remember { mutableStateOf<StressModel?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    // 0 is the last 24 hours; n is the local day n days back, midnight to midnight.
    var dayOffset by remember { mutableStateOf(0) }
    val date = LocalDate.now(zone).minusDays(dayOffset.toLong())
    var now by remember { mutableStateOf<VivaStressNow?>(null) }
    var nowLoaded by remember { mutableStateOf(false) }
    var workouts by remember { mutableStateOf<List<com.noop.data.WorkoutRow>>(emptyList()) }
    LaunchedEffect(days, viewModel.activeStrapId, dayOffset) {
        now = if (dayOffset == 0) vivaStressNow(viewModel, days) else vivaStressOn(viewModel, days, date, zone)
        nowLoaded = true
        // The same read Home marks its card from, so an activity shows on both charts.
        workouts = now?.let {
            runCatching { viewModel.repo.workoutsAllSources(viewModel.deviceId, it.startTs, it.endTs) }.getOrDefault(emptyList())
        }.orEmpty()
    }
    var trend by remember { mutableStateOf<List<Double>>(emptyList()) }
    LaunchedEffect(days, viewModel.activeStrapId) { trend = vivaStressDaily(viewModel, days, zone) }
    fun clock(ts: Long) = VivaHomeMapper.clock(ts, ZoneId.systemDefault(), Locale.getDefault(), ClockPrefs.uses24Hour(context))

    LaunchedEffect(days) {
        // Off the main thread: the stored series spans all history (same read as the home card).
        model = StressLoadCancellation.read {
            withContext(Dispatchers.Default) {
                val stored = viewModel.repo.metricSeries("my-whoop", "stress", "0000-01-01", "9999-12-31")
                    .associate { it.day to it.value.coerceIn(0.0, 3.0) }
                StressModel.build(days, stored)
            }
        }
        loaded = true
    }

    val current = model
    val today = dayOffset == 0
    // A past day shows its average; today shows the reading right now, else the daily score once that read came back empty.
    // Until the fresh read lands, today shows the last reading Home stored (VivaScoreCache), not "Loading".
    val cached = remember(dayOffset) { if (today) VivaScoreCache.get(context, "stress", LocalDate.now(zone).toString()) else null }
    val score = if (today) now?.value ?: current?.score?.takeIf { nowLoaded } ?: cached?.takeIf { !nowLoaded } else now?.mean
    val caption = if (score == null && !(loaded && nowLoaded)) "Loading" else VivaHomeMapper.stressLabel(score)

    VivaDetailScaffold(
        title = "Stress Monitor",
        onBack = onBack,
        trailing = VivaBarTrailing.INFO,
        onTrailing = { showInfo = true },
        coachMessage = score?.let { "Stress is ${caption.lowercase()} today. Ask what is driving it." },
        onCoach = onCoach,
    ) {
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            VivaStepButton(Icons.Rounded.ChevronLeft, "Previous day", dayOffset < 13) { dayOffset++ }
            Text(
                if (today) "TODAY" else VivaConsistency.dayLabel(date.toString()).uppercase(),
                Modifier.padding(horizontal = 14.dp), style = VivaFont.action, color = VivaStyle.textPrimary, maxLines = 1,
            )
            VivaStepButton(Icons.Rounded.ChevronRight, "Next day", dayOffset > 0) { dayOffset-- }
        }
        VivaHalfDial(
            value = score, maximum = 3.0, caption = caption,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            time = if (!today) now?.let { "Day average" } else now?.let { "Updated ${clock(it.endTs)}" } ?: current?.takeIf { nowLoaded }?.let { "Today" },
        )
        now?.takeIf { it.levels.size > 1 }?.let {
            VivaCard(title = if (today) "Last 24 hours" else "Midnight to midnight") {
                VivaLineChart(
                    values = it.levels, color = VivaStyle.textPrimary, range = 0.0..3.0,
                    yLabels = listOf("3.0", "2.0", "1.0", "0.0"), startLabel = clock(it.startTs), endLabel = clock(it.endTs),
                    height = 120.dp, fill = true, brush = VivaStress.lineBrush(), spans = VivaHomeMapper.stressSpans(it, workouts),
                )
            }
        }
        if (trend.size > 1) {
            VivaCard(title = "Last 14 days") {
                VivaLineChart(
                    values = trend, color = VivaStyle.textPrimary, range = 0.0..3.0,
                    yLabels = listOf("3.0", "2.0", "1.0", "0.0"), height = 120.dp, fill = true, brush = VivaStress.lineBrush(),
                )
                Text("Each day's average, sleep included.", style = VivaFont.caption, color = VivaStyle.textSecondary)
            }
        }
        if (score == null && loaded) VivaCallout("No heart rate from the strap for this day yet.")
        now?.let {
            BandCard("Whole day", if (today) "The last 24 hours, sleep included." else "The whole day, sleep included.", it.points)
            BandCard("Awake", "Outside sleep.", it.points.filterNot(it::asleep))
            BandCard("Asleep", "During sleep, where lower is better.", it.points.filter(it::asleep))
        }
        VivaLabel("Sessions", Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(VivaStyle.cardGap)) {
            Session("Increase relaxation", listOf(VivaStyle.sleep, VivaStyle.canvasTop), Modifier.weight(1f), onBreathe)
            Session("Increase alertness", listOf(VivaStyle.recoveryRed.copy(alpha = 0.75f), VivaStyle.callout), Modifier.weight(1f), onBreathe)
        }
    }

    if (showInfo) {
        VivaInfoSheet(
            title = "What is Stress?",
            paragraphs = listOf(
                "Stress here is your body's physiological activation, read from your heart rate in the moment against your own typical resting heart rate. It is scored from 0 to 3, every ten minutes, asleep and awake.",
                "0.0 to 0.9 is low: calm, relaxed or sleepy.",
                "1.0 to 1.9 is medium: neutral, alert or mildly activated.",
                "2.0 to 3.0 is high: excited, stressed or highly activated.",
                "Stress is not good or bad in itself. You want it low when you are winding down, and some activation helps when you need to perform.",
            ),
            onDismiss = { showInfo = false },
        )
    }
}

@Composable
private fun Session(title: String, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).vivaPressable(onClick)) {
        Box(Modifier.fillMaxWidth().height(78.dp).background(Brush.verticalGradient(colors)))
        Column(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 86.dp).background(VivaStyle.card).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Air, contentDescription = null, Modifier.size(13.dp), tint = VivaStyle.textSecondary)
            VivaCardTitle(title)
            Text("Guided breathing", style = VivaFont.caption, color = VivaStyle.textSecondary)
        }
    }
}
