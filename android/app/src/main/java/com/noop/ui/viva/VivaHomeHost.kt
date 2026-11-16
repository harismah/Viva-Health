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
import androidx.compose.material.icons.outlined.Bedtime
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
import java.time.temporal.ChronoUnit
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
    /** The Recovery the ring shows: the day's own, or on an unscored today the last scored night's. */
    val recoveryPercent: Double? = metric?.recovery,
    /** The earlier day a carried Sleep or Recovery score belongs to; null when the score is the day's own. */
    val sleepCarriedDay: String? = null,
    val recoveryCarriedDay: String? = null,
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
    /** Recovery also takes the Sleep ring's score, so its Sleep Performance row equals that ring. */
    val onRecovery: (score: VivaHandedScore, sleep: VivaHandedScore) -> Unit,
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
            viewModel.repo.sleepSessionsMerged(viewModel.activeStrapId, dayStart - 86_400, dayStart + 86_400)
        }.getOrDefault(emptyList())
        sleep = VivaHomeMapper.lastSleep(sessions, input.selectedDay, zone)
    }

    var stressNow by remember { mutableStateOf<VivaStressNow?>(null) }
    LaunchedEffect(input.days, viewModel.activeStrapId) { stressNow = vivaStressNow(viewModel, input.days) }

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

    var extras by remember { mutableStateOf(VivaDashboardExtras()) }
    LaunchedEffect(input.days, viewModel.activeStrapId) {
        val maxHr = com.noop.ui.ProfileStore.from(context).hrMax.toDouble()
        extras = runCatching { loadVivaDashboard(viewModel, input.days, LocalDate.now(), zone, maxHr) }.getOrDefault(VivaDashboardExtras())
    }

    val mapped = VivaHomeMapper.map(input, sleep, journalDays, alarm, zone, Locale.getDefault(), is24Hour, extras = extras)
    // Today, the card states the same verdict as the Health tab it opens, from the same items.
    val vitalsToday = remember(input.days) { VivaVitals.summary(VivaVitals.items(input.days, context)) }
    val withVitals = if (input.dayOffset == 0 && vitalsToday != null) mapped.copy(healthStatus = vitalsToday) else mapped
    // Today, stress is the reading right now, the same one the Stress Monitor screen's dial shows.
    val data = stressNow?.takeIf { input.dayOffset == 0 }?.let {
        withVitals.copy(
            stress = it.value, stressLabel = VivaHomeMapper.stressLabel(it.value), stressTrace = it.levels, stressSpans = VivaHomeMapper.stressSpans(it, input.workouts),
            stressStart = VivaHomeMapper.clock(it.startTs, zone, Locale.getDefault(), is24Hour),
            stressUpdated = VivaHomeMapper.clock(it.endTs, zone, Locale.getDefault(), is24Hour),
            stressTicks = VivaHomeMapper.stressTicks(it.startTs, it.endTs, zone, Locale.getDefault(), is24Hour),
        )
    } ?: withVitals
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
                is VivaHomeRoute.Recovery -> navigation.onRecovery(
                    VivaHandedScore(route.score, data.recoveryCarriedDay),
                    VivaHandedScore(data.sleepPercent, data.sleepCarriedDay),
                )
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
            VivaHomeContent(
                data, actions, avatar = avatar, battery = battery,
                // Today only: the upstream detector's suggestion, drawn in this home's look. It shows
                // nothing unless "auto-detect workouts" is switched on in Settings.
                suggestion = {
                    if (input.dayOffset == 0) {
                        com.noop.ui.AutoWorkoutNudgeCard(viewModel, input.days) { prompt, onSave, onDismiss ->
                            VivaWorkoutSuggestion(prompt, onSave, onDismiss)
                        }
                    }
                },
            )
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
        extras: VivaDashboardExtras = VivaDashboardExtras(),
    ): VivaHomeData {
        val dashboard = VivaDashboard.rows(input, extras, locale)
        val metric = input.metric
        val vitals = listOf(metric?.respRateBpm, metric?.spo2Pct, metric?.restingHr?.toDouble(), metric?.avgHrv, metric?.skinTempDevC)
        val vitalsRead = vitals.count { it != null }
        return VivaHomeData(
            dayTitle = dayTitle(input.dayOffset, input.selectedDay, locale),
            canGoBack = true,
            canGoForward = input.dayOffset > 0,
            sleepPercent = input.sleepPercent,
            recoveryPercent = input.recoveryPercent,
            sleepCarriedDay = input.sleepCarriedDay,
            recoveryCarriedDay = input.recoveryCarriedDay,
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
            metrics = dashboard.first,
            moreMetrics = dashboard.second,
            week = week(input, locale),
            journal = journal(journalDays, today, locale),
        )
    }

    fun dayTitle(offset: Int, day: LocalDate, locale: Locale): String = when (offset) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    }

    /**
     * Whether today's strain misses the start of the day: no heart rate at all, or none until more than
     * two hours in (a strap paired or reconnected part-way through). Such a figure is not the day's strain.
     */
    fun strainIsPartial(dayStart: Long, firstSample: Long?): Boolean =
        firstSample == null || firstSample - dayStart > 2 * 3600

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

    /**
     * What a stress trace marks along its width: each sleep under a crescent and each logged activity
     * inside the window under the activity mark. An activity outside the window is left out.
     */
    internal fun stressSpans(stress: VivaStressNow, workouts: List<WorkoutRow>): List<VivaChartSpan> {
        val last = stress.points.lastIndex
        if (last < 1) return emptyList()
        val asleep = stress.sleepShading.map { VivaChartSpan(it.start, it.endInclusive, VivaStyle.sleep, Icons.Outlined.Bedtime) }
        val active = workouts.mapNotNull { workout ->
            // The trace's steps are evenly spaced, so an activity sits on the steps whose ten minutes it touches.
            val first = stress.points.indexOfFirst { it.ts + VivaStress.BUCKET_SECONDS > workout.startTs }
            val end = stress.points.indexOfLast { it.ts < workout.endTs }
            if (first < 0 || end < first) return@mapNotNull null
            // One step is a point, not a width: widen it by half a step each way so its band shows.
            val pad = if (end == first) 0.5f else 0f
            VivaChartSpan(
                ((first - pad) / last).coerceAtLeast(0f), ((end + pad) / last).coerceAtMost(1f),
                VivaStyle.strain, Icons.AutoMirrored.Outlined.DirectionsRun,
            )
        }
        return asleep + active
    }

    /**
     * Two whole-hour marks about a third and two thirds of the way through a stress window, each as
     * (share of the window, clock text); none for a window under three hours.
     */
    fun stressTicks(startTs: Long, endTs: Long, zone: ZoneId, locale: Locale, is24Hour: Boolean): List<Pair<Float, String>> {
        val span = endTs - startTs
        if (span < 3 * 3600) return emptyList()
        return listOf(1, 2).map { third ->
            val hour = Instant.ofEpochSecond(startTs + span * third / 3 + 1800).atZone(zone).truncatedTo(ChronoUnit.HOURS).toEpochSecond()
            (hour - startTs).toFloat() / span to clock(hour, zone, locale, is24Hour)
        }
    }

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
    fun trend(value: Double?, baseline: Double?): VivaTrend {
        if (value == null || baseline == null || baseline == 0.0) return VivaTrend.FLAT
        val change = (value - baseline) / kotlin.math.abs(baseline)
        return when {
            change > 0.05 -> VivaTrend.UP
            change < -0.05 -> VivaTrend.DOWN
            else -> VivaTrend.FLAT
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
