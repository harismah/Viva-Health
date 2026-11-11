package com.noop.ui.viva

import java.text.DateFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The one answer to "when do I go to bed, when do I wake, and is the alarm really set" that every
 * screen showing those facts renders from. Home's Tonight card and the Sleep Planner both take a
 * value of this type, so the two can never print different times or a different armed state.
 * Twin of `VivaAlarmReadout.swift`.
 *
 * [nextFireEpochMs] is the instant the app's own alarm resolver says the strap will buzz, or null
 * when no alarm will arm. A null makes no promise: the wake time falls back to the wearer's base
 * time and the status reads off.
 */
data class VivaAlarmReadout(
    val nextFireEpochMs: Long?,
    val wakeMinutes: Int,
    val bedtimeMinutes: Int,
    val sleepNeedMinutes: Int,
    val wakeText: String,
    val bedtimeText: String,
) {
    val isArmed: Boolean get() = nextFireEpochMs != null
    val statusText: String get() = if (isArmed) "Alarm on" else "Alarm off"
    val modeText: String get() = "Exact time"
    val timeInBedText: String get() = String.format(Locale.US, "%d:%02d", sleepNeedMinutes / 60, sleepNeedMinutes % 60)

    companion object {
        private const val DAY = 1440

        /**
         * [is24Hour] is the app's resolved clock setting (`ClockPrefs.uses24Hour`); null follows
         * the locale's own convention, which is what the iPhone twin does.
         */
        fun resolve(
            nextFireEpochMs: Long?,
            baseWakeMinutes: Int,
            sleepNeedMinutes: Int,
            zone: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault(),
            is24Hour: Boolean? = null,
        ): VivaAlarmReadout {
            val wake = if (nextFireEpochMs != null) {
                val time = Instant.ofEpochMilli(nextFireEpochMs).atZone(zone)
                time.hour * 60 + time.minute
            } else {
                Math.floorMod(baseWakeMinutes, DAY)
            }
            val need = maxOf(sleepNeedMinutes, 0)
            val bedtime = Math.floorMod(wake - need, DAY)
            return VivaAlarmReadout(
                nextFireEpochMs = nextFireEpochMs,
                wakeMinutes = wake,
                bedtimeMinutes = bedtime,
                sleepNeedMinutes = need,
                wakeText = clockText(wake, locale, is24Hour),
                bedtimeText = clockText(bedtime, locale, is24Hour),
            )
        }

        /** A minute of the day as the locale writes a clock time ("10:00 PM", "22:00"). */
        fun clockText(minutes: Int, locale: Locale = Locale.getDefault(), is24Hour: Boolean? = null): String {
            val (time, period) = clockParts(minutes, locale, is24Hour)
            return if (period.isEmpty()) time else "$time $period"
        }

        /** The same clock time split into digits and day period, so a screen can set them in two sizes. */
        fun clockParts(minutes: Int, locale: Locale = Locale.getDefault(), is24Hour: Boolean? = null): Pair<String, String> {
            val minuteOfDay = Math.floorMod(minutes, DAY)
            val hour = minuteOfDay / 60
            val minute = minuteOfDay % 60
            if (is24Hour ?: localeUses24Hour(locale)) {
                return String.format(Locale.US, "%02d:%02d", hour, minute) to ""
            }
            val symbols = DateFormatSymbols.getInstance(locale).amPmStrings
            val period = symbols.getOrNull(if (hour < 12) 0 else 1).orEmpty()
            val hour12 = if (hour % 12 == 0) 12 else hour % 12
            return String.format(Locale.US, "%d:%02d", hour12, minute) to period
        }

        private fun localeUses24Hour(locale: Locale): Boolean {
            val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale,
            )
            return !pattern.contains('a')
        }
    }
}
