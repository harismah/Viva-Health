package com.noop.ui.viva

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Viva's real-time stress level and its band totals, with made-up heart rates. */
class VivaStressTest {
    @Test
    fun theLevelRisesWithHeartRateOverRestingAndStaysOnTheScale() {
        val resting = 56.0
        // Asleep at resting heart rate reads LOW, the midpoint reads 1.5, a clear rise reads HIGH.
        assertTrue(VivaStress.level(resting, resting) < 0.5)
        assertEquals(1.5, VivaStress.level(resting + VivaStress.MIDPOINT_OVER_RESTING_BPM, resting), 1e-9)
        assertTrue(VivaStress.level(resting + 40.0, resting) > 2.0)
        val levels = (40..180 step 5).map { VivaStress.level(it.toDouble(), resting) }
        assertEquals(levels.sorted(), levels)
        assertTrue(levels.all { it > 0.0 && it < 3.0 })
        // The same heart rate is calmer for a wearer whose resting heart rate is higher.
        assertTrue(VivaStress.level(75.0, 65.0) < VivaStress.level(75.0, 55.0))
    }

    @Test
    fun lowerHrvReadsHigherAtTheSameHeartRate() {
        val hr = 69.0; val resting = 55.0; val typical = 47.0
        val calm = VivaStress.level(hr, resting, rmssd = 71.0, typicalHrv = typical)
        val tense = VivaStress.level(hr, resting, rmssd = 30.0, typicalHrv = typical)
        assertTrue(calm < 1.0)
        assertTrue(tense > 1.0)
        // HRV at its typical level, or unknown, leaves the heart-rate reading as it is.
        assertEquals(VivaStress.level(hr, resting), VivaStress.level(hr, resting, typical, typical), 1e-12)
        // The log ratio is clamped: beyond the range a wilder RMSSD changes nothing.
        assertEquals(VivaStress.level(hr, resting, 500.0, typical), VivaStress.level(hr, resting, 5000.0, typical), 0.0)
        assertEquals(VivaStress.level(hr, resting, 2.0, typical), VivaStress.level(hr, resting, 1.0, typical), 0.0)
    }

    @Test
    fun bucketRmssdNeedsEnoughCleanPairsInOneStep() {
        fun beat(ts: Long, ms: Int) = com.noop.data.RrInterval("d", ts, ms)
        // 70 beats alternating 800 / 840 ms one second apart: 69 pairs, each differing by 40 ms.
        val steady = (0 until 70).map { beat(it.toLong(), if (it % 2 == 0) 800 else 840) }
        assertEquals(40.0, VivaStress.bucketRmssd(steady).getValue(0L), 1e-9)
        // Too few pairs, a gap over two seconds, and an out-of-range or wildly different beat all drop out.
        assertTrue(VivaStress.bucketRmssd(steady.take(40)).isEmpty())
        assertTrue(VivaStress.bucketRmssd((0 until 70).map { beat(it * 5L, 800) }).isEmpty())
        val spiky = (0 until 200).map { beat(600L + it, if (it % 2 == 0) 800 else 2500) }
        assertTrue(VivaStress.bucketRmssd(spiky).isEmpty())
        // Pairs never straddle two steps.
        val two = (0 until 70).map { beat(540L + it, 800) }
        assertTrue(VivaStress.bucketRmssd(two).isEmpty())
    }

    @Test
    fun bandMinutesCountTenMinutesAStep() {
        val points = listOf(0.2, 0.9, 1.0, 1.99, 2.0, 2.8).mapIndexed { i, level -> VivaStress.Point(i * 600L, level) }
        assertEquals(listOf(20, 20, 20), VivaStress.bandMinutes(points))
        assertEquals(listOf(0, 0, 0), VivaStress.bandMinutes(emptyList()))
    }

    @Test
    fun aSleepSpanSplitsTheDay() {
        val now = VivaStressNow(listOf(VivaStress.Point(0, 0.3), VivaStress.Point(600, 1.4)), listOf(0L..599L))
        assertTrue(now.asleep(now.points[0]))
        assertTrue(!now.asleep(now.points[1]))
        assertEquals(1.4, now.value, 0.0)
        // The sleep span covers the first of two evenly spaced steps: the left end of the trace.
        assertEquals(listOf(0f..0f), now.sleepShading)
        val night = VivaStressNow((0 until 5).map { VivaStress.Point(it * 600L, 0.5) }, listOf(600L..1800L))
        assertEquals(listOf(0.25f..0.75f), night.sleepShading)
    }

    @Test
    fun dailyMeansAverageEachLocalDayOldestFirst() {
        val zone = java.time.ZoneOffset.UTC
        val points = listOf(VivaStress.Point(86_400L, 2.0), VivaStress.Point(0L, 1.0), VivaStress.Point(600L, 0.0))
        assertEquals(listOf(0.5, 2.0), VivaStress.dailyMeans(points, zone))
    }
}
