package com.noop.ui.viva

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Health Monitor's one verdict: green all in range, orange any out, red any red band or most out. */
class VivaVitalsOverallTest {
    private fun item(state: VivaVitalState) = VivaVitalItem(
        key = "k", icon = Icons.Outlined.Air, label = "L", shortLabel = "L",
        value = if (state == VivaVitalState.NO_READING) null else "1", unit = "", state = state,
        rangeCaption = null, personalRange = true, about = "", trend = null,
    )

    private fun overall(vararg states: VivaVitalState) = VivaVitals.overall(states.map(::item))

    @Test
    fun followsTheWorstReadingAndTheShareOutOfRange() {
        val ok = VivaVitalState.IN_RANGE
        val out = VivaVitalState.OUT_OF_RANGE
        assertEquals(ok, overall(ok, ok, ok, ok, ok))
        assertEquals(out, overall(ok, ok, ok, out, out)) // 2 of 5 out, as the reference showed it
        assertEquals(VivaVitalState.FAR_OUT_OF_RANGE, overall(ok, ok, out, out, out)) // most out
        assertEquals(VivaVitalState.FAR_OUT_OF_RANGE, overall(ok, ok, ok, ok, VivaVitalState.FAR_OUT_OF_RANGE))
        // Unread vitals neither count against nor for: 1 out of 2 read is not most.
        assertEquals(out, overall(ok, out, VivaVitalState.NO_READING, VivaVitalState.NO_READING, VivaVitalState.NO_READING))
        assertNull(overall(VivaVitalState.NO_READING, VivaVitalState.NO_READING))
    }
}
