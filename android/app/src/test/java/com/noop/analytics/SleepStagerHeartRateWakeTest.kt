package com.noop.analytics

import com.noop.data.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SleepStagerHeartRateWakeTest {
    private val start = 1_000_000L
    private val end = start + 60 * 60L

    private fun hr(highMinutes: Set<Int>) = (0 until 60 * 60).map {
        HrSample("x", start + it, if (it / 60 in highMinutes) 70 else 55)
    }

    @Test
    fun raisedMinutesBecomeWakeAndNeighboursMerge() {
        val stages = listOf(StageSegment(start, start + 1800, "light"), StageSegment(start + 1800, end, "deep"))
        // 70 >= 55 * 1.17 (64.35); minutes 10 and 11 are adjacent, minute 40 stands alone.
        val out = SleepStager.heartRateWake(stages, start, end, hr(setOf(10, 11, 40)))
        assertEquals(
            listOf(
                StageSegment(start, start + 600, "light"),
                StageSegment(start + 600, start + 720, "wake"),
                StageSegment(start + 720, start + 1800, "light"),
                StageSegment(start + 1800, start + 2400, "deep"),
                StageSegment(start + 2400, start + 2460, "wake"),
                StageSegment(start + 2460, end, "deep"),
            ),
            out,
        )
        assertEquals(57.0 / 60.0, SleepStager.efficiency(start, end, out), 1e-9)
    }

    @Test
    fun edgeInGapFollowsTheHeartRateDropAndRise() {
        val t0 = 2_000_000L
        // Awake at 70 for the first 12 minutes of a 30-minute gap, then asleep at 55 for an hour after it.
        val falling = (0 until 90 * 60).map { HrSample("x", t0 + it, if (it < 12 * 60) 70 else 55) }
        assertEquals(t0 + 11 * 60, SleepStager.hrEdgeInGap(falling, t0, t0 + 30 * 60, onset = true))
        // Asleep at 55 for the hour before the gap, up at 70 from minute 20 of it.
        val rising = (0 until 90 * 60).map { HrSample("x", t0 - 3600 + it, if (it < 3600 + 20 * 60) 55 else 70) }
        assertEquals(t0 + 19 * 60, SleepStager.hrEdgeInGap(rising, t0, t0 + 30 * 60, onset = false))
        // A gap inside one burst (under the window) keeps the midpoint.
        assertEquals(null, SleepStager.hrEdgeInGap(falling, t0, t0 + 60, onset = true))
    }

    @Test
    fun steadyOrSparseHeartRateLeavesStagesAlone() {
        val stages = listOf(StageSegment(start, end, "light"))
        assertEquals(stages, SleepStager.heartRateWake(stages, start, end, hr(emptySet())))
        assertSame(stages, SleepStager.heartRateWake(stages, start, end, hr(setOf(5)).take(20 * 60)))
    }
}
