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
        assertTrue(VivaStress.level(resting + 23.0, resting) > 2.0)
        val levels = (40..180 step 5).map { VivaStress.level(it.toDouble(), resting) }
        assertEquals(levels.sorted(), levels)
        assertTrue(levels.all { it > 0.0 && it < 3.0 })
        // The same heart rate is calmer for a wearer whose resting heart rate is higher.
        assertTrue(VivaStress.level(75.0, 65.0) < VivaStress.level(75.0, 55.0))
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
    }
}
