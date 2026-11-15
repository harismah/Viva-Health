#if os(iOS)
import SwiftUI
import StrandDesign
import WhoopStore
import StrandAnalytics

/// The shared shape of the Recovery and Strain detail screens: the pillar ring, the metrics that
/// feed it compared with the last 30 days, and a week of trends.
///
/// `homeScore` is the value Home resolved and showed in its ring. When Home pushes this screen it
/// hands that value over, so the two rings cannot disagree; opened on its own, the screen reads
/// today's stored score.
private struct VivaPillarDetail<Extra: View>: View {
    let pillar: VivaTrendMetric
    let ringLabel: String
    let ringMaximum: Double
    let ringFormat: VivaScoreRing.Format
    let ringColor: (Double?) -> Color
    let score: Double?
    /// Shown under the ring when the score is not today's own ("Last night · Mon 9 Nov").
    var scoreNote: String? = nil
    /// Why today has no score of its own; nil when it has one.
    let emptyNote: String?
    /// Values another screen already resolved and showed, keyed by metric, with their carried-day
    /// caption. A handed value wins over this screen's own lookup so the two cannot disagree.
    var handed: [VivaTrendMetric: (value: Double?, note: String?)] = [:]
    let rows: [VivaTrendMetric]
    let weekly: [VivaTrendMetric]
    let infoTitle: String
    @ViewBuilder var extra: () -> Extra

    @EnvironmentObject private var repo: Repository
    @EnvironmentObject private var router: NavRouter
    @AppStorage("noop.coachEnabled") private var coachEnabled = true
    @State private var showInfo = false

    /// Today's row only. The newest stored row is NOT a stand-in: it may be an earlier day, and its
    /// numbers would then be presented as today's.
    private var todayRow: DailyMetric? {
        repo.today ?? repo.days.last(where: { $0.day == Repository.logicalDayKey(Date()) })
    }

    /// One row's value and the caption under it: today's value over its 30-day baseline, or a
    /// carried value over the day it came from.
    private func reading(_ metric: VivaTrendMetric) -> (value: Double?, caption: String?, trend: VivaMetricRow.Trend) {
        if let given = handed[metric] {
            return (given.value, given.note ?? baseline(metric).map { metric.text($0) },
                    given.note == nil ? .comparing(given.value, to: baseline(metric)) : .none)
        }
        let resolved = metric.todayOrCarried(days: repo.days, today: todayRow)
        if let day = resolved.carriedDay {
            return (resolved.value, BodyVitalReading.dayLabel(day), .none)
        }
        let base = baseline(metric)
        return (resolved.value, base.map { metric.text($0) }, .comparing(resolved.value, to: base))
    }

    private func baseline(_ metric: VivaTrendMetric) -> Double? {
        let todayKey = todayRow?.day ?? Repository.logicalDayKey(Date())
        let prior = repo.days.filter { $0.day < todayKey }.suffix(30).compactMap(metric.value)
        guard !prior.isEmpty else { return nil }
        return prior.reduce(0, +) / Double(prior.count)
    }

    var body: some View {
        VivaDetailScaffold(title: "Today", trailing: .info, onTrailing: { showInfo = true },
                           coachMessage: coachEnabled && score != nil
                               ? String(localized: "\(ringLabel) \(pillar.text(score)). Ask what moved it.") : nil,
                           onCoach: { router.openCoach() }) {
            VivaScoreRing(value: score, maximum: ringMaximum, color: ringColor(score),
                          format: ringFormat, size: .large, label: ringLabel)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
            if let scoreNote {
                Text(scoreNote).vivaLabel()
                    .frame(maxWidth: .infinity)
                    .padding(.top, -10)
            }
            if let emptyNote {
                VivaCallout(text: emptyNote)
            }
            // A row with neither a value today nor a baseline has nothing to say, so it is left out
            // rather than shown as dashes beside a Home tile that found the figure another way.
            let rows = rows.filter { reading($0).value != nil || baseline($0) != nil }
            if !rows.isEmpty {
            VivaCard {
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.element.id) { index, metric in
                        let row = reading(metric)
                        NavigationLink(value: VivaHomeRoute.trend(metric)) {
                            VivaMetricRow(icon: metric.icon, label: metric.title, value: metric.text(row.value),
                                          baseline: row.caption, trend: row.trend)
                                .frame(height: 56)
                        }
                        .buttonStyle(VivaPressStyle())
                        if index < rows.count - 1 { VivaDivider() }
                    }
                }
                HStack(spacing: 6) {
                    Image(systemName: "arrowtriangle.up.fill").font(.system(size: 6)).foregroundStyle(VivaStyle.positive)
                    Image(systemName: "arrowtriangle.down.fill").font(.system(size: 6)).foregroundStyle(VivaStyle.warning)
                    Text("Today").font(VivaFont.caption).foregroundStyle(VivaStyle.textPrimary)
                    Text("vs. last 30 days").font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                .frame(height: 30)
                .background(VivaStyle.inset, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            }
            extra()
            VivaSectionHeader(title: String(localized: "Weekly Trends"))
                .padding(.top, VivaStyle.sectionGap - VivaStyle.cardGap)
                .id("trends")
            ForEach(weekly) { metric in
                NavigationLink(value: VivaHomeRoute.trend(metric)) {
                    VivaWeekBarCard(title: metric.title, days: metric.weekDays(days: repo.days), style: metric.style)
                        .overlay(alignment: .topTrailing) {
                            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(VivaStyle.textSecondary).padding(VivaStyle.cardPadding)
                        }
                }
                .buttonStyle(VivaPressStyle())
            }
        }
        .sheet(isPresented: $showInfo) {
            VivaInfoSheet(title: infoTitle, paragraphs: [pillar.summary] + rows.map { "\($0.title): \($0.summary)" })
        }
    }
}

struct VivaRecoveryDetailView: View {
    @EnvironmentObject private var repo: Repository
    private let homeScore: Double??
    private let scoreNote: String?
    private let sleep: (value: Double?, note: String?)?

    init() { homeScore = nil; scoreNote = nil; sleep = nil }
    init(homeScore: Double?, note: String? = nil, sleep: Double? = nil, sleepNote: String? = nil) {
        self.homeScore = .some(homeScore)
        self.scoreNote = note
        self.sleep = (sleep, sleepNote)
    }

    private var todayRow: DailyMetric? {
        repo.today ?? repo.days.last(where: { $0.day == Repository.logicalDayKey(Date()) })
    }

    /// Why today has no Recovery of its own, naming the input that is actually missing.
    private var emptyNote: String? {
        guard todayRow?.recovery == nil else { return nil }
        let calibrating = RecoveryScorer.calibrationNights(
            nightlyHrv: repo.chargeBaselines?.hrvHistory.values ?? [],
            dayKeys: repo.chargeBaselines?.hrvHistory.dayKeys ?? [],
            hasRecovery: false)
        switch VivaConsistency.recoveryGap(calibrationNights: calibrating,
                                           hasSleepToday: todayRow?.totalSleepMin != nil,
                                           hasHrvToday: todayRow?.avgHrv != nil) {
        case .calibrating(let nights):
            return String(localized: "Learning your baseline, \(nights) of \(Baselines.minNightsSeed) nights.")
        case .noSleep:
            return String(localized: "No sleep has been recorded for last night yet, so today has no Recovery score.")
        case .noHRV:
            return String(localized: "Last night recorded no heart rate variability, so Recovery could not be scored. Check the strap was worn overnight and has synced.")
        case .notScoredYet:
            return String(localized: "Today's Recovery has not been scored yet.")
        }
    }

    var body: some View {
        VivaPillarDetail(
            pillar: .recovery, ringLabel: String(localized: "Recovery"), ringMaximum: 100, ringFormat: .percent,
            ringColor: { VivaStyle.recoveryColor(percent: $0) },
            score: homeScore ?? todayRow?.recovery,
            scoreNote: scoreNote,
            emptyNote: emptyNote,
            handed: sleep.map { [.sleepPerformance: $0] } ?? [:],
            rows: [.hrv, .restingHR, .respiratoryRate, .sleepPerformance],
            weekly: [.recovery, .hrv, .restingHR, .respiratoryRate, .sleepPerformance],
            infoTitle: String(localized: "What is Recovery?")) {
            NavigationLink(value: VivaHomeRoute.behaviorInsights) {
                VivaCard {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Behavior insights").font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                            Text("See which of your logged habits move your Recovery.")
                                .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                                .multilineTextAlignment(.leading)
                            VivaLinkLabel(String(localized: "See impacts"))
                        }
                        Spacer(minLength: 8)
                        Image(systemName: "checkmark.circle").font(.system(size: 40, weight: .ultraLight))
                            .foregroundStyle(VivaStyle.textTertiary)
                    }
                }
            }
            .buttonStyle(VivaPressStyle())
        }
    }
}

struct VivaStrainDetailView: View {
    @EnvironmentObject private var repo: Repository
    @EnvironmentObject private var router: NavRouter
    private let homeScore: Double??

    init() { homeScore = nil }
    init(homeScore: Double?) { self.homeScore = .some(homeScore) }

    private var score: Double? {
        homeScore ?? (repo.today ?? repo.days.last(where: { $0.day == Repository.logicalDayKey(Date()) }))
            .flatMap(VivaTrendMetric.strain.value)
    }

    var body: some View {
        VivaPillarDetail(
            pillar: .strain, ringLabel: String(localized: "Strain"), ringMaximum: 21, ringFormat: .decimal1,
            ringColor: { _ in VivaStyle.strain },
            score: score,
            emptyNote: score == nil ? String(localized: "Wear your strap through the day and your Strain will build here.") : nil,
            rows: [.steps, .calories],
            weekly: [.strain, .calories, .steps],
            infoTitle: String(localized: "What is Strain?")) {
            VivaCard(title: String(localized: "Activities")) {
                Text("Log a workout to see its time in each heart-rate zone and its share of today's Strain.")
                    .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: VivaStyle.cardGap) {
                    VivaCardButton(String(localized: "Add activity"), icon: "plus") { router.requestQuickActions() }
                    NavigationLink(value: TabRoute.workouts) {
                        HStack(spacing: 8) {
                            Image(systemName: "list.bullet").font(.system(size: 13, weight: .semibold))
                            Text("All workouts").font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                        }
                        .foregroundStyle(VivaStyle.textPrimary)
                        .frame(maxWidth: .infinity).frame(height: 40)
                        .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    }
                    .buttonStyle(VivaPressStyle())
                }
            }
        }
    }
}
#endif
