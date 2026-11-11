package com.noop.ui.viva

import com.noop.data.DailyMetric
import com.noop.data.SleepSession
import com.noop.data.WorkoutRow
import com.noop.ui.KeyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * The Today-state to home-values mapping, checked without a device. The rules mirror the iPhone
 * `LiquidTodayView.vivaHomeData`: strain on 0-21, the rings never repeated as dashboard rows, a
 * 30-day baseline with a 5% trend band, and the newest sleep that ended on the selected day.
 */
class VivaHomeMapperTest {
    private val utc = ZoneOffset.UTC
    private val locale = Locale.US
    private val day = LocalDate.of(2026, 11, 10)

    private fun epoch(d: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(d, java.time.LocalTime.of(hour, minute)).toEpochSecond(utc)

    private fun metric(key: String, hrv: Double? = null, recovery: Double? = null, strain: Double? = null) =
        DailyMetric(deviceId = "strap", day = key, avgHrv = hrv, recovery = recovery, strain = strain)

    private fun input(
        offset: Int = 0,
        metric: DailyMetric? = null,
        days: List<DailyMetric> = emptyList(),
        effort: Double? = null,
        stress: Double? = null,
        workouts: List<WorkoutRow> = emptyList(),
        enabled: List<KeyMetric> = emptyList(),
    ) = VivaHomeInput(
        dayOffset = offset, selectedDay = day.minusDays(offset.toLong()), selectedDayKey = day.minusDays(offset.toLong()).toString(),
        metric = metric, days = days, sleepPercent = null, effort = effort, stress = stress, calibrationNights = null,
        workouts = workouts, enabledKeyMetrics = enabled, coachEnabled = true,
    )

    private val alarm = VivaAlarmReadout.resolve(null, baseWakeMinutes = 7 * 60, sleepNeedMinutes = 8 * 60, zone = utc, locale = locale)

    private fun map(input: VivaHomeInput, sleep: SleepSession? = null, journal: Set<String> = emptySet()) =
        VivaHomeMapper.map(input, sleep, journal, alarm, utc, locale, is24Hour = false, today = day)

    @Test
    fun dayTitleNamesTodayAndYesterdayThenDates() {
        assertEquals("Today", VivaHomeMapper.dayTitle(0, day, locale))
        assertEquals("Yesterday", VivaHomeMapper.dayTitle(1, day.minusDays(1), locale))
        assertEquals("Sun, Nov 8", VivaHomeMapper.dayTitle(2, day.minusDays(2), locale))
    }

    @Test
    fun stressLabelBands() {
        assertEquals("Calibrating", VivaHomeMapper.stressLabel(null))
        assertEquals("Low", VivaHomeMapper.stressLabel(0.9))
        assertEquals("Medium", VivaHomeMapper.stressLabel(1.0))
        assertEquals("Medium", VivaHomeMapper.stressLabel(1.9))
        assertEquals("High", VivaHomeMapper.stressLabel(2.0))
    }

    @Test
    fun durationPrintsHoursAndMinutes() {
        assertEquals("7:32", VivaHomeMapper.duration(7 * 3600 + 32 * 60 + 59))
        assertEquals("0:05", VivaHomeMapper.duration(300))
        assertEquals("0:00", VivaHomeMapper.duration(-40))
    }

    @Test
    fun trendNeedsMoreThanFivePercent() {
        assertEquals(VivaTrend.UP, VivaHomeMapper.trend(106.0, 100.0))
        assertEquals(VivaTrend.FLAT, VivaHomeMapper.trend(105.0, 100.0))
        assertEquals(VivaTrend.DOWN, VivaHomeMapper.trend(94.0, 100.0))
        assertEquals(VivaTrend.FLAT, VivaHomeMapper.trend(null, 100.0))
        assertEquals(VivaTrend.FLAT, VivaHomeMapper.trend(50.0, 0.0))
    }

    @Test
    fun lastSleepIsTheNewestEndingOnTheSelectedDay() {
        val night = SleepSession("strap", epoch(day.minusDays(1), 23), epoch(day, 6, 38))
        val nap = SleepSession("strap", epoch(day, 13), epoch(day, 13, 40))
        val older = SleepSession("strap", epoch(day.minusDays(2), 23), epoch(day.minusDays(1), 7))
        assertEquals(nap, VivaHomeMapper.lastSleep(listOf(night, nap, older), day, utc))
        assertEquals(older, VivaHomeMapper.lastSleep(listOf(night, nap, older), day.minusDays(1), utc))
        assertNull(VivaHomeMapper.lastSleep(listOf(older), day, utc))
    }

    @Test
    fun strainIsShownOnTheTwentyOneScale() {
        val data = map(input(effort = 50.0))
        assertEquals(10.5, data.strain!!, 1e-9)
        assertNull(map(input()).strain)
    }

    @Test
    fun healthStatusCountsReadingsAndMakesNoRangeClaim() {
        assertEquals("No readings yet", map(input()).healthStatus)
        val some = DailyMetric(deviceId = "strap", day = day.toString(), avgHrv = 60.0, restingHr = 52)
        assertEquals("2 of 5 vitals in", map(input(metric = some)).healthStatus)
    }

    @Test
    fun dashboardSkipsTheRingsAndKeepsTheWearersOrder() {
        val enabled = listOf(KeyMetric.CHARGE, KeyMetric.RESTING_HR, KeyMetric.EFFORT, KeyMetric.HRV, KeyMetric.REST, KeyMetric.WEIGHT)
        val today = DailyMetric(deviceId = "strap", day = day.toString(), avgHrv = 59.4, restingHr = 54)
        val rows = map(input(metric = today, enabled = enabled)).metrics
        assertEquals(listOf("rhr", "hrv"), rows.map { it.id })
        assertEquals("54", rows[0].value)
        assertEquals("59", rows[1].value)
    }

    @Test
    fun baselineIsThePriorThirtyDaysAndDrivesTheTrend() {
        val history = (1..40).map { back -> metric(day.minusDays(back.toLong()).toString(), hrv = if (back <= 30) 50.0 else 500.0) }
            .sortedBy { it.day }
        val today = metric(day.toString(), hrv = 60.0)
        val row = map(input(metric = today, days = history + today, enabled = listOf(KeyMetric.HRV))).metrics.single()
        assertEquals("50", row.baseline)
        assertEquals(VivaTrend.UP, row.trend)
    }

    @Test
    fun missingReadingShowsDashesWithNoUnitOrBaseline() {
        val row = map(input(enabled = listOf(KeyMetric.BLOOD_OXYGEN))).metrics.single()
        assertEquals("--", row.value)
        assertNull(row.unit)
        assertNull(row.baseline)
    }

    @Test
    fun weekEndsOnTheSelectedDayAndUsesTodaysLiveEffort() {
        val history = listOf(metric(day.minusDays(1).toString(), recovery = 80.0, strain = 100.0))
        val today = metric(day.toString(), recovery = 49.0, strain = 10.0)
        val week = map(input(metric = today, days = history, effort = 50.0)).week
        assertEquals(7, week.size)
        assertTrue(week.last().isToday)
        assertEquals(10, week.last().date)
        assertEquals(10.5, week.last().strain!!, 1e-9)
        assertEquals(49.0, week.last().recovery!!, 0.0)
        assertEquals(21.0, week[5].strain!!, 1e-9)
        assertNull(week[0].strain)
        assertFalse(week[5].isToday)
    }

    @Test
    fun activitiesListSleepFirstThenThatDaysWorkouts() {
        val night = SleepSession("strap", epoch(day.minusDays(1), 23, 5), epoch(day, 6, 38))
        val lift = WorkoutRow(deviceId = "strap", startTs = epoch(day, 20, 22), endTs = epoch(day, 21, 5), sport = "Weightlifting", source = "noop")
        val yesterday = WorkoutRow(deviceId = "strap", startTs = epoch(day.minusDays(1), 9), endTs = epoch(day.minusDays(1), 10), sport = "Run", source = "noop")
        val data = map(input(workouts = listOf(yesterday, lift)), sleep = night)
        assertEquals(listOf("Sleep", "Weightlifting"), data.activities.map { it.title })
        assertEquals("7:33", data.activities[0].value)
        assertEquals("11:05 PM", data.activities[0].start)
        assertEquals("6:38 AM", data.activities[0].end)
        assertEquals("0:43", data.activities[1].value)
        assertEquals("7:33", data.sleepDuration)
    }

    @Test
    fun journalWeekEndsTodayAndMarksLoggedDays() {
        val journal = map(input(), journal = setOf(day.minusDays(2).toString())).journal
        assertEquals(7, journal.size)
        assertTrue(journal.last().isToday)
        assertEquals(listOf(false, false, false, false, true, false, false), journal.map { it.logged })
        assertEquals("Tue", journal.last().label)
    }

    @Test
    fun strainIsPartialUntilHeartRateReachesBackToTheStartOfTheDay() {
        val start = epoch(day, 0)
        assertTrue(VivaHomeMapper.strainIsPartial(start, null))
        assertTrue(VivaHomeMapper.strainIsPartial(start, epoch(day, 16, 50)))
        assertFalse(VivaHomeMapper.strainIsPartial(start, epoch(day, 0, 1)))
        assertFalse(VivaHomeMapper.strainIsPartial(start, epoch(day, 2)))
    }
}
