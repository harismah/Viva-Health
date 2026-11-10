#if os(iOS)
import SwiftUI
import StrandDesign
import WhoopStore

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
    let emptyNote: String
    let rows: [VivaTrendMetric]
    let weekly: [VivaTrendMetric]
    let infoTitle: String
    @ViewBuilder var extra: () -> Extra

    @EnvironmentObject private var repo: Repository
    @EnvironmentObject private var router: NavRouter
    @AppStorage("noop.coachEnabled") private var coachEnabled = true
    @State private var showInfo = false

    private var todayRow: DailyMetric? { repo.today ?? repo.days.last }

    private func baseline(_ metric: VivaTrendMetric) -> Double? {
        let todayKey = todayRow?.day ?? ""
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
            if score == nil {
                VivaCallout(text: emptyNote)
            }
            // A row with neither a value today nor a baseline has nothing to say, so it is left out
            // rather than shown as dashes beside a Home tile that found the figure another way.
            let rows = rows.filter { todayRow.flatMap($0.value) != nil || baseline($0) != nil }
            if !rows.isEmpty {
            VivaCard {
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.element.id) { index, metric in
                        let value = todayRow.flatMap(metric.value)
                        let base = baseline(metric)
                        NavigationLink(value: VivaHomeRoute.trend(metric)) {
                            VivaMetricRow(icon: metric.icon, label: metric.title, value: metric.text(value),
                                          baseline: base.map { metric.text($0) },
                                          trend: .comparing(value, to: base))
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

    init() { homeScore = nil }
    init(homeScore: Double?) { self.homeScore = .some(homeScore) }

    var body: some View {
        VivaPillarDetail(
            pillar: .recovery, ringLabel: String(localized: "Recovery"), ringMaximum: 100, ringFormat: .percent,
            ringColor: { VivaStyle.recoveryColor(percent: $0) },
            score: homeScore ?? (repo.today ?? repo.days.last)?.recovery,
            emptyNote: String(localized: "Viva needs a few nights of sleep to learn your baseline before it can score Recovery."),
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

    var body: some View {
        VivaPillarDetail(
            pillar: .strain, ringLabel: String(localized: "Strain"), ringMaximum: 21, ringFormat: .decimal1,
            ringColor: { _ in VivaStyle.strain },
            score: homeScore ?? (repo.today ?? repo.days.last).flatMap(VivaTrendMetric.strain.value),
            emptyNote: String(localized: "Wear your strap through the day and your Strain will build here."),
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
