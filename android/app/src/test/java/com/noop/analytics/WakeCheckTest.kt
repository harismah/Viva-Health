package com.noop.analytics

import com.noop.data.SleepSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WakeCheckTest {
    private val zone = ZoneId.of("America/Toronto")
    private fun ts(text: String) = LocalDateTime.parse(text).atZone(zone).toEpochSecond()
    private fun night(from: String, to: String) = SleepSession("my-whoop-noop", ts(from), ts(to))

    @Test
    fun theNightIsTheLongestOfAtLeastThreeHoursEndingThatDay() {
        val nap = night("2026-11-17T19:39", "2026-11-17T21:34")
        val main = night("2026-11-18T04:30", "2026-11-18T07:53")
        val laterDoze = night("2026-11-18T08:00", "2026-11-18T09:40")
        assertEquals(main, WakeCheck.mainNight(listOf(nap, main, laterDoze), LocalDate.of(2026, 11, 18), zone))
        // A nap alone is never the night the question is about.
        assertNull(WakeCheck.mainNight(listOf(nap), LocalDate.of(2026, 11, 17), zone))
    }

    @Test
    fun theWearerCountsAsUpOnlyAfterFortyFiveMinutesOfDataPastTheNight() {
        val main = night("2026-11-18T04:30", "2026-11-18T07:53")
        assertFalse(WakeCheck.awake(main, ts("2026-11-18T08:37")))
        assertTrue(WakeCheck.awake(main, ts("2026-11-18T08:38")))
        assertFalse(WakeCheck.awake(main, null))
    }

    @Test
    fun theNapIsWhatFollowsTheCloseWithoutItsWakeEdges() {
        // A detection that bridged the night and the doze: 0-1000 night, 1000-1300 awake, 1300-3400 asleep, then awake.
        val bridged = DetectedSleep(
            start = 0, end = 4000, efficiency = 0.8, restingHR = 55, avgHRV = 49.0,
            stages = listOf(
                StageSegment(0, 1000, "deep"), StageSegment(1000, 1300, "wake"), StageSegment(1300, 2400, "light"),
                StageSegment(2400, 3400, "rem"), StageSegment(3400, 4000, "wake"),
            ),
        )
        val nap = WakeCheck.afterClose(bridged, cut = 1100)!!
        assertEquals(1300L, nap.start)
        assertEquals(3400L, nap.end)
        assertEquals(listOf("light", "rem"), nap.stages.map { it.stage })
        // A night that ended before the close leaves nothing, and neither does a stir shorter than a nap.
        assertNull(WakeCheck.afterClose(bridged.copy(end = 1000), cut = 1100))
        assertNull(WakeCheck.afterClose(bridged, cut = 3000))
    }

    @Test
    fun aCloseCapsOnlyItsOwnDayAndChangesOnlyThatDaysCacheKey() {
        val cuts = mapOf("2026-11-18" to 500L)
        assertEquals(500L, WakeCheck.capped(cuts, "2026-11-18", 900L))
        assertEquals(400L, WakeCheck.capped(cuts, "2026-11-18", 400L))
        assertEquals(900L, WakeCheck.capped(cuts, "2026-11-17", 900L))
        assertEquals("k|wake=500", WakeCheck.keyed("k", cuts, "2026-11-18"))
        assertEquals("k", WakeCheck.keyed("k", cuts, "2026-11-17"))
    }

    @Test
    fun anOlderClosedDayIsSearchedForNapsAgainOnlyWhenItsMotionChanged() {
        val recent = setOf("2026-11-20", "2026-11-21")
        assertTrue(WakeCheck.searchAgain("2026-11-21", recent, gravityRows = 10, searchedAt = 10.0))
        assertTrue(WakeCheck.searchAgain("2026-11-19", recent, gravityRows = 10, searchedAt = null))
        assertTrue(WakeCheck.searchAgain("2026-11-19", recent, gravityRows = 900, searchedAt = 10.0))
        assertFalse(WakeCheck.searchAgain("2026-11-19", recent, gravityRows = 10, searchedAt = 10.0))
    }
}
