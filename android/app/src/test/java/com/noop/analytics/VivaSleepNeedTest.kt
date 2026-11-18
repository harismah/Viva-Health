package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/** Viva's sleep need against nights read from the reference app (need, debt, naps), within a few minutes. */
class VivaSleepNeedTest {
    @Test
    fun aShortNightCarriesHalfItsShortfallAsDebt() {
        // 242 min asleep against a 498 min need with no debt; the reference then asked 590 with 127 of debt.
        val next = RestScorer.nightNeed(RestScorer.NightNeed(498.0, 0.0), 242.0, previousDayStrain21 = 4.7, previousNapMin = 0.0)
        assertEquals(127.0, next.debtMin, 3.0)
        assertEquals(590.0, next.needMin, 3.0)
    }

    @Test
    fun aNapComesOffTheNextNightsNeed() {
        // 474 min asleep against 555 (90 of it debt), strain 6.7, then a 50 min nap: the reference asked 466 with 51 of debt.
        val next = RestScorer.nightNeed(RestScorer.NightNeed(555.0, 90.0), 474.0, previousDayStrain21 = 6.7, previousNapMin = 50.0)
        assertEquals(51.0, next.debtMin, 3.0)
        assertEquals(466.0, next.needMin, 3.0)
    }

    @Test
    fun strainAddsMinutesFasterAsTheDayGetsHarder() {
        assertEquals(0.0, RestScorer.strainNeedMin(null), 0.0)
        assertEquals(1.5, RestScorer.strainNeedMin(4.0), 1.0)
        assertEquals(22.0, RestScorer.strainNeedMin(12.6), 1.5)
        assertEquals(29.0, RestScorer.strainNeedMin(14.0), 1.5)
    }

    @Test
    fun aRestedWearerOwesNothingAndTheNeedIsNeverCappedAtNineAndAHalfHours() {
        assertEquals(0.0, RestScorer.nightNeed(RestScorer.NightNeed(470.0, 0.0), 555.0, 4.2, 0.0).debtMin, 0.0)
        // A long debt pushes past the old 9.5 h cap, as the reference's 10:10 did.
        assertEquals(true, RestScorer.nightNeed(RestScorer.NightNeed(600.0, 120.0), 280.0, 12.6, 0.0).needMin > 570.0)
    }
}
