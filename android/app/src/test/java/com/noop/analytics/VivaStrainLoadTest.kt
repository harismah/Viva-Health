package com.noop.analytics

import com.noop.data.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

/** The Viva strain curve: a smooth power of heart-rate reserve on a log map (made-up heart rates). */
class VivaStrainLoadTest {

    private val rest = 60.0
    private val max = 190.0

    private fun held(share: Double, minutes: Int): List<HrSample> =
        (0 until minutes).map { HrSample(deviceId = "t", ts = it * 60L, bpm = (rest + share * (max - rest)).toInt()) }

    private fun score(hr: List<HrSample>) = StrainScorer.strain(hr, maxHR = max, restingHR = rest)!!

    @Test
    fun anHourAtFourFifthsOfReserveFollowsTheFittedCurve() {
        val expected = StrainScorer.maxStrain * StrainScorer.vivaLogScale / StrainScorer.whoopMaxStrain *
            ln(1.0 + StrainScorer.vivaLoadScale * 60 * 0.8 * 0.8 * 0.8 * 0.8)
        assertEquals(expected, score(held(0.8, 60)), 0.01)
        assertEquals(70.99, score(held(0.8, 60)), 0.01)
    }

    @Test
    fun strainRisesWithIntensityAndWithTime() {
        val byIntensity = listOf(0.2, 0.4, 0.6, 0.8, 1.0).map { score(held(it, 60)) }
        assertEquals(byIntensity.sorted(), byIntensity)
        assertEquals(byIntensity.size, byIntensity.toSet().size)
        assertTrue(score(held(0.5, 120)) > score(held(0.5, 60)))
    }

    @Test
    fun restScoresNothingAndTheAxisStopsAtItsTop() {
        assertEquals(0.0, score(held(0.0, 60)), 0.0)
        assertEquals(StrainScorer.maxStrain, score(held(1.0, 24 * 60)), 0.0)
    }
}

/** The strain the Viva curve gives a load (minutes x reserve share ^ 4), on the 0 to 100 axis. */
internal fun vivaStrainOfLoad(load: Double): Double =
    StrainScorer.maxStrain * StrainScorer.vivaLogScale / StrainScorer.whoopMaxStrain *
        ln(1.0 + StrainScorer.vivaLoadScale * load)
