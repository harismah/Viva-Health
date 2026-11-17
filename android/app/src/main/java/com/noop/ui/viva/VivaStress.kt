package com.noop.ui.viva

import androidx.compose.ui.graphics.Brush
import com.noop.data.DailyMetric
import com.noop.data.RrInterval
import com.noop.ui.AppViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Viva's real-time stress: the last 24 hours in ten-minute steps on the 0-3 scale, each step the heart
 * rate and the heart rate variability of that moment against the wearer's typical resting heart rate
 * and typical nightly HRV.
 *
 * The upstream intraday read (`DaytimeStress`) scores only 06:00 to 22:00, against that day's own calm
 * hours. A wearer who sleeps from 05:00 to 13:00 had their sleep scored as their day and read HIGH
 * through it, where the reference app read 0.3. This follows the recipe the reference app states
 * instead: the reading of the moment against a personal baseline, every hour, sleep included.
 *
 * The three constants are a joint fit, for one wearer, to 18 readings of the reference app over six
 * days and to its time spent low, medium and high on five days. Heart rate alone could not place a
 * reading: at 68 to 70 bpm the reference read 1.5 with an RMSSD of 30 ms and 0.5 with 71 ms. Against the
 * heart-rate-only curve this replaces, the share of a day read HIGH fell from 7 to 38 percent to 0 to 17
 * (reference: 1 to 14).
 *
 * shortcut: one wearer and six days, refit when a second wearer or a month of readings exists.
 */
internal object VivaStress {
    const val BUCKET_SECONDS = 600L
    const val WINDOW_SECONDS = 86_400L

    /** The home card draws the last 12 hours, as the reference app's does; its newest step is the dial's. */
    const val HOME_WINDOW_SECONDS = 43_200L

    /** Beats over typical resting heart rate that read 1.5 when HRV sits at its typical level. */
    const val MIDPOINT_OVER_RESTING_BPM = 23.5

    /** Logistic steps per beat of heart rate. */
    const val HR_SLOPE = 0.09

    /** Logistic steps per natural-log unit of RMSSD over the typical nightly HRV; lower HRV reads higher. */
    const val HRV_SLOPE = 1.5

    /** The log ratio is held to this range, so one noisy step cannot pin the reading. */
    const val HRV_LN_MIN = -1.3
    const val HRV_LN_MAX = 0.9

    /** Successive-beat pairs a step needs before its RMSSD counts; under it the step reads on heart rate alone. */
    const val MIN_RMSSD_PAIRS = 60

    /** Used until a night has banked an HRV. */
    const val DEFAULT_HRV_MS = 45.0

    /** Used until a night has banked a resting heart rate. */
    const val DEFAULT_RESTING_BPM = 60.0

    /** One ten-minute step: its start and its level on the 0-3 scale. */
    data class Point(val ts: Long, val level: Double)

    /**
     * The 0-3 level of a step: its mean heart rate against the wearer's typical resting heart rate, and
     * its [rmssd] against their [typicalHrv]. A null [rmssd] leaves the HRV term at its neutral zero.
     */
    fun level(meanBpm: Double, restingBpm: Double, rmssd: Double? = null, typicalHrv: Double = DEFAULT_HRV_MS): Double {
        val hrv = if (rmssd == null || rmssd <= 0.0 || typicalHrv <= 0.0) 0.0
        else ln(rmssd / typicalHrv).coerceIn(HRV_LN_MIN, HRV_LN_MAX)
        return 3.0 / (1.0 + exp(-(HR_SLOPE * (meanBpm - restingBpm - MIDPOINT_OVER_RESTING_BPM) - HRV_SLOPE * hrv)))
    }

    /** Typical nightly HRV: the median of the latest 14 nights that banked one. */
    fun typicalHrv(days: List<DailyMetric>): Double {
        val recent = days.sortedBy { it.day }.mapNotNull { it.avgHrv }.filter { it > 0.0 }.takeLast(14).sorted()
        return if (recent.isEmpty()) DEFAULT_HRV_MS else recent[recent.size / 2]
    }

    /**
     * RMSSD of each step, keyed by the step's start, from time-ordered beats. A pair counts when both
     * beats fall in the same step no more than two seconds apart, both are 300 to 2000 ms, and they
     * differ by under a fifth of the first; a step needs [MIN_RMSSD_PAIRS] pairs. The same rule the
     * constants above were fitted with, so it is kept separate from the nightly HRV cleaning.
     */
    fun bucketRmssd(rr: List<RrInterval>): Map<Long, Double> {
        val sums = HashMap<Long, Double>()
        val counts = HashMap<Long, Int>()
        var previous: RrInterval? = null
        for (beat in rr) {
            val bucket = beat.ts / BUCKET_SECONDS * BUCKET_SECONDS
            val p = previous
            if (p != null && p.ts / BUCKET_SECONDS * BUCKET_SECONDS == bucket && beat.ts - p.ts <= 2 &&
                beat.rrMs in 301..1999 && p.rrMs in 301..1999
            ) {
                val d = abs(beat.rrMs - p.rrMs).toDouble()
                if (d < 0.2 * p.rrMs) {
                    sums[bucket] = (sums[bucket] ?: 0.0) + d * d
                    counts[bucket] = (counts[bucket] ?: 0) + 1
                }
            }
            previous = beat
        }
        return counts.filterValues { it >= MIN_RMSSD_PAIRS }.mapValues { (bucket, n) -> sqrt(sums.getValue(bucket) / n) }
    }

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
    fun lineBrush(): Brush = Brush.verticalGradient(listOf(VivaStyle.recoveryYellow, VivaStyle.positive, VivaStyle.sleep))
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

/**
 * The steps over [from] to [to], oldest first, each scored from its heart rate and its RMSSD. Read a
 * day at a time, so a fortnight of beats is never held at once; every slice after the first starts on a
 * step boundary, so no step is split between two reads.
 */
private suspend fun vivaStressPoints(
    viewModel: AppViewModel, days: List<DailyMetric>, from: Long, to: Long,
): List<VivaStress.Point> {
    val resting = VivaStress.typicalResting(days)
    val typicalHrv = VivaStress.typicalHrv(days)
    val points = ArrayList<VivaStress.Point>()
    var start = from
    while (start < to) {
        val end = minOf(start / VivaStress.BUCKET_SECONDS * VivaStress.BUCKET_SECONDS + VivaStress.WINDOW_SECONDS, to)
        val buckets = runCatching {
            viewModel.repo.hrBucketsUnion(viewModel.activeStrapId, start, end - 1, VivaStress.BUCKET_SECONDS)
        }.getOrDefault(emptyList())
        if (buckets.isNotEmpty()) {
            val rmssd = runCatching {
                VivaStress.bucketRmssd(
                    viewModel.repo.rrIntervalsUnion(viewModel.activeStrapId, start, end - 1).sortedBy { it.ts },
                )
            }.getOrDefault(emptyMap())
            buckets.forEach {
                points += VivaStress.Point(it.bucket, VivaStress.level(it.avgBpm, resting, rmssd[it.bucket], typicalHrv))
            }
        }
        start = end
    }
    return points
}

/** The wearer's [VivaStressNow] over [from] to [nowSeconds], the last 24 hours unless given; null when the strap banked no heart rate in it. */
internal suspend fun vivaStressNow(
    viewModel: AppViewModel,
    days: List<DailyMetric>,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
    from: Long = nowSeconds - VivaStress.WINDOW_SECONDS,
): VivaStressNow? {
    // The step still filling is left out: a few seconds of heart rate is not a reading.
    val points = vivaStressPoints(viewModel, days, from, nowSeconds)
        .filter { it.ts + VivaStress.BUCKET_SECONDS <= nowSeconds }
    if (points.isEmpty()) return null
    val sleeps = runCatching {
        viewModel.repo.sleepSessionsMerged(viewModel.activeStrapId, from - 86_400, nowSeconds)
    }.getOrDefault(emptyList()).map { it.effectiveStartTs..it.endTs }
    return VivaStressNow(points, sleeps)
}

/** Local [date] from midnight to midnight: the reading a past day's home tile and Stress Monitor dial both average. */
internal suspend fun vivaStressOn(viewModel: AppViewModel, days: List<DailyMetric>, date: LocalDate, zone: ZoneId): VivaStressNow? {
    val end = date.plusDays(1).atStartOfDay(zone).toEpochSecond()
    return vivaStressNow(viewModel, days, nowSeconds = end, from = end - VivaStress.WINDOW_SECONDS)
}

/** The mean stress level of each of the last [count] local days that banked heart rate, oldest first. */
internal suspend fun vivaStressDaily(viewModel: AppViewModel, days: List<DailyMetric>, zone: ZoneId, count: Int = 14): List<Double> {
    val from = LocalDate.now(zone).minusDays(count - 1L).atStartOfDay(zone).toEpochSecond()
    return VivaStress.dailyMeans(vivaStressPoints(viewModel, days, from, System.currentTimeMillis() / 1000L), zone)
}
