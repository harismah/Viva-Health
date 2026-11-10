package com.noop.ui.viva

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * The rules behind the Trend View and the Sleep, Recovery and Strain detail screens, checked without
 * a device: how a metric reads and prints a day, how a week and a baseline are cut, and how a score
 * is handed from the home ring to the screen it opens.
 */
class VivaDetailScreensTest {
    private val locale = Locale.US
    private val today = LocalDate.of(2026, 11, 10)

    private fun day(back: Int, hrv: Double? = null, sleepMin: Double? = null, deep: Double? = null, rem: Double? = null, light: Double? = null) =
        DailyMetric(
            deviceId = "strap", day = today.minusDays(back.toLong()).toString(),
            avgHrv = hrv, totalSleepMin = sleepMin, deepMin = deep, remMin = rem, lightMin = light,
        )

    // Trend metric

    @Test
    fun valueReadsEachMetricOffTheDayRow() {
        val row = DailyMetric(
            deviceId = "strap", day = "2026-11-10", totalSleepMin = 420.0, efficiency = 0.91, deepMin = 80.0, remMin = 100.0,
            restingHr = 52, avgHrv = 61.5, recovery = 72.0, strain = 50.0, steps = 8123,
        )
        assertEquals(420.0, VivaTrendMetric.HOURS_OF_SLEEP.value(row)!!, 1e-9)
        assertEquals(180.0, VivaTrendMetric.RESTORATIVE_SLEEP.value(row)!!, 1e-9)
        // Efficiency is stored as a fraction on some rows and a percent on others; both read as percent.
        assertEquals(91.0, VivaTrendMetric.SLEEP_EFFICIENCY.value(row)!!, 1e-9)
        assertEquals(91.0, VivaTrendMetric.SLEEP_EFFICIENCY.value(row.copy(efficiency = 91.0))!!, 1e-9)
        assertEquals(52.0, VivaTrendMetric.RESTING_HR.value(row)!!, 1e-9)
        assertEquals(10.5, VivaTrendMetric.STRAIN.value(row)!!, 1e-9)
        assertEquals(8123.0, VivaTrendMetric.STEPS.value(row)!!, 1e-9)
        // Sleep performance is not on the row: it comes from the series handed in, or is missing.
        assertNull(VivaTrendMetric.SLEEP_PERFORMANCE.value(row))
        assertEquals(88.0, VivaTrendMetric.SLEEP_PERFORMANCE.value(row, mapOf("2026-11-10" to 88.0))!!, 1e-9)
        // One stage missing still counts the other; both missing is no reading, not zero.
        assertEquals(80.0, VivaTrendMetric.RESTORATIVE_SLEEP.value(row.copy(remMin = null))!!, 1e-9)
        assertNull(VivaTrendMetric.RESTORATIVE_SLEEP.value(row.copy(deepMin = null, remMin = null)))
    }

    @Test
    fun textPrintsEachMetricInItsOwnForm() {
        assertEquals("--", VivaTrendMetric.HRV.text(null, locale))
        assertEquals("--", VivaTrendMetric.HRV.text(Double.NaN, locale))
        assertEquals("7:32", VivaTrendMetric.HOURS_OF_SLEEP.text(452.0, locale))
        assertEquals("14.6", VivaTrendMetric.RESPIRATORY_RATE.text(14.56, locale))
        assertEquals("8,123", VivaTrendMetric.STEPS.text(8123.0, locale))
        assertEquals("73%", VivaTrendMetric.RECOVERY.text(72.5, locale))
        assertEquals("62", VivaTrendMetric.HRV.text(61.5, locale))
    }

    @Test
    fun pointsCoverEveryCalendarDayAndLeaveGaps() {
        val days = listOf(day(2, hrv = 50.0), day(0, hrv = 60.0))
        val points = VivaTrendMetric.HRV.points(days, 3, today)
        assertEquals(listOf("2026-11-08", "2026-11-09", "2026-11-10"), points.map { it.key })
        assertEquals(listOf(50.0, null, 60.0), points.map { it.value })
        // The period before it ends the day before this one starts.
        assertEquals("2026-11-07", VivaTrendMetric.HRV.points(days, 3, today, endingDaysAgo = 3).last().key)
    }

    @Test
    fun averageNeedsTwoReadingsAndChangeNeedsBothPeriods() {
        val days = listOf(day(2, hrv = 50.0), day(0, hrv = 60.0))
        assertEquals(55.0, VivaTrendMetric.average(VivaTrendMetric.HRV.points(days, 3, today))!!, 1e-9)
        assertNull(VivaTrendMetric.average(VivaTrendMetric.HRV.points(days, 1, today)))
        assertEquals(10, VivaTrendMetric.changePercent(55.0, 50.0))
        assertEquals(-20, VivaTrendMetric.changePercent(40.0, 50.0))
        assertNull(VivaTrendMetric.changePercent(55.0, null))
        assertNull(VivaTrendMetric.changePercent(55.0, 0.0))
    }

    @Test
    fun clockAndLevelFloor() {
        assertEquals("0:05", VivaTrendMetric.clock(5.0))
        assertEquals("8:00", VivaTrendMetric.clock(479.6))
        assertEquals(0.0, VivaTrendMetric.levelFloor(emptyList()), 1e-9)
        // A narrow band is widened to a tenth of the top value so the dots are not pinned to the axis.
        assertEquals(46.7, VivaTrendMetric.levelFloor(listOf(53.0, 52.0)), 1e-9)
        assertEquals(30.0, VivaTrendMetric.levelFloor(listOf(40.0, 50.0)), 1e-9)
    }

    @Test
    fun idsRoundTripAndVitalKeysResolve() {
        VivaTrendMetric.entries.forEach { assertEquals(it, VivaTrendMetric.fromId(it.id)) }
        assertNull(VivaTrendMetric.fromId("nope"))
        assertEquals(VivaTrendMetric.RESTING_HR, VivaTrendMetric.forVitalKey("rhr"))
        assertNull(VivaTrendMetric.forVitalKey("skin"))
    }

    @Test
    fun weekDaysEndOnTheGivenDayAndChartLevelsOffTheirFloor() {
        val days = listOf(day(1, hrv = 52.0, sleepMin = 400.0), day(0, hrv = 53.0, sleepMin = 420.0))
        val bars = VivaTrendMetric.HOURS_OF_SLEEP.weekDays(days, today, locale)
        assertEquals(7, bars.size)
        assertEquals("Tue", bars.last().label)
        assertEquals(10, bars.last().date)
        assertTrue(bars.last().isToday)
        assertFalse(bars[5].isToday)
        assertEquals(420.0, bars.last().total, 1e-9)
        assertEquals("7:00", bars.last().valueText)
        assertTrue(bars[0].segments.isEmpty())
        assertNull(bars[0].valueText)

        // A level metric is drawn above its floor, so a one-beat move is visible; the label is the real value.
        val dots = VivaTrendMetric.HRV.weekDays(days, today, locale)
        assertEquals(VivaWeekBarCard.Style.DOT, VivaTrendMetric.HRV.style)
        assertEquals(VivaWeekBarCard.Style.BAR, VivaTrendMetric.HOURS_OF_SLEEP.style)
        val floor = VivaTrendMetric.levelFloor(listOf(52.0, 53.0))
        assertEquals(53.0 - floor, dots.last().total, 1e-9)
        assertEquals("53", dots.last().valueText)
    }

    @Test
    fun baselineIsTheMeanOfTheThirtyRowsBeforeTheDay() {
        val days = (40 downTo 1).map { day(it, hrv = if (it > 30) 100.0 else 50.0) } + day(0, hrv = 90.0)
        // Only the 30 rows before today count: the older 100s and today's own 90 are left out.
        assertEquals(50.0, VivaTrendMetric.HRV.baseline(days, today.toString())!!, 1e-9)
        assertNull(VivaTrendMetric.STEPS.baseline(days, today.toString()))
        assertNull(VivaTrendMetric.HRV.baseline(days, today.minusDays(40).toString()))
    }

    // Sleep detail

    @Test
    fun sleepAnchorIsTheNewestDayWithStageMinutes() {
        val days = listOf(day(2, deep = 60.0), day(1, light = 200.0), day(0, sleepMin = 400.0))
        assertEquals(today.minusDays(1).toString(), VivaSleepDetail.anchorDay(days))
        assertNull(VivaSleepDetail.anchorDay(listOf(day(0, sleepMin = 400.0))))
    }

    @Test
    fun sleepNightOffsetCountsDaysBackAndNeverGoesNegative() {
        assertEquals(0, VivaSleepDetail.nightOffset(today.toString(), today))
        assertEquals(1, VivaSleepDetail.nightOffset(today.minusDays(1).toString(), today))
        assertEquals(0, VivaSleepDetail.nightOffset(today.plusDays(1).toString(), today))
        assertEquals(0, VivaSleepDetail.nightOffset(null, today))
        assertEquals(0, VivaSleepDetail.nightOffset("not a day", today))
    }

    @Test
    fun sleepLevelsNeedAndShares() {
        assertNull(VivaSleepDetail.level(null))
        assertEquals(VivaLevel.OPTIMAL, VivaSleepDetail.level(85.0))
        assertEquals(VivaLevel.SUFFICIENT, VivaSleepDetail.level(84.9))
        assertEquals(VivaLevel.SUFFICIENT, VivaSleepDetail.level(70.0))
        assertEquals(VivaLevel.POOR, VivaSleepDetail.level(69.9))

        assertEquals(450.0, VivaSleepDetail.sleepNeedMinutes(null), 1e-9)
        assertEquals(450.0, VivaSleepDetail.sleepNeedMinutes(380.0), 1e-9)
        assertEquals(495.0, VivaSleepDetail.sleepNeedMinutes(495.0), 1e-9)

        assertEquals(25, VivaSleepDetail.share(120.0, 480.0))
        assertEquals(0, VivaSleepDetail.share(120.0, 0.0))
        assertNull(VivaSleepDetail.typicalRange(null))
        val range = VivaSleepDetail.typicalRange(100.0)!!
        assertEquals(90.0, range.start, 1e-9)
        assertEquals(110.0, range.endInclusive, 1e-9)
        assertEquals("83%", VivaSleepDetail.percentText(82.5))
        assertNull(VivaSleepDetail.percentText(null))
    }

    // Pillar detail

    @Test
    fun pillarRowsDropAMetricWithNeitherValueNorBaseline() {
        val history = listOf(day(2, hrv = 50.0), day(1, hrv = 60.0))
        val row = day(0, hrv = 66.0)
        val rows = VivaPillarDetail.rows(
            listOf(VivaTrendMetric.HRV, VivaTrendMetric.RESTING_HR, VivaTrendMetric.SLEEP_PERFORMANCE),
            row, history + row, row.day, sleepPerformance = mapOf(row.day to 91.0),
        )
        assertEquals(listOf(VivaTrendMetric.HRV, VivaTrendMetric.SLEEP_PERFORMANCE), rows.map { it.metric })
        assertEquals(66.0, rows[0].value!!, 1e-9)
        assertEquals(55.0, rows[0].baseline!!, 1e-9)
        assertEquals(91.0, rows[1].value!!, 1e-9)
        assertNull(rows[1].baseline)

        // No row for the day: a metric with history still shows, against its baseline alone.
        val absent = VivaPillarDetail.rows(listOf(VivaTrendMetric.HRV), null, history, today.toString())
        assertNull(absent.single().value)
        assertEquals(55.0, absent.single().baseline!!, 1e-9)
    }

    @Test
    fun pillarTitleNamesTheDay() {
        assertEquals("Today", VivaPillarDetail.title(today, today, locale))
        assertEquals("Yesterday", VivaPillarDetail.title(today.minusDays(1), today, locale))
        assertEquals("Sun, Nov 8", VivaPillarDetail.title(today.minusDays(2), today, locale))
    }

    // Handing a score over

    @Test
    fun handedScoreKeepsNoScoreApartFromNothingHanded() {
        assertNull(VivaShell.handedScore(null))
        assertEquals(VivaHandedScore(null), VivaShell.handedScore(VivaShell.scoreArgument(null)))
        assertEquals(VivaHandedScore(null), VivaShell.handedScore(VivaShell.scoreArgument(Double.NaN)))
        assertEquals(VivaHandedScore(72.25), VivaShell.handedScore(VivaShell.scoreArgument(72.25)))
        assertNull(VivaShell.handedScore("garbage"))
    }

    @Test
    fun detailRoutesFillTheirPatternsAndHideTheTabBar() {
        assertEquals("viva_sleep?score=81.5", VivaShell.sleepRoute(81.5))
        assertEquals("viva_recovery?day=2026-11-10&score=none", VivaShell.recoveryRoute("2026-11-10", null))
        assertEquals("viva_strain?day=2026-11-09&score=10.5", VivaShell.strainRoute("2026-11-09", 10.5))
        listOf(VivaShell.SLEEP, VivaShell.RECOVERY, VivaShell.STRAIN, VivaShell.TREND).forEach { assertFalse(VivaShell.showsTabBar(it)) }
        assertTrue(VivaShell.showsTabBar(VivaShell.FULL_HEALTH))
    }

    // Line chart

    @Test
    fun lineChartBoundsAndAxis() {
        assertEquals(0.0..1.0, VivaLineChart.bounds(emptyList()))
        assertEquals(0.0..1.0, VivaLineChart.bounds(listOf(60.0, 60.0)))
        assertEquals(48.0..72.0, VivaLineChart.bounds(listOf(50.0, 70.0)))
        assertEquals(0.0..3.0, VivaLineChart.bounds(listOf(50.0, 70.0), range = 0.0..3.0))
        assertEquals(listOf("70", "63", "57", "50"), VivaLineChart.axisLabels(listOf(50.0, 70.0, 61.0)))
        assertTrue(VivaLineChart.axisLabels(listOf(60.0, 60.0)).isEmpty())
    }
}
