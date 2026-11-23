package com.noop.ui.viva

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.noop.analytics.StrainScorer
import com.noop.data.DailyMetric
import com.noop.data.WorkoutRow
import com.noop.ui.AppViewModel
import com.noop.ui.EffortScale
import com.noop.ui.UnitFormatter
import com.noop.ui.WorkoutEditing
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.json.JSONObject

/**
 * What the reference app's My Dashboard rows read beyond the daily rows, each keyed by day
 * ("yyyy-MM-dd"). [activityZoneMin] holds, per day, the minutes logged activities spent in zones 0 to 5
 * (zone 0 = under 50 %HRR): the reference's weekly zone rows count activity time only (on this wearer's
 * week its 1:03 in zones 1-3 and 1:05 in all zones are exactly a 43-minute lift and a 22-minute walk).
 */
data class VivaDashboardExtras(
    val sleepPerformance: Map<String, Double> = emptyMap(),
    val sleepConsistency: Map<String, Double> = emptyMap(),
    val sleepNeedMin: Map<String, Double> = emptyMap(),
    val sleepDebtMin: Map<String, Double> = emptyMap(),
    val vo2Max: Map<String, Double> = emptyMap(),
    val weightKg: Map<String, Double> = emptyMap(),
    val bodyFatPct: Map<String, Double> = emptyMap(),
    val avgHr: Map<String, Double> = emptyMap(),
    val activityZoneMin: Map<String, List<Double>> = emptyMap(),
    val strengthMin: Map<String, Double> = emptyMap(),
    /** The Settings choice to show Body Fat in Lean Body Mass's row (NoopPrefs.vivaShowBodyFat). */
    val showBodyFat: Boolean = false,
)

/**
 * Loads [VivaDashboardExtras] for the [back] days ending [today]: 37 for Home (30-day baselines of weekly
 * sums), about six months for the Trend View.
 */
suspend fun loadVivaDashboard(viewModel: AppViewModel, days: List<DailyMetric>, today: LocalDate, zone: ZoneId, maxHr: Double, back: Long = 37): VivaDashboardExtras {
    val repo = viewModel.repo
    val strap = viewModel.activeStrapId
    val fromDay = today.minusDays(back)
    val from = fromDay.toString()
    val to = today.toString()
    suspend fun series(key: String, source: String = "my-whoop") = runCatching {
        repo.resolvedSeries(key, source, from, to, strapDeviceId = strap).values.toMap()
    }.getOrDefault(emptyMap())
    val fromTs = fromDay.atStartOfDay(zone).toEpochSecond()
    val toTs = today.plusDays(1).atStartOfDay(zone).toEpochSecond()
    fun dayOf(ts: Long) = Instant.ofEpochSecond(ts).atZone(zone).toLocalDate().toString()
    val buckets = runCatching { repo.hrBucketsUnion(strap, fromTs, toTs, 60L) }.getOrDefault(emptyList())
    // Average HR per physiological day, sleep onset to the next onset (now for the open day), as the
    // reference counts it; a day without a stored onset keeps its calendar day.
    val onsets = series("day_cycle_onset_ts").mapValues { it.value.toLong() }.toSortedMap()
    val calendar = buckets.groupBy { dayOf(it.bucket) }.mapValues { (_, b) -> b.sumOf { it.avgBpm } / b.size }
    val avgHr = calendar.keys.associateWith { day ->
        val start = onsets[day]
        val end = onsets.tailMap(day).entries.firstOrNull { it.key > day }?.value ?: (System.currentTimeMillis() / 1000)
        val inside = if (start == null) emptyList() else buckets.filter { it.bucket >= start && it.bucket < end }
        if (inside.isEmpty()) calendar.getValue(day) else inside.sumOf { it.avgBpm } / inside.size
    }
    val workouts = runCatching {
        WorkoutEditing.dedupCrossSource(
            repo.workoutsUnion(strap, fromTs, toTs) + repo.workouts("apple-health", fromTs, toTs) +
                repo.workouts("health-connect", fromTs, toTs),
        )
    }.getOrDefault(emptyList())
    val zones = HashMap<String, MutableList<Double>>()
    val strength = HashMap<String, Double>()
    val restingByDay = days.associate { it.day to it.restingHr?.toDouble() }
    for (w in workouts) {
        val day = dayOf(w.startTs)
        val minutes = VivaDashboard.zoneMinutes(w, buckets, restingByDay[day] ?: StrainScorer.defaultRestingHR, maxHr)
        val acc = zones.getOrPut(day) { MutableList(6) { 0.0 } }
        for (i in 0..5) acc[i] += minutes[i]
        if (VivaDashboard.isStrength(w.sport)) strength[day] = (strength[day] ?: 0.0) + minutes.sum()
    }
    return VivaDashboardExtras(
        sleepPerformance = series("sleep_performance"),
        sleepConsistency = series("sleep_consistency"),
        sleepNeedMin = series("sleep_need_min"),
        sleepDebtMin = series("sleep_debt_min"),
        vo2Max = series("vo2max_est"),
        weightKg = series("weight", "apple-health"),
        bodyFatPct = series("body_fat", "apple-health"),
        avgHr = avgHr,
        activityZoneMin = zones,
        strengthMin = strength,
    )
}

/** The reference app's My Dashboard: 8 rows above the stress and strain cards, 16 below them. */
object VivaDashboard {

    /** Sports whose time counts as strength activity. */
    fun isStrength(sport: String): Boolean =
        sport.lowercase(Locale.US).let { s -> listOf("weight", "strength", "lift", "functional", "crossfit").any { it in s } }

    /**
     * Minutes [w] spent in zones 0 to 5 (%HRR: under 50, 50-60, ..., 90+). An imported session's own
     * zone shares win; otherwise the strap's one-minute means inside the session are banded.
     */
    fun zoneMinutes(w: WorkoutRow, buckets: List<com.noop.data.HrBucket>, resting: Double, maxHr: Double): List<Double> {
        val total = (w.durationS ?: (w.endTs - w.startTs).toDouble()) / 60.0
        w.zonesJSON?.let { json ->
            runCatching {
                val o = JSONObject(json)
                val shares = (1..5).map { o.optDouble("zone$it", 0.0) / 100.0 }
                return listOf(total * (1.0 - shares.sum()).coerceAtLeast(0.0)) + shares.map { it * total }
            }
        }
        val out = MutableList(6) { 0.0 }
        val inside = buckets.filter { it.bucket >= w.startTs && it.bucket < w.endTs }
        if (inside.isEmpty()) return out.also { it[0] = total }
        val reserve = (maxHr - resting).coerceAtLeast(1.0)
        for (b in inside) {
            val pct = (b.avgBpm - resting) / reserve
            out[if (pct < 0.5) 0 else minOf(5, ((pct - 0.5) / 0.1).toInt() + 1)] += total / inside.size
        }
        return out
    }

    /**
     * The dashboard rows that are not plain day-row columns, as per-day series. Home's rows and the Trend
     * View they open both read these, so a card and its chart cannot disagree. Weekly rows are the sum of
     * the 7 days ending on the day; body readings carry the latest one. Days before the first day with any
     * data ([firstDay] or the first logged activity) are empty rather than a zero week.
     */
    fun series(x: VivaDashboardExtras, firstDay: String?): Map<VivaTrendMetric, VivaTrendMetric.Series> {
        val start = listOfNotNull(firstDay, x.activityZoneMin.keys.minOrNull(), x.strengthMin.keys.minOrNull()).minOrNull()
        fun latest(map: Map<String, Double>) = VivaTrendMetric.Series { d -> map.filterKeys { it <= d }.maxByOrNull { it.key }?.value }
        fun week(map: Map<String, Double>) = VivaTrendMetric.Series { d ->
            if (start == null || d < start) null
            else LocalDate.parse(d).let { end -> (0L..6L).sumOf { map[end.minusDays(it).toString()] ?: 0.0 } }
        }
        fun zones(range: IntRange) = x.activityZoneMin.mapValues { (_, z) -> range.sumOf { z[it] } }
        fun daily(map: Map<String, Double>) = VivaTrendMetric.Series { map[it] }
        val bodyFat = latest(x.bodyFatPct)
        return mapOf(
            VivaTrendMetric.SLEEP_CONSISTENCY to daily(x.sleepConsistency),
            VivaTrendMetric.SLEEP_NEEDED to daily(x.sleepNeedMin),
            VivaTrendMetric.SLEEP_DEBT to daily(x.sleepDebtMin),
            VivaTrendMetric.AVERAGE_HR to daily(x.avgHr),
            VivaTrendMetric.ZONES_1_3 to week(zones(1..3)),
            VivaTrendMetric.ZONES_4_5 to week(zones(4..5)),
            VivaTrendMetric.ZONES_ALL to week(zones(0..5)),
            VivaTrendMetric.STRENGTH_TIME to week(x.strengthMin),
            VivaTrendMetric.VO2_MAX to latest(x.vo2Max),
            VivaTrendMetric.LEAN_BODY_MASS to VivaTrendMetric.Series { d -> bodyFat.at(d)?.let { 100.0 - it } },
            VivaTrendMetric.BODY_FAT to bodyFat,
            VivaTrendMetric.WEIGHT to latest(x.weightKg),
        )
    }

    fun rows(input: VivaHomeInput, x: VivaDashboardExtras, locale: Locale): Pair<List<VivaHomeData.Metric>, List<VivaHomeData.Metric>> {
        val dayKey = input.selectedDayKey
        val byDay = input.days.associateBy { it.day }
        val prior = (1L..30L).map { input.selectedDay.minusDays(it).toString() }
        fun daily(field: (DailyMetric) -> Double?): (String) -> Double? =
            { d -> (if (d == dayKey) input.metric ?: byDay[d] else byDay[d])?.let(field) }
        val extra = series(x, input.days.minOfOrNull { it.day })
        fun of(metric: VivaTrendMetric): (String) -> Double? = { d -> extra.getValue(metric).at(d) }
        fun trend(metric: VivaTrendMetric) = daily { metric.value(it) }

        val whole: (Double?) -> String = { v -> v?.let { VivaScoreRing.roundHalfAwayFromZero(it).toString() } ?: "--" }
        val grouped: (Double?) -> String = { v -> v?.let { NumberFormat.getIntegerInstance(locale).format(VivaScoreRing.roundHalfAwayFromZero(it)) } ?: "--" }
        val one: (Double?) -> String = { v -> v?.let { String.format(locale, "%.1f", it) } ?: "--" }
        val clock: (Double?) -> String = { v -> v?.let { VivaTrendMetric.clock(it) } ?: "--" }

        /** A row with today's value and the mean of the 30 days before (weekly rows: of the weekly sums). */
        fun row(id: String, icon: ImageVector, label: String, value: (String) -> Double?, format: (Double?) -> String,
                unit: String? = null, today: Double? = null, carry: VivaTrendMetric? = null): VivaHomeData.Metric {
            // Today, a vital with no reading of its own shows the one the Health Monitor shows, stamped with its day.
            val reading = carry?.takeIf { input.dayOffset == 0 }?.todayOrCarried(input.days, input.metric, dayKey)
            val shown = reading?.value ?: today ?: value(dayKey)
            val stamp = reading?.carriedDay
            val past = prior.mapNotNull(value)
            val base = if (past.isEmpty()) null else past.average()
            return VivaHomeData.Metric(
                id = id, icon = icon, label = label, value = format(shown), unit = unit.takeIf { shown != null },
                baseline = stamp?.let { VivaConsistency.dayLabel(it, locale) } ?: base?.let(format),
                // Every row opens its Trend View (VivaTrendMetric.forVitalKey maps each id).
                trend = if (stamp != null) VivaTrend.FLAT else VivaHomeMapper.trend(shown, base), opens = true,
            )
        }

        val strainNow = input.effort?.let { UnitFormatter.effortValue(it, EffortScale.WHOOP) }

        val top = listOf(
            row("hrv", Icons.Outlined.MonitorHeart, "Heart Rate Variability", daily { it.avgHrv }, whole, carry = VivaTrendMetric.HRV),
            row("rhr", Icons.Outlined.FavoriteBorder, "Resting Heart Rate", daily { it.restingHr?.toDouble() }, whole, carry = VivaTrendMetric.RESTING_HR),
            row("steps_est", Icons.AutoMirrored.Outlined.DirectionsWalk, "Steps", daily { it.steps?.toDouble() }, grouped),
            row("zones13", Icons.Outlined.Timer, "HR Zones 1-3 (Weekly)", of(VivaTrendMetric.ZONES_1_3), clock),
            row("zones45", Icons.Outlined.Timer, "HR Zones 4-5 (Weekly)", of(VivaTrendMetric.ZONES_4_5), clock),
            row("strength", Icons.Outlined.FitnessCenter, "Strength Activity Time", of(VivaTrendMetric.STRENGTH_TIME), clock),
            row("vo2max", Icons.Outlined.Speed, "VO₂ Max", of(VivaTrendMetric.VO2_MAX), whole),
            row("active_kcal", Icons.Outlined.LocalFireDepartment, "Calories", daily { it.activeKcalEst }, grouped),
        )
        val rest = listOf(
            row("avg_hr", Icons.Outlined.FavoriteBorder, "Average Heart Rate", of(VivaTrendMetric.AVERAGE_HR), whole),
            row("strain", Icons.Outlined.LocalFireDepartment, "Day Strain",
                daily { d -> d.strain?.let { UnitFormatter.effortValue(it, EffortScale.WHOOP) } }, one, today = strainNow),
            row("asleep", Icons.Outlined.Bedtime, "Hours of Sleep", daily { it.totalSleepMin }, clock),
            row("zones_all", Icons.Outlined.Timer, "HR Zones All (Weekly)", of(VivaTrendMetric.ZONES_ALL), clock),
            if (x.showBodyFat) row("body_fat", Icons.Outlined.MonitorWeight, "Body Fat", of(VivaTrendMetric.BODY_FAT), one, unit = "%")
            else row("lean_mass", Icons.Outlined.MonitorWeight, "Lean Body Mass", of(VivaTrendMetric.LEAN_BODY_MASS), one, unit = "%"),
            row("recovery", Icons.Outlined.FavoriteBorder, "Recovery", daily { it.recovery }, whole, unit = "%"),
            row("resp", Icons.Outlined.Air, "Respiratory Rate", daily { it.respRateBpm }, one),
            row("restorative_pct", Icons.Outlined.Bedtime, "Restorative Sleep (%)", trend(VivaTrendMetric.RESTORATIVE_PERCENT), whole, unit = "%"),
            row("restorative_min", Icons.Outlined.Bedtime, "Restorative Sleep (Hrs)", trend(VivaTrendMetric.RESTORATIVE_SLEEP), clock),
            row("consistency", Icons.Outlined.Bedtime, "Sleep Consistency", of(VivaTrendMetric.SLEEP_CONSISTENCY), whole, unit = "%"),
            row("debt", Icons.Outlined.Bedtime, "Sleep Debt", of(VivaTrendMetric.SLEEP_DEBT), clock),
            row("efficiency", Icons.Outlined.Bedtime, "Sleep Efficiency", trend(VivaTrendMetric.SLEEP_EFFICIENCY), whole, unit = "%"),
            row("need", Icons.Outlined.Bedtime, "Sleep Needed", of(VivaTrendMetric.SLEEP_NEEDED), clock),
            row("rest", Icons.Outlined.Bedtime, "Sleep Performance", { x.sleepPerformance[it] }, whole, unit = "%", today = input.sleepPercent),
            row("in_bed", Icons.Outlined.Bedtime, "Time in Bed", trend(VivaTrendMetric.TIME_IN_BED), clock),
            row("weight", Icons.Outlined.MonitorWeight, "Weight", of(VivaTrendMetric.WEIGHT), one, unit = "kg"),
        )
        return top to rest
    }
}
