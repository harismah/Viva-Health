package com.noop.analytics

import com.noop.data.RrInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class RespRateSpectralTest {
    /**
     * Twenty minutes of beats stamped to the whole second, as a 5.0 / MG banks them, with breathing at
     * [bpm] modulating the interval and a slower 6 bpm rhythm on top that the breathing band must ignore.
     */
    private fun night(bpm: Double, start: Long = 1_000_000L): List<RrInterval> {
        val out = ArrayList<RrInterval>()
        var t = 0.0
        while (t < 20 * 60) {
            val rr = 1000.0 + 45.0 * sin(2 * PI * bpm / 60.0 * t) + 60.0 * sin(2 * PI * 6.0 / 60.0 * t)
            t += rr / 1000.0
            out.add(RrInterval(deviceId = "x", ts = start + t.toLong(), rrMs = rr.toInt()))
        }
        return out
    }

    @Test
    fun recoversEachInjectedBreathingRate() {
        for (bpm in listOf(12.0, 16.0, 20.0)) {
            val rr = night(bpm)
            assertEquals("injected $bpm", bpm, SleepStager.respRateSpectral(rr, rr.first().ts, rr.last().ts), 0.5)
        }
    }

    @Test
    fun tooFewBeatsIsNaN() {
        val rr = night(16.0).take(20)
        assertTrue(SleepStager.respRateSpectral(rr, rr.first().ts, rr.last().ts).isNaN())
    }
}
