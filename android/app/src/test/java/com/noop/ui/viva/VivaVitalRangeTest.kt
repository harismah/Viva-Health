package com.noop.ui.viva

import com.noop.ui.viva.VivaVitalRange.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import kotlin.math.roundToInt

/** The 30-day banding of a vital, checked on made-up windows whose quartiles are easy to work out by hand. */
class VivaVitalRangeTest {
    private val oneDecimal = { v: Double -> String.format(Locale.US, "%.1f", v) }
    private val whole = { v: Double -> v.roundToInt().toString() }

    // 16.0, 16.1 ... 17.3: quartiles 16.325 and 16.975, median 16.65, so orange from 17.5 and red from 17.9.
    private val breaths = (160..173).map { it / 10.0 }
    private fun resp(value: Double) = VivaVitalRange.band("resp", value, breaths, "rpm", oneDecimal)!!

    // 40 ... 53: quartiles 43.25 and 49.75, median 46.5, so green from 29 and red under 22.
    private val variability = (40..53).map { it.toDouble() }
    private fun hrv(value: Double) = VivaVitalRange.band("hrv", value, variability, "ms", whole)!!

    @Test
    fun aHighIsBadVitalMovesThroughEveryChipAndBand() {
        assertEquals(listOf(Level.GREEN, "within 16.3 - 17.0"), resp(16.5).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.GREEN, "low < 16.3"), resp(16.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.GREEN, "near 17.0"), resp(17.4).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.ORANGE, "elevated > 17.4"), resp(17.5).let { listOf(it.level, it.chip) })
        assertEquals(Level.ORANGE, resp(17.8).level)
        assertEquals(Level.RED, resp(17.9).level)
        assertEquals(
            "Typical range: 16.3 to 17.0 rpm, the middle half of your last 30 days.\n" +
                "Green: 17.4 and below. Orange: 17.5 to 17.8. Red: 17.9 and above.",
            resp(16.5).detail,
        )
    }

    @Test
    fun aLowIsBadVitalIsJudgedTheOtherWayRound() {
        assertEquals(listOf(Level.GREEN, "elevated > 50"), hrv(55.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.GREEN, "within 43 - 50"), hrv(43.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.GREEN, "near 43"), hrv(29.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.ORANGE, "low < 29"), hrv(28.0).let { listOf(it.level, it.chip) })
        assertEquals(Level.ORANGE, hrv(22.0).level)
        assertEquals(Level.RED, hrv(21.0).level)
        assertEquals(
            "Typical range: 43 to 50 ms, the middle half of your last 30 days.\n" +
                "Green: 29 and above. Orange: 22 to 28. Red: 21 and below.",
            hrv(45.0).detail,
        )
    }

    @Test
    fun theBandIsDecidedOnTheNumberAsDisplayed() {
        // 17.46 shows as 17.5, which the chip calls elevated; it must not stay green on the unrounded figure.
        assertEquals(Level.ORANGE, resp(17.46).level)
        assertEquals(Level.GREEN, resp(17.44).level)
    }

    @Test
    fun bloodOxygenUsesOneFixedRangeAndNeedsNoHistory() {
        fun spo2(value: Double) = VivaVitalRange.band("spo2", value, emptyList(), "%", whole)!!
        assertEquals(listOf(Level.GREEN, "within 95 - 100"), spo2(96.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.GREEN, "near 95"), spo2(93.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.ORANGE, "low < 90"), spo2(89.0).let { listOf(it.level, it.chip) })
        assertEquals("Typical range: 95 to 100 %.\nGreen: 90 and above. Orange: 89 and below.", spo2(96.0).detail)
    }

    @Test
    fun aReadingInsideTheTypicalRangeIsNeverFlagged() {
        // Half the nights at 33.0 and half at 36.0: the median-based limit (35.5) falls inside the typical range.
        val spread = List(7) { 33.0 } + List(7) { 36.0 }
        val band = VivaVitalRange.band("skin", 36.0, spread, "°C", oneDecimal)!!
        assertEquals(listOf(Level.GREEN, "within 33.0 - 36.0"), listOf(band.level, band.chip))
    }

    @Test
    fun tooFewNightsOrAnUnknownVitalGivesNoBand() {
        assertNull(VivaVitalRange.band("resp", 16.5, breaths.take(VivaVitalRange.MIN_NIGHTS - 1), "rpm", oneDecimal))
        assertNull(VivaVitalRange.band("steps", 1.0, breaths, "", oneDecimal))
    }

    @Test
    fun quantilesInterpolateBetweenNeighbours() {
        assertEquals(2.5, VivaVitalRange.quantile(listOf(1.0, 2.0, 3.0, 4.0), 0.5), 1e-9)
        assertEquals(1.75, VivaVitalRange.quantile(listOf(1.0, 2.0, 3.0, 4.0), 0.25), 1e-9)
        assertEquals(7.0, VivaVitalRange.quantile(listOf(7.0), 0.75), 1e-9)
    }
}
