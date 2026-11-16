package com.noop.analytics

import com.noop.data.DailyMetric
import com.noop.data.MetricSeriesRow
import com.noop.data.ScoreInputProvenanceRow
import com.noop.data.SleepSession
import com.noop.data.WhoopRepository

/** Persistence-only helpers kept out of the already large scoring orchestrator. */
internal object IntelligencePersistence {
    fun historyRepairIsPending(effortDone: Boolean, sleepWearDone: Boolean): Boolean =
        !effortDone || !sleepWearDone

    data class LegacyScoreSnapshot(
        val avgHrv: Double,
        val recovery: Double?,
        val respRateBpm: Double?,
        val avgSdnn: Double?,
        val recoverySource: String?,
    )

    data class LegacyScoreClock(
        val nowLocalMidnight: Long,
        val nowSeconds: Long,
        val tzOffsetSeconds: Long,
    )

    data class ComputedWindow(
        val deviceId: String,
        val from: String,
        val to: String,
        val dailies: List<DailyMetric>,
        val metricRows: List<MetricSeriesRow>,
        val provenance: List<ScoreInputProvenanceRow>,
        val markerSourceIds: List<String>,
    )

    /** Keep the per-day repair loop outside the instrumented scoring coroutine's bytecode budget. */
    suspend fun persistComputedWindow(repo: WhoopRepository, window: ComputedWindow, preserveUnscoredHistory: Boolean) {
        val windows = if (preserveUnscoredHistory) byScoredDay(window) else listOf(window)
        for (part in windows) part.persistInto(repo)
    }

    private suspend fun ComputedWindow.persistInto(repo: WhoopRepository) =
        repo.replaceComputedScoreWindow(this)

    /** Per-day repair writes leave older, unscorable cached days and their provenance intact. */
    fun byScoredDay(window: ComputedWindow): List<ComputedWindow> {
        val dailiesByDay = window.dailies.groupBy { it.day }
        val metricsByDay = window.metricRows.groupBy { it.day }
        val provenanceByDay = window.provenance.groupBy { it.day }
        return dailiesByDay.keys.sorted().map { day ->
            window.copy(from = day, to = day,
                dailies = dailiesByDay.getValue(day),
                metricRows = metricsByDay[day].orEmpty(),
                provenance = provenanceByDay[day].orEmpty())
        }
    }

    suspend fun prepareComputedWindow(
        repo: WhoopRepository,
        importedDeviceId: String,
        computedId: String,
        from: String,
        to: String,
        dailies: List<DailyMetric>,
        metricRows: List<MetricSeriesRow>,
        cycle: PhysiologicalStepCycleEngine.Result,
        candidatePriorities: List<Pair<String, Int>>,
        ownerByDay: Map<String, String>,
        legacyClock: LegacyScoreClock,
        computed: MutableList<IntelligenceEngine.Computed>,
        detectedSleeps: List<SleepSession> = emptyList(),
    ): ComputedWindow {
        // Sessions are saved after this window, so this pass's detections are passed in and win over stored copies.
        val fromTs = java.time.LocalDate.parse(from).toEpochDay() * 86_400L - legacyClock.tzOffsetSeconds - 4 * 86_400L
        val stored = repo.sleepSessionsForDevice(computedId, fromTs, legacyClock.nowSeconds + 86_400L, 4000)
            .filter { s -> detectedSleeps.none { it.startTs < s.endTs && s.startTs < it.endTs } }
        val rescored = withNightHistory(metricRows, dailies, detectedSleeps + stored, legacyClock.tzOffsetSeconds)
        val mutableDailies = dailies.toMutableList()
        val snapshots = preserveLegacyScores(
            repo, computedId, from, to, mutableDailies, ownerByDay,
            legacyClock,
        )
        applyLegacyScoresToOutput(computed, snapshots)
        val provenance = scoreProvenance(computedId, mutableDailies, rescored, ownerByDay, snapshots)
        return ComputedWindow(
            deviceId = computedId,
            from = from,
            to = to,
            dailies = mutableDailies,
            metricRows = (rescored + cycle.recoveredOwnerMarkerRows)
                .distinctBy { Triple(it.deviceId, it.day, it.key) },
            provenance = provenance,
            markerSourceIds = DayCycleIntelligenceIntegration.markerRewriteSourceIds(
                repo.computedSourceIds(importedDeviceId),
                candidatePriorities.map { (owner, _) -> repo.computedDeviceId(owner) },
            ),
        )
    }

    /**
     * Viva: re-scores each `sleep_performance` row with what a single day cannot see, the previous
     * night's sleep ([RestScorer.carriedNeedHours]) and bed-time consistency against the three nights
     * before ([RestScorer.overlapConsistency]). The night of a day is its longest session that ended on
     * that local day. Rows with no matching daily keep their value.
     * shortcut: other readers call [RestScorer.restFromDaily] without history and read up to ~5 off this
     * stored value on irregular nights; point them at the stored series if that shows.
     */
    fun withNightHistory(
        rows: List<MetricSeriesRow>,
        dailies: List<DailyMetric>,
        sessions: List<SleepSession>,
        tzOffsetSeconds: Long,
    ): List<MetricSeriesRow> {
        val byDay = dailies.associateBy { it.day }
        val nights = sessions.filter { it.endTs > it.effectiveStartTs }
            .groupBy { java.time.LocalDate.ofEpochDay(Math.floorDiv(it.endTs + tzOffsetSeconds, 86_400L)).toString() }
            .mapValues { (_, s) -> s.maxBy { it.endTs - it.effectiveStartTs }.let { it.effectiveStartTs to it.endTs } }
        return rows.flatMap { row ->
            val daily = byDay[row.day]
            if (row.key != "sleep_performance" || daily == null) return@flatMap listOf(row)
            val day = java.time.LocalDate.parse(row.day)
            val previous = (1L..3L).mapNotNull { nights[day.minusDays(it).toString()] }
            val consistency = nights[row.day]?.let { RestScorer.overlapConsistency(it, previous) }
            val previousAsleep = byDay[day.minusDays(1).toString()]?.totalSleepMin
            val needMin = (RestScorer.carriedNeedHours(previousAsleep) ?: RestScorer.defaultSleepNeedHours) * 60.0
            // The same need and consistency the score used, stored for the dashboard's sleep rows.
            listOfNotNull(
                RestScorer.restFromDaily(daily, consistency, previousAsleep)?.let { row.copy(value = it) } ?: row,
                consistency?.let { row.copy(key = "sleep_consistency", value = it * 100.0) },
                row.copy(key = "sleep_need_min", value = needMin),
                row.copy(key = "sleep_debt_min", value = needMin - RestScorer.defaultSleepNeedHours * 60.0),
            )
        }
    }

    fun scoreProvenance(
        computedId: String,
        dailies: List<DailyMetric>,
        metricRows: List<MetricSeriesRow>,
        ownerByDay: Map<String, String>,
        legacySnapshots: Map<String, LegacyScoreSnapshot> = emptyMap(),
    ): List<ScoreInputProvenanceRow> {
        val byCell = LinkedHashMap<Pair<String, String>, ScoreInputProvenanceRow>()
        for (daily in dailies) {
            val source = ownerByDay[daily.day] ?: continue
            if (daily.recovery != null) {
                val legacy = legacySnapshots[daily.day]
                val recoverySource = legacy?.recoverySource ?: if (legacy == null) source else null
                if (recoverySource != null) {
                    byCell[daily.day to "recovery"] = ScoreInputProvenanceRow(
                        computedId, daily.day, "recovery", recoverySource,
                    )
                }
            }
            if (daily.strain != null) {
                byCell[daily.day to "strain"] = ScoreInputProvenanceRow(
                    computedId, daily.day, "strain", source,
                )
            }
        }
        for (point in metricRows) {
            val source = ownerByDay[point.day] ?: continue
            byCell[point.day to point.key] = ScoreInputProvenanceRow(
                computedId, point.day, point.key, source,
            )
        }
        return byCell.values.toList()
    }

    private suspend fun preserveLegacyScores(
        repo: WhoopRepository,
        computedId: String,
        from: String,
        to: String,
        dailies: MutableList<DailyMetric>,
        ownerByDay: Map<String, String>,
        clock: LegacyScoreClock,
    ): Map<String, LegacyScoreSnapshot> {
        val existingByDay = repo.dailyMetrics(computedId, from, to).associateBy { it.day }
        val snapshots = LinkedHashMap<String, LegacyScoreSnapshot>()
        for (index in dailies.indices) {
            val fresh = dailies[index]
            val existing = existingByDay[fresh.day] ?: continue
            val oldHrv = existing.avgHrv ?: continue
            if (fresh.avgHrv != null || (fresh.totalSleepMin ?: 0.0) <= 0.0) continue
            val owner = ownerByDay[fresh.day] ?: continue
            val dayStart = try {
                java.time.LocalDate.parse(fresh.day).toEpochDay() * 86_400L - clock.tzOffsetSeconds
            } catch (_: java.time.format.DateTimeParseException) { continue }
            val readFrom = dayStart - StreamReadCap.LOOKBACK_SECONDS
            val readTo = IntelligenceEngine.sleepReadWindowEnd(
                dayStart, clock.nowLocalMidnight, clock.nowSeconds,
            )
            if (!repo.legacyWhoop5RrWithheld(owner, readFrom, readTo)) continue
            val snapshot = LegacyScoreSnapshot(
                avgHrv = oldHrv,
                recovery = existing.recovery,
                respRateBpm = existing.respRateBpm,
                avgSdnn = existing.avgSdnn,
                recoverySource = repo.scoreInputSource(computedId, fresh.day, "recovery"),
            )
            dailies[index] = fresh.copy(
                avgHrv = snapshot.avgHrv,
                recovery = snapshot.recovery,
                respRateBpm = snapshot.respRateBpm,
                avgSdnn = snapshot.avgSdnn,
            )
            snapshots[fresh.day] = snapshot
        }
        return snapshots
    }

    private fun applyLegacyScoresToOutput(
        computed: MutableList<IntelligenceEngine.Computed>,
        snapshots: Map<String, LegacyScoreSnapshot>,
    ) {
        for (index in computed.indices) {
            val snapshot = snapshots[computed[index].day] ?: continue
            computed[index] = computed[index].copy(hrv = snapshot.avgHrv, recovery = snapshot.recovery)
        }
    }

    suspend fun persistDetectedSleepDetails(
        repo: WhoopRepository,
        computedId: String,
        kept: List<SleepSession>,
        scoredNights: List<DayResult>,
    ) {
        // Only kept (not edited/dismissed) sessions receive their per-epoch motion and band-state arrays.
        // Missing streams remain absent rather than being materialised as synthetic zero arrays.
        if (kept.isNotEmpty()) repo.upsertSleepSessions(kept)
        val keptStarts = kept.map { it.startTs }.toHashSet()
        val motionByStart = HashMap<Long, List<Double>>()
        val sleepStateByStart = HashMap<Long, List<Int>>()
        for (result in scoredNights) {
            for ((start, motion) in result.sessionMotionByStart) {
                if (start in keptStarts) motionByStart[start] = motion
            }
            for ((start, states) in result.sessionSleepStateByStart) {
                if (start in keptStarts) sleepStateByStart[start] = states
            }
        }
        for ((start, motion) in motionByStart) repo.persistSessionMotion(computedId, start, motion)
        for ((start, states) in sleepStateByStart) repo.persistSessionSleepState(computedId, start, states)
    }
}

/** Keep the oversized scoring orchestrator at a single, auditable transactional call site. */
internal suspend fun WhoopRepository.replaceComputedScoreWindow(
    window: IntelligencePersistence.ComputedWindow,
) = replaceComputedScoreWindow(
    deviceId = window.deviceId,
    from = window.from,
    to = window.to,
    dailyMetrics = window.dailies,
    metricPoints = window.metricRows,
    provenance = window.provenance,
    replaceMetricKeys = listOf(DayCycleIntelligenceIntegration.ONSET_KEY),
    replaceMetricSourceIds = window.markerSourceIds,
)
