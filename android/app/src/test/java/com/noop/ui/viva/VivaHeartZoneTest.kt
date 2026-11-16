package com.noop.ui.viva

import org.junit.Assert.assertEquals
import org.junit.Test

class VivaHeartZoneTest {
    // Resting 50, maximum 190: the reserve is 140, so 50% of it is 120 bpm and each further 10% is 14 bpm.
    private fun zone(bpm: Int) = VivaHeartZone.zone(bpm, restingHr = 50.0, maxHr = 190.0)
    private fun blend(bpm: Int) = VivaHeartZone.blend(bpm, restingHr = 50.0, maxHr = 190.0)

    @Test
    fun zonesStepEveryTenthOfReserveFromHalf() {
        assertEquals(listOf(0, 0, 1, 1, 2, 3, 4, 5, 5), listOf(40, 119, 120, 133, 134, 148, 162, 176, 230).map(::zone))
    }

    @Test
    fun colourScaleStartsBelowZoneOneAndEndsWhereZoneFiveStarts() {
        assertEquals(0f, blend(60), 0f)
        assertEquals(0f, blend(106), 1e-4f)
        assertEquals(1f, blend(120), 1e-4f)
        assertEquals(3f, blend(148), 1e-4f)
        assertEquals(5f, blend(176), 1e-4f)
        assertEquals(5f, blend(200), 0f)
        assertEquals(VivaStyle.strain, VivaHeartZone.color(0f))
        assertEquals(VivaStyle.recoveryYellow, VivaHeartZone.color(3f))
        assertEquals(VivaStyle.recoveryRed, VivaHeartZone.color(5f))
    }
}
