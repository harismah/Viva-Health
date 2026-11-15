package com.noop.ui.viva

import androidx.compose.ui.graphics.Brush
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp

/**
 * Viva's real-time stress: the last 24 hours in ten-minute steps on the 0-3 scale, each step the
 * heart rate of that moment against the wearer's typical resting heart rate.
 *
 * The upstream intraday read (`DaytimeStress`) scores only 06:00 to 22:00, against that day's own calm
 * hours. A wearer who sleeps from 05:00 to 13:00 had their sleep scored as their day and read HIGH
 * through it, where the reference app read 0.3. This follows the recipe the reference app states
 * instead: the reading of the moment against a personal baseline, every hour, sleep included.
 *
 * shortcut: heart rate only, and the two constants are a least-squares fit to seven readings of the
 * reference app from one day (asleep, at rest and one peak), so the two ends are right and the middle
 * is within about half a point. Refit, and add the HRV term the reference also uses, once a week of
 * paired readings exists.
 */
internal object VivaStress {
    const val BUCKET_SECONDS = 600L
    const val WINDOW_SECONDS = 86_400L

    /** Beats over typical resting heart rate that read 1.5, the middle of the scale. */
    const val MIDPOINT_OVER_RESTING_BPM = 16.0

    /** Beats that move a reading by one step of the logistic. */
    const val SCALE_BPM = 5.8

    /** Used until a night has banked a resting heart rate. */
    const val DEFAULT_RESTING_BPM = 60.0

    /** One ten-minute step: its start and its level on the 0-3 scale. */
    data class Point(val ts: Long, val level: Double)

    /** The 0-3 level of a mean heart rate against the wearer's typical resting heart rate. */
    fun level(meanBpm: Double, restingBpm: Double): Double =
        3.0 / (1.0 + exp(-(meanBpm - restingBpm - MIDPOINT_OVER_RESTING_BPM) / SCALE_BPM))

    /** Typical resting heart rate: the median of the latest 14 nights that banked one. */
    fun typicalResting(days: List<DailyMetric>): Double {
        val recent = days.sortedBy { it.day }.mapNotNull { it.restingHr }.takeLast(14).sorted()
        return if (recent.isEmpty()) DEFAULT_RESTING_BPM else recent[recent.size / 2].toDouble()
    }

    /** Minutes spent LOW (under 1.0), MEDIUM (under 2.0) and HIGH, in that order. */
    fun bandMinutes(points: List<Point>): List<Int> {
        val steps = IntArray(3)
        points.forEach { steps[if (it.level < 1.0) 0 else if (it.level < 2.0) 1 else 2]++ }
        return steps.map { it * (BUCKET_SECONDS / 60).toInt() }
    }

    /** The mean level of each local day that has a step, oldest first. */
    fun dailyMeans(points: List<Point>, zone: ZoneId): List<Double> =
        points.groupBy { Instant.ofEpochSecond(it.ts).atZone(zone).toLocalDate() }.toSortedMap().values
            .map { day -> day.map { it.level }.average() }

    /** Colours a trace by height: the High band at the top, Medium in the middle, Low at the bottom. */
    fun lineBrush(): Brush = Brush.verticalGradient(listOf(VivaStyle.recoveryYellow, VivaStyle.recoveryGreen, VivaStyle.sleep))
}

/**
 * Stress as the body reads right now: the last 24 hours, oldest first, and the newest step. The home
 * tile, the home graph card and the dial on the Stress Monitor screen all take their figure from here,
 * so the three cannot disagree. [sleeps] are the sleep spans inside the window.
 */
internal data class VivaStressNow(val points: List<VivaStress.Point>, val sleeps: List<LongRange>) {
    val levels: List<Double> get() = points.map { it.level }
    val startTs: Long get() = points.first().ts
    val endTs: Long get() = points.last().ts + VivaStress.BUCKET_SECONDS
    val value: Double get() = points.last().level
    val mean: Double get() = levels.average()
    fun asleep(point: VivaStress.Point): Boolean = sleeps.any { point.ts in it }

    /** The sleep spans as fractions of the trace's width; the steps are drawn evenly spaced. */
    val sleepShading: List<ClosedFloatingPointRange<Float>>
        get() {
            if (points.size < 2) return emptyList()
            val spans = mutableListOf<ClosedFloatingPointRange<Float>>()
            var start = -1
            points.forEachIndexed { index, point ->
                val asleep = asleep(point)
                if (asleep && start < 0) start = index
                if (start >= 0 && (!asleep || index == points.lastIndex)) {
                    val end = if (asleep) index else index - 1
                    spans += start.toFloat() / points.lastIndex..end.toFloat() / points.lastIndex
                    start = -1
                }
            }
            return spans
        }
}

/** The wearer's [VivaStressNow] over [from] to [nowSeconds], the last 24 hours unless given; null when the strap banked no heart rate in it. */
internal suspend fun vivaStressNow(
    viewModel: AppViewModel,
    days: List<DailyMetric>,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
    from: Long = nowSeconds - VivaStress.WINDOW_SECONDS,
): VivaStressNow? {
    val resting = VivaStress.typicalResting(days)
    val points = runCatching {
        viewModel.repo.hrBucketsUnion(viewModel.activeStrapId, from, nowSeconds, VivaStress.BUCKET_SECONDS)
    }.getOrDefault(emptyList())
        // The step still filling is left out: a few seconds of heart rate is not a reading.
        .filter { it.bucket + VivaStress.BUCKET_SECONDS <= nowSeconds }
        .map { VivaStress.Point(it.bucket, VivaStress.level(it.avgBpm, resting)) }
    if (points.isEmpty()) return null
    val sleeps = runCatching {
        viewModel.repo.sleepSessionsMerged(viewModel.activeStrapId, from - 86_400, nowSeconds)
    }.getOrDefault(emptyList()).map { it.effectiveStartTs..it.endTs }
    return VivaStressNow(points, sleeps)
}

/** The mean stress level of each of the last [count] local days that banked heart rate, oldest first. */
internal suspend fun vivaStressDaily(viewModel: AppViewModel, days: List<DailyMetric>, zone: ZoneId, count: Int = 14): List<Double> {
    val from = LocalDate.now(zone).minusDays(count - 1L).atStartOfDay(zone).toEpochSecond()
    val resting = VivaStress.typicalResting(days)
    val points = runCatching {
        viewModel.repo.hrBucketsUnion(viewModel.activeStrapId, from, System.currentTimeMillis() / 1000L, VivaStress.BUCKET_SECONDS)
    }.getOrDefault(emptyList()).map { VivaStress.Point(it.bucket, VivaStress.level(it.avgBpm, resting)) }
    return VivaStress.dailyMeans(points, zone)
}
