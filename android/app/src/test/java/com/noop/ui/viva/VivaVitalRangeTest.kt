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

    // 16.0, 16.1 ... 17.3: quartiles 16.325 and 16.975, median 16.65, so orange above 17.7 and red above 18.2.
    private val breaths = (160..173).map { it / 10.0 }
    private fun resp(value: Double) = VivaVitalRange.band("resp", value, breaths, "rpm", oneDecimal)!!

    // 40 ... 53: quartiles 43.25 and 49.75, median 46.5, so green from 30 and red under 23.25.
    private val variability = (40..53).map { it.toDouble() }
    private fun hrv(value: Double) = VivaVitalRange.band("hrv", value, variability, "ms", whole)!!

    @Test
    fun aHighIsBadVitalMovesThroughEveryChipAndBand() {
        assertEquals(listOf(Level.GREEN, "within 16.3 - 17.0"), resp(16.5).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.GREEN, "low < 16.3"), resp(16.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.GREEN, "near 17.0"), resp(17.7).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.ORANGE, "elevated > 17.7"), resp(17.8).let { listOf(it.level, it.chip) })
        assertEquals(Level.ORANGE, resp(18.2).level)
        assertEquals(Level.RED, resp(18.3).level)
        assertEquals(
            "Your respiratory rate of 16.5 rpm is within your typical range of 16.3 rpm to 17.0 rpm.\n\n" +
                "Your ranges are worked out from your last 30 days:\n" +
                "Green = 17.7 rpm and below\nOrange = 17.8 rpm to 18.2 rpm\nRed = 18.3 rpm and above",
            resp(16.5).detail,
        )
    }

    @Test
    fun aLowIsBadVitalIsJudgedTheOtherWayRound() {
        assertEquals(listOf(Level.GREEN, "elevated > 50"), hrv(55.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.GREEN, "within 43 - 50"), hrv(43.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.GREEN, "near 43"), hrv(30.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.ORANGE, "low < 30"), hrv(29.0).let { listOf(it.level, it.chip) })
        assertEquals(Level.ORANGE, hrv(24.0).level)
        assertEquals(Level.RED, hrv(23.0).level)
        assertEquals(
            "Your HRV of 45 ms is within your typical range of 43 ms to 50 ms.\n\n" +
                "Your ranges are worked out from your last 30 days:\n" +
                "Green = 30 ms and above\nOrange = 24 ms to 29 ms\nRed = 23 ms and below",
            hrv(45.0).detail,
        )
    }

    @Test
    fun theBandIsDecidedOnTheNumberAsDisplayed() {
        // 17.76 shows as 17.8, which the chip calls elevated; it must not stay green on the unrounded figure.
        assertEquals(Level.ORANGE, resp(17.76).level)
        assertEquals(Level.GREEN, resp(17.74).level)
    }

    @Test
    fun reproducesTheReferenceBandsOfOneWearersNight() {
        // 17 Nov 2026: medians 17.0 rpm, 56 bpm, 47 ms and 34.14 C gave these bands in the reference app.
        fun bands(key: String, median: Double, value: Double, format: (Double) -> String) =
            VivaVitalRange.band(key, value, List(14) { median }, "", format)!!.let { it.level to it.detail.substringAfter(":\n") }
        assertEquals(Level.GREEN to "Green = 18.0 and below\nOrange = 18.1 to 18.5\nRed = 18.6 and above", bands("resp", 17.0, 16.9, oneDecimal))
        assertEquals(Level.ORANGE to "Green = 66 and below\nOrange = 67 to 70\nRed = 71 and above", bands("rhr", 56.0, 67.0, whole))
        assertEquals(Level.ORANGE to "Green = 31 and above\nOrange = 24 to 30\nRed = 23 and below", bands("hrv", 47.0, 29.0, whole))
        assertEquals(Level.GREEN to "Green = 35.2 and below\nOrange = 35.3 to 35.6\nRed = 35.7 and above", bands("skin", 34.14, 34.0, oneDecimal))
        assertEquals("elevated > 67", VivaVitalRange.band("rhr", 67.0, List(14) { 56.0 }, "bpm", whole)!!.chip)
        assertEquals("low < 30", VivaVitalRange.band("hrv", 29.0, List(14) { 47.0 }, "ms", whole)!!.chip)
    }

    @Test
    fun skinIsExplainedInDegreesWhileItsChipStaysADeviation() {
        val band = VivaVitalRange.band("skin", -0.2, List(14) { 0.0 }, "°C", oneDecimal, detailShift = 34.2)!!
        assertEquals("low < 0.0", band.chip)
        assertEquals("Your skin temperature of 34.0 °C is below your typical range of 34.2 °C to 34.2 °C.", band.detail.substringBefore("\n\n"))
    }

    @Test
    fun bloodOxygenUsesOneFixedRangeAndNeedsNoHistory() {
        fun spo2(value: Double) = VivaVitalRange.band("spo2", value, emptyList(), "%", whole)!!
        assertEquals(listOf(Level.GREEN, "within 95 - 100"), spo2(96.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.GREEN, "near 95"), spo2(93.0).let { listOf(it.level, it.chip) })
        assertEquals(listOf(Level.ORANGE, "low < 90"), spo2(89.0).let { listOf(it.level, it.chip) })
        assertEquals(
            "Your blood oxygen of 96 % is within the typical range of 95 % to 100 %.\n\n" +
                "These ranges are the same for everyone:\nGreen = 90 % and above\nOrange = 89 % and below",
            spo2(96.0).detail,
        )
    }

    @Test
    fun aReadingInsideTheTypicalRangeIsNeverFlagged() {
        // Half the nights at 33.0 and half at 36.0: the median-based limit (35.6) falls inside the typical range.
        val spread = List(7) { 33.0 } + List(7) { 36.0 }
        val band = VivaVitalRange.band("skin", 36.0, spread, "°C", oneDecimal)!!
        assertEquals(listOf(Level.GREEN, "within 33.0 to 36.0"), listOf(band.level, band.chip))
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
