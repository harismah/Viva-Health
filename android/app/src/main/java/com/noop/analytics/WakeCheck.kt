package com.noop.analytics

import com.noop.data.MetricSeriesRow
import com.noop.data.SleepSession
import com.noop.data.WhoopRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Viva (Android only so far): the morning "Are you awake?" check, as the reference app runs it. A night
 * stays open, its scores hidden on Home, until the wearer confirms they are up or the strap shows them up
 * for [AUTO_AWAKE_SECONDS]. Closing stores the wake as [KEY] for the day the night ends on, and every
 * scoring pass then reads that night's streams only up to it, so a later doze is never folded into the
 * night it followed (it raised Sleep 44 -> 68 and Recovery 43 -> 61 on 18 Nov 2026).
 */
object WakeCheck {
    /** metricSeries key under the computed id: the epoch second the day's night was closed at. */
    const val KEY = "wake_confirmed_ts"

    /** A night shorter than this is a nap, never the night the question is about. */
    const val MIN_NIGHT_SECONDS = 3 * 3600L

    /** Strap data this long past the night's end means the wearer is up: close it without asking. */
    const val AUTO_AWAKE_SECONDS = 45 * 60L

    /** The night [day] closes: the longest computed sleep of at least [MIN_NIGHT_SECONDS] ending on it. */
    fun mainNight(sessions: List<SleepSession>, day: LocalDate, zone: ZoneId): SleepSession? = sessions
        .filter { Instant.ofEpochSecond(it.endTs).atZone(zone).toLocalDate() == day && it.endTs - it.effectiveStartTs >= MIN_NIGHT_SECONDS }
        .maxByOrNull { it.endTs - it.effectiveStartTs }

    /** Whether the strap's newest reading lies far enough past [night] to call the wearer up. */
    fun awake(night: SleepSession, latestDataTs: Long?): Boolean =
        latestDataTs != null && latestDataTs - night.endTs >= AUTO_AWAKE_SECONDS

    /** [readEnd] for [day], capped at its close when it has one. */
    fun capped(cuts: Map<String, Long>, day: String, readEnd: Long): Long = minOf(readEnd, cuts[day] ?: Long.MAX_VALUE)

    /** A day-cache [key] that also changes with the day's close, so closing a night re-scores it. */
    fun keyed(key: String, cuts: Map<String, Long>, day: String): String = cuts[day]?.let { "$key|wake=$it" } ?: key

    /** Each day's stored close, to cap that night's read window. */
    suspend fun cutoffs(repo: WhoopRepository, computedId: String): Map<String, Long> =
        repo.metricSeries(computedId, KEY, "0000-01-01", "9999-12-31").associate { it.day to it.value.toLong() }

    suspend fun cutoff(repo: WhoopRepository, computedId: String, day: String): Long? =
        repo.metricSeries(computedId, KEY, day, day).firstOrNull()?.value?.toLong()

    suspend fun close(repo: WhoopRepository, computedId: String, day: String, atTs: Long) =
        repo.upsertMetricSeries(listOf(MetricSeriesRow(computedId, day, KEY, atTs.toDouble())))

    /** How much of the night before a close the nap search reads, so it judges sleep the way the night's own detection does. */
    const val CONTEXT_SECONDS = 12 * 3600L

    /** A nap shorter than this after the close is a stir, not a nap. */
    const val MIN_NAP_SECONDS = 15 * 60L

    /**
     * The part of [session] after [cut], as a nap: stages clipped to the close, leading and trailing wake
     * dropped. Null when nothing of [MIN_NAP_SECONDS] is left. Heart rate and HRV are left for the caller
     * to recompute over the new span.
     */
    fun afterClose(session: DetectedSleep, cut: Long): DetectedSleep? {
        if (session.end <= cut) return null
        val clipped = session.stages.filter { it.end > cut }
            .map { if (it.start < cut) StageSegment(cut, it.end, it.stage) else it }
            .dropWhile { it.stage == "wake" }.dropLastWhile { it.stage == "wake" }
        if (clipped.isEmpty()) return null
        val start = clipped.first().start
        val end = clipped.last().end
        if (end - start < MIN_NAP_SECONDS) return null
        return session.copy(start = start, end = end, stages = clipped, efficiency = SleepStager.efficiency(start, end, clipped))
    }

    /**
     * Stores the sleep a closed night's day holds AFTER its close as that day's naps. A pass scores such a
     * day only up to the close, so without this a doze after the wake (and any later nap that day) would be
     * lost. Run after the pass, outside the scoring loop; the day's totals stay the night's, as for every
     * nap, and the next pass's sleep need reads these rows as nap credit. A re-detected nap that drifted
     * is folded by the pass's overlapping-duplicate heal (#899).
     * shortcut: only today's and yesterday's closes are searched; widen if older naps go missing.
     */
    suspend fun storeLaterNaps(
        repo: WhoopRepository, strapId: String, cuts: Map<String, Long>, nowSeconds: Long,
        tzOffsetSeconds: Long, useSleepStagerV2: Boolean,
    ): Int {
        val today = AnalyticsEngine.dayString(nowSeconds, tzOffsetSeconds)
        val yesterday = AnalyticsEngine.dayString(nowSeconds - 86_400, tzOffsetSeconds)
        var stored = 0
        for (day in listOf(yesterday, today)) {
            val cut = cuts[day] ?: continue
            val dayEnd = minOf(nowSeconds, AnalyticsEngine.dayStartUtcSeconds(day) - tzOffsetSeconds + 86_400)
            if (dayEnd <= cut) continue
            // The detector judges sleep against its whole window's heart rate: a window starting at the
            // close holds only waking hours, and sitting still then reads as sleep (a 09:40 wake read 10:13
            // on 18 Nov). So it reads the night before too, and only what follows the close is kept.
            val from = cut - CONTEXT_SECONDS
            val hr = repo.hrSamplesForDevice(strapId, from, dayEnd, IntelligenceEngine.STREAM_LIMIT)
            if (hr.size < 200) continue
            val rr = repo.rrIntervalsForDevice(strapId, from, dayEnd, IntelligenceEngine.STREAM_LIMIT).sortedBy { it.ts }
            val naps = SleepStager.detectSleep(
                hr = hr,
                rr = rr,
                resp = repo.respSamples(strapId, from, dayEnd, IntelligenceEngine.STREAM_LIMIT),
                gravity = repo.gravitySamplesForDevice(strapId, from, dayEnd, IntelligenceEngine.STREAM_LIMIT),
                tzOffsetSeconds = tzOffsetSeconds,
                // The strap's own asleep / up band, which settles a borderline morning re-onset as the night's detection does.
                bandSleepState = repo.sleepStateSamples(strapId, from, dayEnd).map { it.ts to it.state },
                useSleepStagerV2 = useSleepStagerV2,
            ).filter { AnalyticsEngine.dayString(it.end, tzOffsetSeconds) == day }
                .mapNotNull { afterClose(it, cut) }
                .map { it.copy(restingHR = SleepStager.sessionRestingHR(it.start, it.end, hr), avgHRV = SleepStager.sessionAvgHRV(it.start, it.end, rr)) }
            // This search owns the detected rows after the close: drop its earlier ones (a re-detected nap
            // may have moved), never a hand-edited one, and never with a dismissal tombstone.
            repo.sleepSessionsForDevice("$strapId-noop", cut, dayEnd)
                .filter { !it.userEdited && it.startTs >= cut && AnalyticsEngine.dayString(it.endTs, tzOffsetSeconds) == day }
                .forEach { repo.deleteSleepSessionRowOnly(it) }
            if (naps.isEmpty()) continue
            repo.upsertSleepSessions(naps.map {
                SleepSession(
                    deviceId = "$strapId-noop", startTs = it.start, endTs = it.end, efficiency = it.efficiency,
                    restingHr = it.restingHR, avgHrv = it.avgHRV, stagesJSON = AnalyticsEngine.encodeStages(it.stages),
                )
            })
            stored += naps.size
        }
        return stored
    }

    /**
     * Closes today's night once the strap shows the wearer up. Run after a scoring pass, so the night's end
     * is the one detected from every reading so far. Returns the close it wrote, or null.
     */
    suspend fun closeOnceAwake(repo: WhoopRepository, strapId: String, nowSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val computedId = "$strapId-noop"
        val today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate()
        val night = mainNight(repo.sleepSessionsForDevice(computedId, nowSeconds - 36 * 3600, nowSeconds), today, zone) ?: return null
        if (cutoff(repo, computedId, today.toString()) != null) return null
        if (!awake(night, repo.latestHrSampleTs(strapId))) return null
        close(repo, computedId, today.toString(), night.endTs)
        return night.endTs
    }
}
