package com.noop.ui.viva

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.noop.alarm.SmartAlarmStore
import com.noop.alarm.WindDownStore
import com.noop.data.DailyMetric
import com.noop.data.SleepSession
import com.noop.data.WorkoutRow
import com.noop.ui.AppViewModel
import com.noop.ui.ClockPrefs
import com.noop.ui.EffortScale
import com.noop.ui.JOURNAL_DEVICE_ID
import com.noop.ui.KeyMetric
import com.noop.ui.UnitFormatter
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * What the Today screen hands the Viva home: values it has already loaded for the selected day.
 * The Today screen keeps every loader and every sheet; this is only its state, renamed.
 */
class VivaHomeInput(
    val dayOffset: Int,
    val selectedDay: LocalDate,
    val selectedDayKey: String,
    val metric: DailyMetric?,
    val days: List<DailyMetric>,
    val sleepPercent: Double?,
    /** Effort on the stored 0-100 scale; the home shows it on 0-21. */
    val effort: Double?,
    val stress: Double?,
    val calibrationNights: Int?,
    val workouts: List<WorkoutRow>,
    val enabledKeyMetrics: List<KeyMetric>,
    val coachEnabled: Boolean,
)

/** Where the Viva home sends the wearer; each is bound to a screen the app already has. */
class VivaHomeNavigation(
    val onDayOffset: (Int) -> Unit,
    val onPickDay: () -> Unit,
    val onAdd: () -> Unit,
    val onCoach: () -> Unit,
    val onCustomize: () -> Unit,
    /** The three rings hand over the score they showed, so the detail screen opens on the same figure. */
    val onSleep: (Double?) -> Unit,
    val onRecovery: (Double?) -> Unit,
    val onStrain: (Double?) -> Unit,
    val onHealth: () -> Unit,
    val onStress: () -> Unit,
    val onMetric: (String) -> Unit,
    val onSleepPlanner: () -> Unit,
    val onJournal: () -> Unit,
    val onBehaviorInsights: () -> Unit,
    val onWorkouts: () -> Unit,
    val onAllMetrics: () -> Unit,
)

/**
 * The one resolver for the alarm facts, shared by Home's Tonight card and the Sleep Planner, so
 * neither can state a wake time or an armed state the scheduler does not hold: an alarm counts as
 * armed only while its stored deadline is ahead.
 */
internal fun vivaAlarmReadout(context: android.content.Context, zone: ZoneId, is24Hour: Boolean): VivaAlarmReadout {
    val store = SmartAlarmStore.from(context)
    val deadline = store.scheduledDeadlineMs
    val armed = store.enabled && deadline > System.currentTimeMillis()
    return VivaAlarmReadout.resolve(
        nextFireEpochMs = if (armed) deadline else null,
        // The stored target is the EARLIEST wake; the alarm that is guaranteed to ring comes a window
        // later, and that is the time an armed deadline carries. Same rule off, or the time jumps on toggle.
        baseWakeMinutes = store.targetMinutes + store.windowMinutes,
        sleepNeedMinutes = WindDownStore.from(context).sleepNeedMinutes,
        zone = zone,
        is24Hour = is24Hour,
    )
}

/**
 * The Viva home on the canvas, fed from the Today screen's state. It adds only what that screen
 * does not already hold: last night's sleep session, the journal week and the alarm readout.
 */
@Composable
fun VivaHomeHost(
    viewModel: AppViewModel,
    input: VivaHomeInput,
    navigation: VivaHomeNavigation,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit = {},
    battery: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val is24Hour = ClockPrefs.uses24Hour(context)

    var sleep by remember { mutableStateOf<SleepSession?>(null) }
    LaunchedEffect(input.selectedDayKey, input.days) {
        val dayStart = input.selectedDay.atStartOfDay(zone).toEpochSecond()
        val sessions = runCatching {
            viewModel.repo.sleepSessionsUnion(viewModel.activeStrapId, dayStart - 86_400, dayStart + 86_400)
        }.getOrDefault(emptyList())
        sleep = VivaHomeMapper.lastSleep(sessions, input.selectedDay, zone)
    }

    var journalDays by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(input.days) {
        val from = LocalDate.now().minusDays(6).toString()
        val to = LocalDate.now().toString()
        journalDays = runCatching {
            (viewModel.repo.journal("my-whoop", from, to) + viewModel.repo.journal(JOURNAL_DEVICE_ID, from, to))
                .map { it.day }.toSet()
        }.getOrDefault(emptySet())
    }

    val alarm = remember(input.selectedDayKey, is24Hour) { vivaAlarmReadout(context, zone, is24Hour) }

    val mapped = VivaHomeMapper.map(input, sleep, journalDays, alarm, zone, Locale.getDefault(), is24Hour)
    // Today, the card states the same verdict as the Health tab it opens, from the same items.
    val vitalsToday = remember(input.days) { VivaVitals.summary(VivaVitals.items(input.days, context)) }
    val data = if (input.dayOffset == 0 && vitalsToday != null) mapped.copy(healthStatus = vitalsToday) else mapped
    val actions = VivaHomeActions(
        onPreviousDay = { navigation.onDayOffset(input.dayOffset + 1) },
        onNextDay = { navigation.onDayOffset((input.dayOffset - 1).coerceAtLeast(0)) },
        onPickDay = navigation.onPickDay,
        onAdd = navigation.onAdd,
        onStartActivity = navigation.onAdd,
        onCoach = navigation.onCoach,
        onCustomize = navigation.onCustomize,
        onOpen = { route ->
            when (route) {
                VivaHomeRoute.Sleep -> navigation.onSleep(data.sleepPercent)
                is VivaHomeRoute.Recovery -> navigation.onRecovery(route.score)
                is VivaHomeRoute.Strain -> navigation.onStrain(route.score)
                VivaHomeRoute.HealthMonitor -> navigation.onHealth()
                VivaHomeRoute.StressMonitor -> navigation.onStress()
                VivaHomeRoute.SleepPlanner -> navigation.onSleepPlanner()
                VivaHomeRoute.BehaviorInsights -> navigation.onBehaviorInsights()
                VivaHomeRoute.Journal -> navigation.onJournal()
                VivaHomeRoute.AllMetrics -> navigation.onAllMetrics()
                is VivaHomeRoute.Metric -> navigation.onMetric(route.id)
                is VivaHomeRoute.Activity -> if (route.id == VivaHomeMapper.SLEEP_ACTIVITY_ID) navigation.onSleep(data.sleepPercent) else navigation.onWorkouts()
            }
        },
    )

    val scroll = rememberScrollState()
    val pinThreshold = with(LocalDensity.current) { 190.dp.toPx() }
    val pinned by remember { derivedStateOf { scroll.value > pinThreshold } }
    VivaCanvas(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = VivaStyle.pagePadding)
                .padding(top = 8.dp, bottom = VivaTabBar.height + 40.dp)
                .navigationBarsPadding(),
        ) {
            VivaHomeContent(data, actions, avatar = avatar, battery = battery)
        }
        if (pinned) {
            Box(Modifier.align(Alignment.TopCenter)) {
                VivaHomePinnedRings(data, actions.onOpen)
            }
        }
    }
}

/**
 * Today-screen state to home values. Pure, so the mapping is tested without a device; twin of
 * `LiquidTodayView.vivaHomeData` on iPhone.
 */
object VivaHomeMapper {
    const val SLEEP_ACTIVITY_ID = "sleep"

    fun map(
        input: VivaHomeInput,
        sleep: SleepSession?,
        journalDays: Set<String>,
        alarm: VivaAlarmReadout,
        zone: ZoneId,
        locale: Locale,
        is24Hour: Boolean,
        today: LocalDate = LocalDate.now(),
    ): VivaHomeData {
        val metric = input.metric
        val vitals = listOf(metric?.respRateBpm, metric?.spo2Pct, metric?.restingHr?.toDouble(), metric?.avgHrv, metric?.skinTempDevC)
        val vitalsRead = vitals.count { it != null }
        return VivaHomeData(
            dayTitle = dayTitle(input.dayOffset, input.selectedDay, locale),
            canGoBack = true,
            canGoForward = input.dayOffset > 0,
            sleepPercent = input.sleepPercent,
            recoveryPercent = metric?.recovery,
            strain = input.effort?.let { UnitFormatter.effortValue(it, EffortScale.WHOOP) },
            sleepDuration = sleep?.let { duration(it.endTs - sleepStart(it)) },
            // A count of readings, not a verdict: this card cannot yet band them against a range.
            healthStatus = if (vitalsRead == 0) "No readings yet" else "$vitalsRead of ${vitals.size} vitals in",
            stress = input.stress,
            stressLabel = stressLabel(input.stress),
            activities = activities(input, sleep, zone, locale, is24Hour),
            alarm = alarm,
            isToday = input.dayOffset == 0,
            coachEnabled = input.coachEnabled,
            calibrationNights = input.calibrationNights,
            metrics = metrics(input, locale),
            week = week(input, locale),
            journal = journal(journalDays, today, locale),
        )
    }

    fun dayTitle(offset: Int, day: LocalDate, locale: Locale): String = when (offset) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    }

    fun stressLabel(stress: Double?): String = when {
        stress == null -> "Calibrating"
        stress < 1 -> "Low"
        stress < 2 -> "Medium"
        else -> "High"
    }

    /** Hours and minutes as `7:32`, the way the home prints every duration. */
    fun duration(seconds: Long): String {
        val minutes = maxOf(seconds, 0) / 60
        return String.format(Locale.US, "%d:%02d", minutes / 60, minutes % 60)
    }

    private fun sleepStart(session: SleepSession): Long = session.startTsAdjusted ?: session.startTs

    /** The newest sleep that ended on the selected local day. */
    fun lastSleep(sessions: List<SleepSession>, day: LocalDate, zone: ZoneId): SleepSession? =
        sessions.filter { Instant.ofEpochSecond(it.endTs).atZone(zone).toLocalDate() == day }.maxByOrNull { it.endTs }

    /** A moment as the wearer's clock time (`10:42 PM` or `22:42`). */
    fun clock(epochSeconds: Long, zone: ZoneId, locale: Locale, is24Hour: Boolean): String {
        val time = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        return VivaAlarmReadout.clockText(time.hour * 60 + time.minute, locale, is24Hour)
    }

    private fun activities(input: VivaHomeInput, sleep: SleepSession?, zone: ZoneId, locale: Locale, is24Hour: Boolean): List<VivaHomeData.Activity> {
        val out = mutableListOf<VivaHomeData.Activity>()
        if (sleep != null) {
            val start = sleepStart(sleep)
            out += VivaHomeData.Activity(
                id = SLEEP_ACTIVITY_ID, icon = Icons.Outlined.NightsStay, title = "Sleep", value = duration(sleep.endTs - start),
                start = clock(start, zone, locale, is24Hour), end = clock(sleep.endTs, zone, locale, is24Hour), color = VivaStyle.sleep,
            )
        }
        input.workouts
            .filter { Instant.ofEpochSecond(it.startTs).atZone(zone).toLocalDate() == input.selectedDay }
            .sortedBy { it.startTs }
            .forEach { workout ->
                out += VivaHomeData.Activity(
                    id = "workout-${workout.startTs}",
                    icon = Icons.AutoMirrored.Outlined.DirectionsRun,
                    title = workout.sport,
                    value = workout.strain?.let { UnitFormatter.effortDisplay(it, EffortScale.WHOOP) } ?: duration(workout.endTs - workout.startTs),
                    start = clock(workout.startTs, zone, locale, is24Hour),
                    end = clock(workout.endTs, zone, locale, is24Hour),
                    color = VivaStyle.strain,
                )
            }
        return out
    }

    /** Mean of a field over the 30 recorded days before the selected day, or null when none carry it. */
    private fun baseline(input: VivaHomeInput, value: (DailyMetric) -> Double?): Double? {
        val prior = input.days.filter { it.day < input.selectedDayKey }.takeLast(30).mapNotNull(value)
        return if (prior.isEmpty()) null else prior.sum() / prior.size
    }

    fun trend(value: Double?, baseline: Double?): VivaTrend {
        if (value == null || baseline == null || baseline == 0.0) return VivaTrend.FLAT
        val change = (value - baseline) / kotlin.math.abs(baseline)
        return when {
            change > 0.05 -> VivaTrend.UP
            change < -0.05 -> VivaTrend.DOWN
            else -> VivaTrend.FLAT
        }
    }

    private fun metrics(input: VivaHomeInput, locale: Locale): List<VivaHomeData.Metric> {
        val day = input.metric
        fun whole(v: Double?) = v?.let { VivaScoreRing.roundHalfAwayFromZero(it).toString() } ?: "--"
        fun grouped(v: Double?) = v?.let { NumberFormat.getIntegerInstance(locale).format(VivaScoreRing.roundHalfAwayFromZero(it)) } ?: "--"
        fun one(v: Double?) = v?.let { String.format(locale, "%.1f", it) } ?: "--"
        fun row(
            icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, key: String,
            value: Double?, field: (DailyMetric) -> Double?, format: (Double?) -> String, unit: String? = null,
        ): VivaHomeData.Metric {
            val base = baseline(input, field)
            return VivaHomeData.Metric(
                id = key, icon = icon, label = label, value = format(value), unit = unit.takeIf { value != null },
                baseline = base?.let { format(it) }, trend = trend(value, base), opens = true,
            )
        }
        return input.enabledKeyMetrics.mapNotNull { metric ->
            when (metric) {
                // The three rings above already carry these.
                KeyMetric.CHARGE, KeyMetric.EFFORT, KeyMetric.REST -> null
                KeyMetric.HRV -> row(Icons.Outlined.MonitorHeart, "Heart Rate Variability", "hrv", day?.avgHrv, { it.avgHrv }, ::whole)
                KeyMetric.RESTING_HR -> row(Icons.Outlined.FavoriteBorder, "Resting Heart Rate", "rhr", day?.restingHr?.toDouble(), { it.restingHr?.toDouble() }, ::whole)
                KeyMetric.BLOOD_OXYGEN -> row(Icons.Outlined.WaterDrop, "Blood Oxygen", "spo2", day?.spo2Pct, { it.spo2Pct }, ::whole, unit = "%")
                KeyMetric.RESPIRATORY -> row(Icons.Outlined.Air, "Respiratory Rate", "resp", day?.respRateBpm, { it.respRateBpm }, ::one)
                KeyMetric.STEPS -> row(Icons.AutoMirrored.Outlined.DirectionsWalk, "Steps", "steps_est", day?.steps?.toDouble(), { it.steps?.toDouble() }, ::grouped)
                KeyMetric.CALORIES -> row(Icons.Outlined.LocalFireDepartment, "Calories", "active_kcal", day?.activeKcalEst, { it.activeKcalEst }, ::grouped)
                KeyMetric.SKIN_TEMP -> row(Icons.Outlined.Thermostat, "Skin Temperature", "skin", day?.skinTempDevC, { it.skinTempDevC }, ::one)
                // Weight is not a daily strap reading and has no row on the iPhone home either.
                KeyMetric.WEIGHT -> null
            }
        }
    }

    /** The seven days ending on the selected day, oldest first. */
    private fun week(input: VivaHomeInput, locale: Locale): List<VivaHomeData.WeekDay> {
        val byDay = input.days.associateBy { it.day }
        return (6 downTo 0).map { back ->
            val date = input.selectedDay.minusDays(back.toLong())
            val key = if (back == 0) input.selectedDayKey else date.toString()
            val day = if (back == 0) input.metric ?: byDay[key] else byDay[key]
            VivaHomeData.WeekDay(
                id = key,
                label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                date = date.dayOfMonth,
                strain = (if (back == 0) input.effort else day?.strain)?.let { UnitFormatter.effortValue(it, EffortScale.WHOOP) },
                recovery = day?.recovery,
                isToday = back == 0,
            )
        }
    }

    private fun journal(logged: Set<String>, today: LocalDate, locale: Locale): List<VivaHomeData.JournalDay> =
        (6 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            VivaHomeData.JournalDay(
                key = date.toString(),
                label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                logged = date.toString() in logged,
                isToday = back == 0,
            )
        }
}
