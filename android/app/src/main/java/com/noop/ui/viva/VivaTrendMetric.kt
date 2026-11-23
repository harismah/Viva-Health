package com.noop.ui.viva

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.noop.analytics.Baselines
import com.noop.data.DailyMetric
import com.noop.ui.EffortScale
import com.noop.ui.UnitFormatter
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * The per-day metrics the Viva weekly cards and Trend View chart. Each case knows how to read its
 * value off a stored [DailyMetric], how to print it and how to draw it, so a weekly card and the
 * Trend View it opens can never disagree about a day's number. Twin of `VivaTrendMetric.swift`.
 */
enum class VivaTrendMetric(val id: String, val group: Group, val title: String, val unit: String) {
    SLEEP_PERFORMANCE("sleepPerformance", Group.SLEEP, "Sleep Performance", "%"),
    HOURS_OF_SLEEP("hoursOfSleep", Group.SLEEP, "Hours of Sleep", "hr"),
    RESTORATIVE_SLEEP("restorativeSleep", Group.SLEEP, "Restorative Sleep", "hr"),
    SLEEP_EFFICIENCY("sleepEfficiency", Group.SLEEP, "Sleep Efficiency", "%"),
    RECOVERY("recovery", Group.RECOVERY, "Recovery", "%"),
    HRV("hrv", Group.RECOVERY, "Heart Rate Variability", "ms"),
    RESTING_HR("restingHR", Group.RECOVERY, "Resting Heart Rate", "bpm"),
    RESPIRATORY_RATE("respiratoryRate", Group.RECOVERY, "Respiratory Rate", "rpm"),
    BLOOD_OXYGEN("bloodOxygen", Group.RECOVERY, "Blood Oxygen", "%"),
    STRAIN("strain", Group.STRAIN, "Strain", ""),
    CALORIES("calories", Group.STRAIN, "Calories", ""),
    STEPS("steps", Group.STRAIN, "Steps", ""),
    // The rest read the dashboard's extra series ([VivaDashboard.series]) as well as the day row.
    TIME_IN_BED("timeInBed", Group.SLEEP, "Time in Bed", "hr"),
    RESTORATIVE_PERCENT("restorativePercent", Group.SLEEP, "Restorative Sleep (%)", "%"),
    SLEEP_CONSISTENCY("sleepConsistency", Group.SLEEP, "Sleep Consistency", "%"),
    SLEEP_NEEDED("sleepNeeded", Group.SLEEP, "Sleep Needed", "hr"),
    SLEEP_DEBT("sleepDebt", Group.SLEEP, "Sleep Debt", "hr"),
    AVERAGE_HR("averageHR", Group.STRAIN, "Average Heart Rate", "bpm"),
    ZONES_1_3("zones13", Group.STRAIN, "HR Zones 1-3 (Weekly)", "hr"),
    ZONES_4_5("zones45", Group.STRAIN, "HR Zones 4-5 (Weekly)", "hr"),
    ZONES_ALL("zonesAll", Group.STRAIN, "HR Zones All (Weekly)", "hr"),
    STRENGTH_TIME("strengthTime", Group.STRAIN, "Strength Activity Time", "hr"),
    VO2_MAX("vo2Max", Group.FITNESS, "VO₂ Max", "ml/kg/min"),
    LEAN_BODY_MASS("leanBodyMass", Group.FITNESS, "Lean Body Mass", "%"),
    BODY_FAT("bodyFat", Group.FITNESS, "Body Fat", "%"),
    WEIGHT("weight", Group.FITNESS, "Weight", "kg");

    enum class Group(val title: String) { STRAIN("Strain"), RECOVERY("Recovery"), SLEEP("Sleep"), FITNESS("Fitness") }

    /** A per-day series keyed by "yyyy-MM-dd"; null where the day has no value. */
    fun interface Series { fun at(day: String): Double? }

    /** One day of a series; [value] is null when nothing was recorded, never a stand-in zero. */
    data class Point(val key: String, val date: LocalDate, val value: Double?)

    val icon: ImageVector
        get() = when (this) {
            SLEEP_PERFORMANCE -> Icons.Outlined.NightsStay
            HOURS_OF_SLEEP -> Icons.Outlined.Schedule
            RESTORATIVE_SLEEP -> Icons.Outlined.AutoAwesome
            SLEEP_EFFICIENCY -> Icons.Outlined.Bed
            RECOVERY -> Icons.Outlined.Sync
            HRV -> Icons.Outlined.MonitorHeart
            RESTING_HR -> Icons.Outlined.FavoriteBorder
            RESPIRATORY_RATE -> Icons.Outlined.Air
            BLOOD_OXYGEN -> Icons.Outlined.WaterDrop
            STRAIN -> Icons.Outlined.Bolt
            CALORIES -> Icons.Outlined.LocalFireDepartment
            STEPS -> Icons.AutoMirrored.Outlined.DirectionsWalk
            TIME_IN_BED -> Icons.Outlined.Bed
            RESTORATIVE_PERCENT -> Icons.Outlined.AutoAwesome
            SLEEP_CONSISTENCY -> Icons.Outlined.Schedule
            SLEEP_NEEDED -> Icons.Outlined.NightsStay
            SLEEP_DEBT -> Icons.Outlined.EventBusy
            AVERAGE_HR -> Icons.Outlined.FavoriteBorder
            ZONES_1_3, ZONES_4_5, ZONES_ALL -> Icons.Outlined.Timer
            STRENGTH_TIME -> Icons.Outlined.FitnessCenter
            VO2_MAX -> Icons.Outlined.Speed
            LEAN_BODY_MASS, BODY_FAT, WEIGHT -> Icons.Outlined.MonitorWeight
        }

    val color: Color
        get() = when (group) {
            Group.SLEEP -> if (this == RESTORATIVE_SLEEP) VivaStyle.stageREM else VivaStyle.sleep
            Group.RECOVERY -> if (this == RECOVERY) VivaStyle.textPrimary else VivaStyle.link
            Group.STRAIN -> VivaStyle.strain
            Group.FITNESS -> VivaStyle.link
        }

    /** Vitals read as a level (points on a line); totals and scores read as bars. */
    val isLevel: Boolean get() = this == HRV || this == RESTING_HR || this == RESPIRATORY_RATE || this == BLOOD_OXYGEN ||
        this == AVERAGE_HR || this == VO2_MAX || this == LEAN_BODY_MASS || this == BODY_FAT || this == WEIGHT

    /** Level metrics chart against their own range rather than from zero, so a 2 bpm move is visible. */
    val chartsFromZero: Boolean get() = !isLevel

    /** How a week card draws this metric: a dot against the scale for a level, a bar otherwise. */
    val style: VivaWeekBarCard.Style get() = if (isLevel) VivaWeekBarCard.Style.DOT else VivaWeekBarCard.Style.BAR

    /**
     * The day's value. Sleep performance is not a column on the day row on Android, so its series
     * is passed in ([sleepPerformance], keyed by day) by whoever loaded it; so are the dashboard's
     * [extra] series, which only Home's dashboard and the Trend View read.
     */
    fun value(day: DailyMetric, sleepPerformance: Map<String, Double> = emptyMap(), extra: Map<VivaTrendMetric, Series> = emptyMap()): Double? = when (this) {
        SLEEP_PERFORMANCE -> sleepPerformance[day.day]
        HOURS_OF_SLEEP -> day.totalSleepMin
        RESTORATIVE_SLEEP -> if (day.deepMin == null && day.remMin == null) null else (day.deepMin ?: 0.0) + (day.remMin ?: 0.0)
        SLEEP_EFFICIENCY -> day.efficiency?.let { if (it <= 1) it * 100 else it }
        RECOVERY -> day.recovery
        HRV -> day.avgHrv
        RESTING_HR -> day.restingHr?.toDouble()
        RESPIRATORY_RATE -> day.respRateBpm
        BLOOD_OXYGEN -> day.spo2Pct
        STRAIN -> day.strain?.let { UnitFormatter.effortValue(it, EffortScale.WHOOP) }
        CALORIES -> day.activeKcalEst
        STEPS -> day.steps?.toDouble()
        TIME_IN_BED -> day.totalSleepMin?.let { t -> SLEEP_EFFICIENCY.value(day)?.takeIf { it > 0 }?.let { t / (it / 100) } }
        RESTORATIVE_PERCENT -> day.totalSleepMin?.takeIf { it > 0 }?.let { total -> RESTORATIVE_SLEEP.value(day)?.let { it / total * 100.0 } }
        else -> extra[this]?.at(day.day)
    }

    fun text(value: Double?, locale: Locale = Locale.getDefault()): String {
        if (value == null || !value.isFinite()) return "--"
        val whole = VivaScoreRing.roundHalfAwayFromZero(value)
        return when (this) {
            HOURS_OF_SLEEP, RESTORATIVE_SLEEP, TIME_IN_BED, SLEEP_NEEDED, SLEEP_DEBT,
            ZONES_1_3, ZONES_4_5, ZONES_ALL, STRENGTH_TIME -> clock(value)
            RESPIRATORY_RATE, STRAIN, WEIGHT -> String.format(locale, "%.1f", value)
            LEAN_BODY_MASS, BODY_FAT -> String.format(locale, "%.1f%%", value)
            STEPS, CALORIES -> NumberFormat.getIntegerInstance(locale).format(whole)
            SLEEP_PERFORMANCE, SLEEP_EFFICIENCY, RECOVERY, BLOOD_OXYGEN, RESTORATIVE_PERCENT, SLEEP_CONSISTENCY -> "$whole%"
            HRV, RESTING_HR, AVERAGE_HR, VO2_MAX -> whole.toString()
        }
    }

    /** Colour for one day's mark; Recovery carries its band colour. */
    fun markColor(value: Double): Color = if (this == RECOVERY) VivaStyle.recoveryColor(value) else color

    val summary: String
        get() = when (this) {
            SLEEP_PERFORMANCE -> "How much of the sleep you needed you actually got, adjusted for how efficiently you slept."
            HOURS_OF_SLEEP -> "Time asleep each night, not counting time awake in bed."
            RESTORATIVE_SLEEP -> "Deep and REM sleep together, the stages that do most of the physical and mental repair."
            SLEEP_EFFICIENCY -> "The share of your time in bed that you spent asleep."
            RECOVERY -> "How ready your body is for load today, from last night's heart-rate variability, resting heart rate and sleep."
            HRV -> "The beat-to-beat variation in your heart rhythm during sleep. Compare it with your own baseline, not with other people."
            RESTING_HR -> "Your heart rate at its calmest during sleep. It usually falls as fitness improves and rises with fatigue or illness."
            RESPIRATORY_RATE -> "Breaths per minute during sleep. It is normally very stable, so a clear change is worth noticing."
            BLOOD_OXYGEN -> "The oxygen saturation of your blood during sleep."
            STRAIN -> "The cardiovascular load you built up over the day, on a 0 to 21 scale."
            CALORIES -> "Active energy estimated from your heart rate through the day."
            STEPS -> "Steps counted through the day. Regular walking is one of the simplest habits linked to better heart and metabolic health."
            TIME_IN_BED -> "Everything from lying down to getting up, asleep or not. The gap to Hours of Sleep is the time you lay awake."
            RESTORATIVE_PERCENT -> "The share of your sleep spent in deep and REM sleep, the stages where most physical and mental repair happens."
            SLEEP_CONSISTENCY -> "How closely your sleep and wake times match the previous nights. A steady rhythm helps your body clock and your recovery."
            SLEEP_NEEDED -> "How much sleep tonight would cover you: a base amount plus debt carried from recent short nights, less naps."
            SLEEP_DEBT -> "Sleep you needed but did not get on recent nights. It is added to tonight's need until you sleep it back."
            AVERAGE_HR -> "Your mean heart rate across the whole day, sleep included. It rises with activity, stress, heat, alcohol or illness."
            ZONES_1_3 -> "Time your logged activities spent in the light to moderate heart-rate zones over the last 7 days: your aerobic base."
            ZONES_4_5 -> "Time your logged activities spent in the hard heart-rate zones over the last 7 days. Short doses build top-end fitness."
            ZONES_ALL -> "All the time your logged activities spent in any heart-rate zone over the last 7 days."
            STRENGTH_TIME -> "Time spent in strength activities over the last 7 days. Lifting keeps muscle and bone strong as you age."
            VO2_MAX -> "An estimate of the most oxygen your body can use during hard effort. Higher means fitter heart and lungs."
            LEAN_BODY_MASS -> "Everything that is not fat (muscle, bone, organs and water) as a share of your weight, from your body-fat readings."
            BODY_FAT -> "The share of your weight that is fat, from your body-fat readings. It is Lean Body Mass seen the other way round."
            WEIGHT -> "Your latest body weight, from Health Connect or your profile."
        }

    /** One point per calendar day for the [count] days ending [endingDaysAgo] days before [today]. */
    fun points(
        days: List<DailyMetric>,
        count: Int,
        today: LocalDate,
        endingDaysAgo: Int = 0,
        sleepPerformance: Map<String, Double> = emptyMap(),
        extra: Map<VivaTrendMetric, Series> = emptyMap(),
    ): List<Point> {
        val byDay = days.associateBy { it.day }
        return (count - 1 downTo 0).map { back ->
            val date = today.minusDays((back + endingDaysAgo).toLong())
            val key = date.toString()
            // A dashboard series (a weekly sum, a carried weight) can hold a value on a day without its own row.
            Point(key, date, byDay[key]?.let { value(it, sleepPerformance, extra) } ?: extra[this]?.at(key))
        }
    }

    /** The seven days ending on [today] as week-card columns, the last one marked as today. */
    fun weekDays(
        days: List<DailyMetric>,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
        sleepPerformance: Map<String, Double> = emptyMap(),
    ): List<VivaWeekBarCard.Day> {
        val series = points(days, 7, today, sleepPerformance = sleepPerformance)
        val floor = if (chartsFromZero) 0.0 else levelFloor(series.mapNotNull { it.value })
        return series.mapIndexed { index, point ->
            VivaWeekBarCard.Day(
                label = point.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                date = point.date.dayOfMonth,
                // A reading that sits on the floor still gets a sliver, so it is never drawn as a gap.
                segments = point.value?.let { listOf(VivaWeekBarCard.Segment(maxOf(it - floor, 0.0001), markColor(it))) } ?: emptyList(),
                valueText = point.value?.let { text(it, locale) },
                isToday = index == series.lastIndex,
            )
        }
    }

    /**
     * Mean over the last 30 recorded days before [dayKey], or null when none of them carries this
     * metric. The same window the home dashboard rows compare against.
     */
    fun baseline(days: List<DailyMetric>, dayKey: String, sleepPerformance: Map<String, Double> = emptyMap()): Double? {
        val prior = days.filter { it.day < dayKey }.takeLast(30).mapNotNull { value(it, sleepPerformance) }
        return if (prior.isEmpty()) null else prior.sum() / prior.size
    }

    /** A resolved figure: the value and, when it was carried from an earlier day, that day's key. */
    data class Reading(val value: Double?, val carriedDay: String?)

    /**
     * The day's own value, or for a vital the freshest earlier one still inside the carry window,
     * with the day it came from. The window is the one the Health Monitor reads through, so Home,
     * the detail screens and the Health Monitor give one answer; totals and scores are never carried.
     * Twin of `VivaTrendMetric.todayOrCarried`.
     */
    fun todayOrCarried(
        days: List<DailyMetric>,
        today: DailyMetric?,
        todayKey: String,
        sleepPerformance: Map<String, Double> = emptyMap(),
    ): Reading {
        today?.let { value(it, sleepPerformance) }?.let { return Reading(it, null) }
        if (!isLevel) return Reading(null, null)
        val row = Baselines.freshestCarried(days.filter { it.day <= todayKey && value(it) != null }.map { it.day to it }, todayKey)?.second
        return Reading(row?.let { value(it) }, row?.day?.takeIf { it != todayKey })
    }

    companion object {
        fun fromId(id: String?): VivaTrendMetric? = entries.firstOrNull { it.id == id }

        /** The trend metric behind one of the app's `vital_detail` keys, or null when it has none. */
        fun forVitalKey(key: String): VivaTrendMetric? = when (key) {
            "hrv" -> HRV
            "rhr" -> RESTING_HR
            "spo2" -> BLOOD_OXYGEN
            "resp" -> RESPIRATORY_RATE
            "steps_est" -> STEPS
            "active_kcal" -> CALORIES
            "strain" -> STRAIN
            "recovery" -> RECOVERY
            "rest" -> SLEEP_PERFORMANCE
            "asleep" -> HOURS_OF_SLEEP
            "restorative_min" -> RESTORATIVE_SLEEP
            "efficiency" -> SLEEP_EFFICIENCY
            "in_bed" -> TIME_IN_BED
            "restorative_pct" -> RESTORATIVE_PERCENT
            "consistency" -> SLEEP_CONSISTENCY
            "need" -> SLEEP_NEEDED
            "debt" -> SLEEP_DEBT
            "avg_hr" -> AVERAGE_HR
            "zones13" -> ZONES_1_3
            "zones45" -> ZONES_4_5
            "zones_all" -> ZONES_ALL
            "strength" -> STRENGTH_TIME
            "vo2max" -> VO2_MAX
            "lean_mass" -> LEAN_BODY_MASS
            "body_fat" -> BODY_FAT
            "weight" -> WEIGHT
            else -> null
        }

        /** The sentence above a Trend View chart: the period's average, or that there is none yet. */
        fun periodLine(metric: VivaTrendMetric, average: Double?, noun: String, locale: Locale = Locale.getDefault()): String {
            val span = if (noun == "6 months") "these 6 months" else "this $noun"
            if (average == null) return "Not enough ${metric.title} readings in $span yet. Keep wearing your strap to see the trend."
            val unit = metric.unit.takeIf { it.isNotEmpty() && it != "%" && it != "hr" }?.let { " $it" } ?: ""
            return "Your average ${metric.title} over $span is ${metric.text(average, locale)}$unit."
        }

        /** Minutes as `7:32`. */
        fun clock(minutes: Double): String {
            val total = VivaScoreRing.roundHalfAwayFromZero(minutes)
            return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
        }

        /** A baseline a little under the lowest value, so level charts use their vertical room. */
        fun levelFloor(values: List<Double>): Double {
            val low = values.minOrNull() ?: return 0.0
            val high = values.maxOrNull() ?: return 0.0
            val span = maxOf(high - low, maxOf(high * 0.1, 1.0))
            return maxOf(low - span, 0.0)
        }

        /** Mean of the recorded values, or null with fewer than two: one day is not a trend. */
        fun average(points: List<Point>): Double? {
            val values = points.mapNotNull { it.value }
            return if (values.size < 2) null else values.sum() / values.size
        }

        /**
         * Change of this period's average against the one before it, in whole percent, or null
         * when either side is missing or the earlier one is zero.
         */
        fun changePercent(current: Double?, previous: Double?): Int? {
            if (current == null || previous == null || previous == 0.0) return null
            return VivaScoreRing.roundHalfAwayFromZero((current - previous) / kotlin.math.abs(previous) * 100).toInt()
        }
    }
}
