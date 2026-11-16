package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepStagerNremHrvTest {
    private fun w(stage: String, rmssd: Double?) = SleepStager.HrvWindow(0L, stage, 100, rmssd)

    @Test
    fun meanCoversLightAndDeepOnly() {
        val windows = List(4) { w("light", 40.0) } + List(4) { w("deep", 50.0) } +
            List(5) { w("rem", 90.0) } + w("wake", 120.0) + w("?", 200.0) + w("light", null)
        assertEquals(45.0, SleepStager.nremMeanRmssd(windows)!!, 1e-9)
    }

    @Test
    fun nullBelowTheMinimumSoTheCallerFallsBack() {
        assertNull(SleepStager.nremMeanRmssd(List(5) { w("light", 40.0) } + List(20) { w("rem", 60.0) }))
        assertNull(SleepStager.nremMeanRmssd(List(30) { w("?", 40.0) }))
        assertNull(SleepStager.nremMeanRmssd(emptyList()))
    }
}
