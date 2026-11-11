package com.noop.ui.viva

import com.noop.data.DailyMetric
import com.noop.data.JournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The journal-to-insights shaping, checked without a device. The effect maths has its own tests. */
class VivaBehaviorInsightsTest {
    private fun day(n: Int) = "2026-10-%02d".format(n)
    private fun answer(n: Int, question: String, yes: Boolean) = JournalEntry("journal", day(n), question, yes)
    private fun metric(n: Int, recovery: Double?) = DailyMetric(deviceId = "strap", day = day(n), recovery = recovery)

    @Test
    fun threeYesDaysAreTalliedButNotPrintedAsAnImpact() {
        val entries = (1..3).map { answer(it, "Late meal", true) } + (4..10).map { answer(it, "Late meal", false) }
        val days = (1..3).map { metric(it, 40.0 + it % 2) } + (4..10).map { metric(it, 80.0 + it % 2) }
        val result = vivaBehaviorInsights(entries, days)
        assertEquals(listOf(VivaBehaviorCount("Late meal", 3, 7)), result.counts)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun aStarterQuestionFoldsIntoTheImportedWordingOnlyWhenTheImportHasIt() {
        val starter = "Did you drink any alcohol?"
        assertEquals("Have any alcoholic drinks?", vivaJournalQuestion(starter, setOf("Have any alcoholic drinks?")))
        assertEquals(starter, vivaJournalQuestion(starter, setOf("Consumed caffeine?")))
        assertEquals("Consumed caffeine?", vivaJournalQuestion("Consumed caffeine?", setOf("Consumed caffeine?")))
    }

    @Test
    fun aLeftoverStarterAnswerFillsADayButNeverOverridesTheImport() {
        val whoop = "Have any alcoholic drinks?"
        val starter = "Did you drink any alcohol?"
        val imported = listOf(JournalEntry("my-whoop", day(1), whoop, true))
        val native = listOf(
            JournalEntry("journal", day(1), starter, false),  // the import already answers this day
            JournalEntry("journal", day(2), starter, false),  // it does not answer this one
            JournalEntry("journal", day(1), "Did you use a sauna?", true),
        )
        val history = vivaJournalHistory(imported, native).map { Triple(it.day, it.question, it.answeredYes) }
        assertEquals(
            listOf(Triple(day(1), "Did you use a sauna?", true), Triple(day(1), whoop, true), Triple(day(2), whoop, false)),
            history,
        )
        // An answer given under the import's own wording is the wearer's latest word and does win.
        val corrected = vivaJournalHistory(imported, listOf(JournalEntry("journal", day(1), whoop, false)))
        assertEquals(listOf(false), corrected.map { it.answeredYes })
    }

    @Test
    fun talliesEveryBehaviourAndRanksOnlyThoseWithEnoughDays() {
        // Alcohol: five yes days at Recovery 40, five no days at 80. Reading: one answer only.
        val entries = (1..5).map { answer(it, "Alcohol", true) } + (6..10).map { answer(it, "Alcohol", false) } +
            answer(1, "Reading", false)
        val days = (1..5).map { metric(it, 40.0 + it % 2) } + (6..10).map { metric(it, 80.0 + it % 2) }

        val result = vivaBehaviorInsights(entries, days)

        assertEquals(listOf(VivaBehaviorCount("Alcohol", 5, 5), VivaBehaviorCount("Reading", 0, 1)), result.counts)
        assertEquals(listOf("Alcohol"), result.effects.map { it.behavior })
        assertEquals(-50.0, result.effects.single().pctChange!!, 1.0)
    }

    @Test
    fun noRecoveryMeansTalliesButNoEffects() {
        val entries = (1..5).map { answer(it, "Alcohol", true) } + (6..10).map { answer(it, "Alcohol", false) }
        val result = vivaBehaviorInsights(entries, (1..10).map { metric(it, null) })
        assertEquals(1, result.counts.size)
        assertTrue(result.effects.isEmpty())
    }
}
