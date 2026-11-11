package com.noop.ui.viva

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * The Kotlin twins of the iPhone StrandDesign tests (VivaStyleTests, VivaScoreRingTests,
 * VivaRangeBarTests, VivaAlarmReadoutTests). Same inputs, same expected values, so the two
 * platforms cannot drift on the pure logic behind the components.
 */
class VivaComponentsTest {

    // VivaStyleTests

    @Test
    fun recoveryColorBands() {
        assertEquals(VivaStyle.recoveryGreen, VivaStyle.recoveryColor(67.0))
        assertEquals(VivaStyle.recoveryYellow, VivaStyle.recoveryColor(66.9))
        assertEquals(VivaStyle.recoveryYellow, VivaStyle.recoveryColor(34.0))
        assertEquals(VivaStyle.recoveryRed, VivaStyle.recoveryColor(33.9))
        assertEquals(VivaStyle.textSecondary, VivaStyle.recoveryColor(null))
    }

    // VivaScoreRingTests

    @Test
    fun ringFractionClamps() {
        assertEquals(1.0, VivaScoreRing.fraction(25.0, 21.0), 0.0)
        assertEquals(0.0, VivaScoreRing.fraction(-3.0, 21.0), 0.0)
        assertEquals(0.0, VivaScoreRing.fraction(null, 21.0), 0.0)
        assertEquals(0.5, VivaScoreRing.fraction(10.5, 21.0), 0.0)
        assertEquals(0.0, VivaScoreRing.fraction(5.0, 0.0), 0.0)
    }

    @Test
    fun ringWithNullValueShowsDashes() {
        assertEquals("--", VivaScoreRing.text(null, VivaScoreRing.Format.PERCENT))
        assertEquals("--", VivaScoreRing.text(null, VivaScoreRing.Format.DECIMAL1))
    }

    @Test
    fun ringText() {
        assertEquals("85", VivaScoreRing.text(85.0, VivaScoreRing.Format.PERCENT))
        assertEquals("85", VivaScoreRing.text(84.6, VivaScoreRing.Format.PERCENT))
        assertEquals("7.5", VivaScoreRing.text(7.46, VivaScoreRing.Format.DECIMAL1))
    }

    /** Swift's `rounded()` sends halves away from zero; Kotlin's `round` would give 84 here. */
    @Test
    fun ringPercentRoundsHalvesLikeSwift() {
        assertEquals("85", VivaScoreRing.text(84.5, VivaScoreRing.Format.PERCENT))
        assertEquals("83", VivaScoreRing.text(82.5, VivaScoreRing.Format.PERCENT))
    }

    // VivaRangeBarTests

    @Test
    fun rangeBarClamps() {
        assertEquals(200f, VivaRangeBar.layout(150.0, null, 100.0, 200f).fill, 0f)
        assertEquals(0f, VivaRangeBar.layout(-5.0, null, 100.0, 200f).fill, 0f)
        assertEquals(0f, VivaRangeBar.layout(50.0, null, 0.0, 200f).fill, 0f)
    }

    @Test
    fun rangeBracketPosition() {
        val layout = VivaRangeBar.layout(50.0, 20.0..40.0, 100.0, 200f)
        assertEquals(100f, layout.fill, 0f)
        assertEquals(40f, layout.hatchStart, 0f)
        assertEquals(40f, layout.hatchWidth, 0f)
    }

    @Test
    fun rangeBracketClampsToBar() {
        val layout = VivaRangeBar.layout(10.0, 80.0..140.0, 100.0, 200f)
        assertEquals(160f, layout.hatchStart, 0f)
        assertEquals(40f, layout.hatchWidth, 0f)
    }

    @Test
    fun weekCardScaleIgnoresEmptyDays() {
        val white = Color.White
        val days = listOf(
            VivaWeekBarCard.Day("Sun", 8, emptyList(), null, isToday = false),
            VivaWeekBarCard.Day(
                "Mon", 9,
                listOf(VivaWeekBarCard.Segment(4.0, white), VivaWeekBarCard.Segment(2.0, white)),
                "6", isToday = true,
            ),
        )
        assertEquals(6.0, VivaWeekBarCard.peak(days), 0.0)
        assertFalse(VivaWeekBarCard.isEmpty(days))
        assertTrue(VivaWeekBarCard.isEmpty(listOf(days[0])))
    }

    @Test
    fun halfDialFractionClamps() {
        assertEquals(1.0, VivaHalfDial.fraction(4.2, 3.0), 0.0)
        assertEquals(0.0, VivaHalfDial.fraction(null, 3.0), 0.0)
        assertEquals(0.5, VivaHalfDial.fraction(1.5, 3.0), 0.0)
    }

    // VivaAlarmReadoutTests

    private val utc = ZoneOffset.UTC
    private val locale = Locale.US

    @Test
    fun armedAlarmShowsNextFireTimeAndOnStatus() {
        val fire = LocalDateTime.of(2026, 11, 10, 7, 0).toInstant(utc).toEpochMilli()
        val readout = VivaAlarmReadout.resolve(fire, baseWakeMinutes = 6 * 60 + 30, sleepNeedMinutes = 9 * 60, zone = utc, locale = locale)
        assertTrue(readout.isArmed)
        assertEquals(7 * 60, readout.wakeMinutes)
        assertEquals(22 * 60, readout.bedtimeMinutes)
        assertEquals("Alarm on", readout.statusText)
    }

    @Test
    fun unarmedAlarmFallsBackToBaseWakeAndMakesNoPromise() {
        val readout = VivaAlarmReadout.resolve(null, baseWakeMinutes = 6 * 60 + 30, sleepNeedMinutes = 8 * 60, zone = utc, locale = locale)
        assertFalse(readout.isArmed)
        assertEquals(6 * 60 + 30, readout.wakeMinutes)
        assertEquals(22 * 60 + 30, readout.bedtimeMinutes)
        assertEquals("Alarm off", readout.statusText)
        assertNull(readout.nextFireEpochMs)
    }

    @Test
    fun bedtimeWrapsBeforeMidnight() {
        val readout = VivaAlarmReadout.resolve(null, baseWakeMinutes = 5 * 60, sleepNeedMinutes = 9 * 60, zone = utc, locale = locale)
        assertEquals(20 * 60, readout.bedtimeMinutes)
        assertEquals("9:00", readout.timeInBedText)
    }

    @Test
    fun clockTextSplitsTimeAndPeriod() {
        val (time, period) = VivaAlarmReadout.clockParts(22 * 60, locale)
        assertEquals("10:00", time)
        assertEquals("PM", period)
    }

    /** Android-only: the app's own 12/24-hour setting wins over the locale's convention. */
    @Test
    fun clockTextHonoursTheAppClockSetting() {
        assertEquals("22:00", VivaAlarmReadout.clockText(22 * 60, locale, is24Hour = true))
        assertEquals("12:05 AM", VivaAlarmReadout.clockText(5, locale, is24Hour = false))
        assertEquals("12:00 PM", VivaAlarmReadout.clockText(12 * 60, locale, is24Hour = false))
    }
}
