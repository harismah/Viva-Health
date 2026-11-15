package com.noop.ui.viva

import com.noop.ui.DisplayText
import com.noop.ui.carriedCaption
import com.noop.ui.uiString
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Small pure rules that keep two Viva screens from contradicting each other. Twin of
 * `VivaConsistency.swift`; its other two rules live beside their callers
 * (`VivaSleepDetail.nightOffset`, `VivaVitals.summary`). Change both phones together.
 */
internal object VivaConsistency {
    /** Why today's Recovery has no score, most specific cause first. */
    sealed interface RecoveryGap {
        data class Calibrating(val nights: Int) : RecoveryGap
        data object NoSleep : RecoveryGap
        data object NoHrv : RecoveryGap
        data object NotScoredYet : RecoveryGap
    }

    /**
     * A wearer still calibrating is told so; otherwise the screen names the input that is actually
     * missing instead of blaming the baseline.
     */
    fun recoveryGap(calibrationNights: Int?, hasSleepToday: Boolean, hasHrvToday: Boolean): RecoveryGap = when {
        calibrationNights != null -> RecoveryGap.Calibrating(calibrationNights)
        !hasSleepToday -> RecoveryGap.NoSleep
        !hasHrvToday -> RecoveryGap.NoHrv
        else -> RecoveryGap.NotScoredYet
    }

    /**
     * The day a displayed value was carried from, or null when the value is today's own (or there is
     * none). Every screen stamps a carried value with this, so an older reading is never passed off
     * as today's.
     */
    fun carriedDay(todayValue: Double?, carriedFrom: String?): String? = if (todayValue == null) carriedFrom else null

    /** `9 Nov` for a `yyyy-MM-dd` key: the stamp printed where a carried value's baseline would sit. */
    fun dayLabel(dayKey: String, locale: Locale = Locale.getDefault()): String =
        runCatching { LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("d MMM", locale)) }.getOrDefault(dayKey)
}

/**
 * The caption under a carried Sleep or Recovery score (`Last night · 9 Nov`), from the same rule the
 * app's own carried read-outs use, so Home and the detail screen it opens word it identically.
 */
internal fun vivaCarriedNote(dayKey: String): String = when (val text = carriedCaption(dayKey)) {
    is DisplayText.Resource -> uiString(text.id, *text.args.toTypedArray())
    is DisplayText.Dynamic -> text.value
}
