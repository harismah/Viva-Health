package com.noop.ui.viva

import com.noop.data.DailyMetric
import com.noop.ui.KeyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * The rules that stop two Viva screens contradicting each other. The first two tests carry the same
 * cases as `VivaConsistencyTests.swift`; the rest pin the Android callers to one resolver.
 */
class VivaConsistencyTest {
    private val locale = Locale.US
    private val today = LocalDate.of(2026, 11, 10)
    private val todayKey = today.toString()

    private fun day(back: Int, hrv: Double? = null, steps: Int? = null, recovery: Double? = null, sleepMin: Double? = null) =
        DailyMetric(
            deviceId = "strap", day = today.minusDays(back.toLong()).toString(),
            avgHrv = hrv, steps = steps, recovery = recovery, totalSleepMin = sleepMin,
        )

    @Test
    fun recoveryGapPrefersCalibrationThenTheMissingInput() {
        assertEquals(VivaConsistency.RecoveryGap.Calibrating(2), VivaConsistency.recoveryGap(2, hasSleepToday = true, hasHrvToday = false))
        assertEquals(VivaConsistency.RecoveryGap.NoSleep, VivaConsistency.recoveryGap(null, hasSleepToday = false, hasHrvToday = false))
        assertEquals(VivaConsistency.RecoveryGap.NoHrv, VivaConsistency.recoveryGap(null, hasSleepToday = true, hasHrvToday = false))
        assertEquals(VivaConsistency.RecoveryGap.NotScoredYet, VivaConsistency.recoveryGap(null, hasSleepToday = true, hasHrvToday = true))
    }

    @Test
    fun carriedValueIsStampedOnlyWhenNotTodays() {
        assertNull(VivaConsistency.carriedDay(66.0, "2026-11-09"))
        assertEquals("2026-11-09", VivaConsistency.carriedDay(null, "2026-11-09"))
        assertNull(VivaConsistency.carriedDay(null, null))
    }

    @Test
    fun dayLabelIsDayThenMonthAndNeverEmpty() {
        assertEquals("9 Nov", VivaConsistency.dayLabel("2026-11-09", locale))
        assertEquals("not a day", VivaConsistency.dayLabel("not a day", locale))
    }

    @Test
    fun todaysOwnValueWinsAndIsNotStamped() {
        val row = day(0, hrv = 66.0)
        assertEquals(VivaTrendMetric.Reading(66.0, null), VivaTrendMetric.HRV.todayOrCarried(listOf(day(1, hrv = 50.0), row), row, todayKey))
    }

    @Test
    fun aVitalIsCarriedFromInsideTheWindowWithItsDay() {
        val days = listOf(day(3, hrv = 48.0), day(1, hrv = 52.0), day(0))
        assertEquals(
            VivaTrendMetric.Reading(52.0, today.minusDays(1).toString()),
            VivaTrendMetric.HRV.todayOrCarried(days, days.last(), todayKey),
        )
        // With no row for today at all, the same night is found.
        assertEquals(52.0, VivaTrendMetric.HRV.todayOrCarried(days.dropLast(1), null, todayKey).value!!, 1e-9)
    }

    @Test
    fun aStaleVitalAndATotalAreNeverCarried() {
        assertEquals(VivaTrendMetric.Reading(null, null), VivaTrendMetric.HRV.todayOrCarried(listOf(day(8, hrv = 48.0)), null, todayKey))
        assertEquals(VivaTrendMetric.Reading(null, null), VivaTrendMetric.STEPS.todayOrCarried(listOf(day(1, steps = 9000)), null, todayKey))
        assertEquals(VivaTrendMetric.Reading(null, null), VivaTrendMetric.RECOVERY.todayOrCarried(listOf(day(1, recovery = 70.0)), null, todayKey))
    }

    @Test
    fun detailRowShowsTheCarriedDayOnlyOnToday() {
        val days = listOf(day(1, hrv = 52.0), day(0))
        val carried = VivaPillarDetail.rows(listOf(VivaTrendMetric.HRV), days.last(), days, todayKey, carry = true).single()
        assertEquals(52.0, carried.value!!, 1e-9)
        assertEquals(today.minusDays(1).toString(), carried.carriedDay)
        // A past day's row is that day's own or nothing.
        val past = VivaPillarDetail.rows(listOf(VivaTrendMetric.HRV), days.last(), days, todayKey).single()
        assertNull(past.value)
        assertNull(past.carriedDay)
    }

    @Test
    fun detailRowTakesAHandedValueOverItsOwnLookup() {
        val row = VivaPillarDetail.rows(
            listOf(VivaTrendMetric.SLEEP_PERFORMANCE), day(0), listOf(day(0)), todayKey,
            sleepPerformance = mapOf(todayKey to 40.0), carry = true,
            handed = mapOf(VivaTrendMetric.SLEEP_PERFORMANCE to VivaHandedScore(81.0, "2026-11-09")),
        ).single()
        assertEquals(81.0, row.value!!, 1e-9)
        assertEquals("2026-11-09", row.carriedDay)
    }

    @Test
    fun recoveryEmptyNoteNamesTheRealGap() {
        assertNull(VivaPillarDetail.recoveryEmptyNote(day(0, recovery = 61.0), isToday = true, calibrationNights = null))
        assertEquals("Learning your baseline, 2 of 4 nights.", VivaPillarDetail.recoveryEmptyNote(day(0), isToday = true, calibrationNights = 2))
        assertEquals(
            "No sleep has been recorded for last night yet, so today has no Recovery score.",
            VivaPillarDetail.recoveryEmptyNote(null, isToday = true, calibrationNights = null),
        )
        assertEquals(
            "Last night recorded no heart rate variability, so Recovery could not be scored. Check the strap was worn overnight and has synced.",
            VivaPillarDetail.recoveryEmptyNote(day(0, sleepMin = 420.0), isToday = true, calibrationNights = null),
        )
        assertEquals(
            "Today's Recovery has not been scored yet.",
            VivaPillarDetail.recoveryEmptyNote(day(0, hrv = 50.0, sleepMin = 420.0), isToday = true, calibrationNights = null),
        )
        assertEquals("No Recovery was scored for this day.", VivaPillarDetail.recoveryEmptyNote(day(2), isToday = false, calibrationNights = 2))
    }

    @Test
    fun recoveryRouteCarriesTheNightAndHomesSleepScore() {
        assertEquals(
            "viva_recovery?day=2026-11-10&score=65.0&from=2026-11-09&sleep=81.0&sleepFrom=2026-11-09",
            VivaShell.recoveryRoute(todayKey, 65.0, "2026-11-09", VivaHandedScore(81.0, "2026-11-09")),
        )
        assertEquals("viva_recovery?day=2026-11-10&score=65.0&sleep=none", VivaShell.recoveryRoute(todayKey, 65.0, null, VivaHandedScore(null)))
        assertEquals(VivaHandedScore(65.0, "2026-11-09"), VivaShell.handedScore("65.0", "2026-11-09"))
    }

    @Test
    fun homeRowShowsTheCarriedVitalStampedWithItsDay() {
        val days = listOf(day(2, hrv = 48.0), day(1, hrv = 52.0), day(0))
        fun row(offset: Int) = VivaHomeMapper.map(
            VivaHomeInput(
                dayOffset = offset, selectedDay = today.minusDays(offset.toLong()), selectedDayKey = today.minusDays(offset.toLong()).toString(),
                metric = if (offset == 0) days.last() else null, days = days, sleepPercent = null, effort = null, stress = null,
                calibrationNights = null, workouts = emptyList(), enabledKeyMetrics = listOf(KeyMetric.HRV), coachEnabled = true,
            ),
            sleep = null, journalDays = emptySet(),
            alarm = VivaAlarmReadout.resolve(null, baseWakeMinutes = 420, sleepNeedMinutes = 480, zone = ZoneOffset.UTC, locale = locale),
            zone = ZoneOffset.UTC, locale = locale, is24Hour = false, today = today,
        ).metrics.first { it.id == "hrv" }

        val carried = row(0)
        assertEquals("52", carried.value)
        assertEquals("9 Nov", carried.baseline)
        assertEquals(VivaTrend.FLAT, carried.trend)
        // A past day with no reading stays empty: the carry is today's only.
        assertEquals("--", row(3).value)
    }
}
