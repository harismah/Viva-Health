package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The band span trim: a detected still run is narrowed to the span the strap's own sleep_state scored
 * asleep. The band is modelled the way it arrives, as a two-minute burst every half hour, with
 * made-up nights.
 */
class BandSpanTrimTest {
    private val hour = 3600L
    private val burstEvery = 1800L
    private val asleep = SleepStager.bandStateAsleep

    /** One 120-sample burst at each half hour in [from, to), its state given by [state] at the burst's start. */
    private fun band(from: Long, to: Long, state: (Long) -> Int): List<Pair<Long, Int>> =
        (from until to step burstEvery).flatMap { t -> (0 until 120).map { (t + it) to state(t) } }

    private fun run(start: Long, end: Long) = SleepStager.Period("sleep", start, end)

    @Test
    fun aRunTheBandDoesNotCoverIsLeftAlone() {
        val p = run(0, 8 * hour)
        assertSame(p, SleepStager.bandTrimmedPeriod(p, emptyList()))
        // A single burst is under the coverage floor, whatever it says.
        assertSame(p, SleepStager.bandTrimmedPeriod(p, band(0, burstEvery) { 1 }.take(100)))
        // Switched off, a fully covered run is untouched too.
        assertSame(p, SleepStager.bandTrimmedPeriod(p, band(0, 8 * hour) { 0 }, enabled = false))
    }

    @Test
    fun aStillRunTheBandNeverScoresAsleepIsDropped() {
        // An evening at a desk: the band reads "still" and "wake" throughout.
        assertNull(SleepStager.bandTrimmedPeriod(run(0, 2 * hour), band(0, 2 * hour) { if (it < hour) 1 else 0 }))
    }

    @Test
    fun theOnsetFollowsTheBandAsItMoves() {
        // The same 12-hour still run, with the strap falling asleep 3, 4 and 6 hours in and waking at 11.
        for (onsetHour in listOf(3L, 4L, 6L)) {
            val states = band(0, 12 * hour) { if (it >= onsetHour * hour && it < 11 * hour) asleep else 0 }
            val trimmed = SleepStager.bandTrimmedPeriod(run(0, 12 * hour), states)!!
            // Each edge sits midway between the deciding asleep sample and its neighbour on the far side.
            val lastWakeSample = onsetHour * hour - burstEvery + 119
            assertEquals((lastWakeSample + onsetHour * hour) / 2, trimmed.start)
            val lastAsleepSample = 11 * hour - burstEvery + 119
            assertEquals((lastAsleepSample + 11 * hour) / 2, trimmed.end)
        }
    }

    @Test
    fun aBriefAsleepBlipBeforeTheNightDoesNotOpenIt() {
        // Asleep for one burst at hour 2, "up" for the next two, then asleep from hour 3.5 on.
        val states = band(0, 10 * hour) {
            when {
                it == 2 * hour -> asleep
                it in (2 * hour + burstEvery)..(3 * hour) -> 3
                it >= 3 * hour + burstEvery -> asleep
                else -> 0
            }
        }
        val trimmed = SleepStager.bandTrimmedPeriod(run(0, 10 * hour), states)!!
        assertTrue("onset ${trimmed.start} should fall after the blip", trimmed.start > 3 * hour)
        assertTrue(trimmed.start < 3 * hour + burstEvery)
        // The band is asleep to the end of the run, so the end is the run's own.
        assertEquals(10 * hour, trimmed.end)
    }

    @Test
    fun sleepingHeartRateIsTheNightsTypicalLowNotItsFloor() {
        // Ten five-minute bins whose means run 50 to 59 bpm, one sample every five seconds.
        val hr = (0 until 10).flatMap { bin ->
            (0 until 60).map { com.noop.data.HrSample(deviceId = "t", ts = bin * 300L + it * 5L, bpm = 50 + bin) }
        }
        assertEquals(50, SleepStager.sessionRestingHR(0, 2999, hr))
        assertEquals(52, SleepStager.sessionSleepingHR(0, 2999, hr))
        assertNull(SleepStager.sessionSleepingHR(0, 2999, emptyList()))
    }

    @Test
    fun theTrimOnlyEverNarrows() {
        // The band reads asleep well outside the run on both sides.
        val trimmed = SleepStager.bandTrimmedPeriod(run(4 * hour, 8 * hour), band(0, 12 * hour) { asleep })!!
        assertEquals(4 * hour, trimmed.start)
        assertEquals(8 * hour, trimmed.end)
    }
}
