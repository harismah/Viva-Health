package com.noop.ui.viva

import org.junit.Assert.assertEquals
import org.junit.Test

class VivaSleepStageStretchesTest {
    @Test
    fun aStageBecomesItsShareOfTheNightWithTouchingStretchesJoined() {
        // Night 0..1000 s: light 0-100 and 100-200 touch (one stretch), light again 800-1100 runs past the wake.
        val json = """[{"start":0,"end":100,"stage":"light"},{"start":100,"end":200,"stage":"light"},""" +
            """{"start":200,"end":800,"stage":"deep"},{"start":800,"end":1100,"stage":"light"}]"""
        assertEquals(listOf(0f..0.2f, 0.8f..1f), VivaSleepDetail.stageStretches(json, "light", 0, 1000))
        assertEquals(listOf(0.2f..0.8f), VivaSleepDetail.stageStretches(json, "deep", 0, 1000))
        assertEquals(emptyList<ClosedFloatingPointRange<Float>>(), VivaSleepDetail.stageStretches(json, "rem", 0, 1000))
        // An imported minute-dict night has no timeline.
        assertEquals(emptyList<ClosedFloatingPointRange<Float>>(), VivaSleepDetail.stageStretches("""{"light":90}""", "light", 0, 1000))
    }
}
