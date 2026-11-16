package com.noop.ui.viva

import org.junit.Assert.assertEquals
import org.junit.Test

class VivaHeartZoneTest {
    // Resting 50, maximum 190: the reserve is 140, so 50% of it is 120 bpm and each further 10% is 14 bpm.
    private fun zone(bpm: Int) = VivaHeartZone.zone(bpm, restingHr = 50.0, maxHr = 190.0)

    @Test
    fun zonesStepEveryTenthOfReserveFromHalf() {
        assertEquals(listOf(0, 0, 1, 1, 2, 3, 4, 5, 5), listOf(40, 119, 120, 133, 134, 148, 162, 176, 230).map(::zone))
    }

    @Test
    fun everyZoneHasItsOwnColour() {
        val colours = (0..5).map(VivaHeartZone::color)
        assertEquals(6, colours.toSet().size)
        assertEquals(
            listOf(VivaStyle.strain, VivaStyle.recoveryGreen, VivaStyle.recoveryYellow, VivaStyle.warning, VivaStyle.recoveryRed),
            colours.drop(1),
        )
    }
}
