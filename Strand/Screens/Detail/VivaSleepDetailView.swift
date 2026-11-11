#if os(iOS)
import SwiftUI
import StrandDesign
import StrandAnalytics
import WhoopStore

/// Sleep detail: the Sleep Performance ring, what fed it, last night broken down by stage against
/// the wearer's typical night, and the week's trends. Reads the same `SleepModel` the full Sleep
/// screen builds, so every figure here agrees with that screen.
struct VivaSleepDetailView: View {
    @EnvironmentObject private var repo: Repository
    @EnvironmentObject private var router: NavRouter
    @AppStorage("noop.coachEnabled") private var coachEnabled = true

    @State private var model: SleepModel?
    @State private var loaded = false
    @State private var nightHR: [HRBucket] = []
    @State private var showInfo = false

    /// How many days before today the night on screen ended. The model describes the newest night
    /// that has stage data, which is not always last night, so the screen titles itself from this.
    private var nightOffset: Int {
        guard let model, !model.isStubNight else { return 0 }
        let ended = Date(timeIntervalSince1970: TimeInterval(model.night.session.endTs))
        return VivaConsistency.nightOffset(dayKey: Repository.localDayKey(ended),
                                           todayKey: Repository.logicalDayKey(Date()))
    }

    /// "Today", "Yesterday" or the weekday, the same wording Home's day stepper uses.
    private var nightTitle: String {
        switch nightOffset {
        case 0: return String(localized: "Today")
        case 1: return String(localized: "Yesterday")
        default:
            let ended = model.map { Date(timeIntervalSince1970: TimeInterval($0.night.session.endTs)) } ?? Date()
            return ended.formatted(.dateTime.weekday(.wide).locale(AppLanguage.activeLocale))
        }
    }

    var body: some View {
        VivaDetailScaffold(title: LocalizedStringKey(nightTitle), trailing: .info, onTrailing: { showInfo = true },
                           coachMessage: coachEnabled ? coachLine : nil, onCoach: { router.openCoach() }) {
            VivaScoreRing(value: model?.performance.latest, maximum: 100, color: VivaStyle.sleep,
                          format: .percent, size: .large, label: String(localized: "Sleep Performance"))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
            if nightOffset > 0 {
                VivaCallout(text: String(localized: "Last night has no sleep-stage data yet, so this is your most recent analysed night."))
            }
            if let model, !model.isStubNight {
                contributors(model)
                VivaSectionHeader(title: nightOffset > 0 ? String(localized: "That Night's Sleep")
                                      : String(localized: "Last Night's Sleep"),
                                  subtitle: String(localized: "Compared with your typical night"))
                    .overlay(alignment: .trailing) {
                        NavigationLink(value: TabRoute.sleep) {
                            HStack(spacing: 8) {
                                Text("Edit").font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                                Image(systemName: "pencil").font(.system(size: 13, weight: .semibold))
                            }
                            .foregroundStyle(VivaStyle.textPrimary)
                        }
                        .padding(.trailing, 4)
                    }
                    .padding(.top, VivaStyle.sectionGap - VivaStyle.cardGap)
                    .id("night")
                nightCard(model)
                hoursVsNeededCard(model).id("needed")
                consistencyCard(model)
                efficiencyCard(model)
                wakeEventsCard
            } else if loaded {
                VivaCallout(text: String(localized: "No sleep recorded yet. Wear your strap to bed and your night will appear here."))
            }
            VivaSectionHeader(title: String(localized: "Weekly Trends"))
                .padding(.top, VivaStyle.sectionGap - VivaStyle.cardGap)
                .id("trends")
            ForEach([VivaTrendMetric.sleepPerformance, .hoursOfSleep, .restorativeSleep, .sleepEfficiency]) { metric in
                NavigationLink(value: VivaHomeRoute.trend(metric)) {
                    VivaWeekBarCard(title: metric.title, days: metric.weekDays(days: repo.days),
                                    style: metric.style, action: nil)
                        .overlay(alignment: .topTrailing) {
                            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(VivaStyle.textSecondary).padding(VivaStyle.cardPadding)
                        }
                }
                .buttonStyle(VivaPressStyle())
            }
        }
        .task(id: repo.refreshSeq) { await load() }
        .sheet(isPresented: $showInfo) {
            VivaInfoSheet(title: String(localized: "What is Sleep Performance?"), paragraphs: [
                VivaTrendMetric.sleepPerformance.summary,
                String(localized: "Hours vs. Needed is the share of your sleep need you got. Sleep Consistency compares when you went to bed and woke with recent nights. Sleep Efficiency is the share of your time in bed spent asleep."),
                String(localized: "A score of 85% or more is optimal, 70 to 85% is sufficient, and under 70% is poor."),
            ])
        }
    }

    // MARK: Loading

    private func load() async {
        let sessions = await repo.allSleepSessions()
        let habitual = await repo.habitualMidsleepSec()
        let motions = await repo.sessionMotions(sessions: sessions)
        guard !Task.isCancelled else { return }
        let built = SleepModel.build(SleepModelInputs(
            days: repo.days, sleeps: repo.sleeps, allSessions: sessions,
            importedSleep: repo.importedSleep, habitualMidsleepSec: habitual, motionByStart: motions))
        model = built
        loaded = true
        if let night = built?.night {
            nightHR = await repo.hrBuckets(from: night.session.effectiveStartTs, to: night.session.endTs,
                                           bucketSeconds: 60)
        }
    }

    private var coachLine: String? {
        guard let model, let performance = model.performance.latest else { return nil }
        return String(localized: "Sleep Performance \(Int(performance.rounded()))%. Ask what moved it.")
    }

    // MARK: Contributors

    private static func level(_ percent: Double?) -> VivaLevelStrip.Level? {
        guard let percent else { return nil }
        if percent >= 85 { return .optimal }
        if percent >= 70 { return .sufficient }
        return .poor
    }

    private func contributors(_ model: SleepModel) -> some View {
        VivaCard {
            VStack(spacing: 0) {
                contributorRow("clock", String(localized: "Hours vs. Needed"), model.hoursVsNeeded.latest)
                VivaDivider()
                contributorRow("arrow.triangle.2.circlepath", String(localized: "Sleep Consistency"), model.consistency.latest)
                VivaDivider()
                contributorRow("bed.double", String(localized: "Sleep Efficiency"), model.efficiency.latest)
                VivaDivider()
                contributorRow("sparkles", String(localized: "Restorative Sleep"), model.restorative.latest)
            }
            HStack(spacing: 14) {
                ForEach([VivaLevelStrip.Level.poor, .sufficient, .optimal], id: \.rawValue) { level in
                    HStack(spacing: 8) {
                        Capsule().fill(VivaLevelStrip.color(level)).frame(width: 12, height: 3)
                        Text(level == .poor ? "Poor" : level == .sufficient ? "Sufficient" : "Optimal")
                            .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .frame(height: 32)
            .background(VivaStyle.inset, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
        }
    }

    private func contributorRow(_ icon: String, _ label: String, _ percent: Double?) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).font(.system(size: 15)).foregroundStyle(VivaStyle.textSecondary).frame(width: 22)
            Text(label).vivaCardTitle().lineLimit(1).minimumScaleFactor(0.75)
            Spacer(minLength: 8)
            if let percent {
                VivaLevelStrip(level: Self.level(percent))
                Text("\(Int(percent.rounded()))%").font(VivaFont.valueMedium).foregroundStyle(VivaStyle.textPrimary)
                    .frame(minWidth: 58, alignment: .trailing)
            } else {
                Text("More nights needed").font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
            }
        }
        .frame(height: 58)
    }

    // MARK: Last night

    private func nightCard(_ model: SleepModel) -> some View {
        let stages = model.night.stages
        let total = max(stages.total, 1)
        let hrValues = nightHR.map(\.bpm)
        return VivaCard(title: String(localized: "Hours of Sleep")) {
            VivaValueHeader(value: VivaTrendMetric.clock(minutes: stages.asleep),
                            baseline: model.typicalTotalMin.map { VivaTrendMetric.clock(minutes: $0) },
                            trend: .comparing(stages.asleep, to: model.typicalTotalMin))
            if hrValues.count > 1 {
                VivaLineChart(values: hrValues, color: VivaStyle.sleep,
                              yLabels: Self.axisLabels(hrValues),
                              startLabel: model.night.onsetText, endLabel: model.night.wakeText)
            } else {
                HStack {
                    Text(model.night.onsetText)
                    Spacer()
                    Text(model.night.wakeText)
                }
                .font(VivaFont.number(10, weight: .semibold))
                .foregroundStyle(VivaStyle.textPrimary)
            }
            VivaDivider()
            HStack {
                Image(systemName: "rectangle.dashed").font(.system(size: 11)).foregroundStyle(VivaStyle.textSecondary)
                Text("Typical range").vivaLabel()
                Spacer()
                Text("Duration").vivaLabel()
                Text(VivaTrendMetric.clock(minutes: stages.total)).font(VivaFont.valueSmall)
                    .foregroundStyle(VivaStyle.textPrimary)
            }
            stageRow(VivaStyle.stageAwake, String(localized: "Awake"), stages.awake, total, typical: nil)
            stageRow(VivaStyle.stageLight, String(localized: "Light"), stages.light, total, typical: model.typicalLightMin)
            stageRow(VivaStyle.stageDeep, String(localized: "SWS (Deep)"), stages.deep, total, typical: model.typicalDeepMin)
            stageRow(VivaStyle.stageREM, String(localized: "REM"), stages.rem, total, typical: model.typicalRemMin)
            VivaDivider()
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(LinearGradient(colors: [VivaStyle.stageDeep, VivaStyle.stageREM],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 12, height: 12)
                Text("Restorative Sleep").vivaCardTitle()
                Spacer()
                VStack(alignment: .trailing, spacing: 1) {
                    Text(VivaTrendMetric.clock(minutes: stages.deep + stages.rem))
                        .font(VivaFont.valueMedium).foregroundStyle(VivaStyle.textPrimary)
                    if let deep = model.typicalDeepMin, let rem = model.typicalRemMin {
                        Text(VivaTrendMetric.clock(minutes: deep + rem))
                            .font(VivaFont.baseline).foregroundStyle(VivaStyle.textTertiary)
                    }
                }
            }
        }
    }

    private func stageRow(_ color: Color, _ label: String, _ minutes: Double, _ total: Double,
                          typical: Double?) -> some View {
        VivaStageRow(color: color, label: label, percent: Int((minutes / total * 100).rounded()),
                     duration: VivaTrendMetric.clock(minutes: minutes), value: minutes,
                     range: typical.map { ($0 * 0.9)...($0 * 1.1) }, maximum: total)
    }

    private static func axisLabels(_ values: [Double]) -> [String] {
        guard let low = values.min(), let high = values.max(), high > low else { return [] }
        return stride(from: 0, through: 3, by: 1).map { step in
            String(Int((high - (high - low) * Double(step) / 3).rounded()))
        }
    }

    // MARK: Cards

    private func hoursVsNeededCard(_ model: SleepModel) -> some View {
        let asleep = model.night.stages.asleep
        let need = SleepModel.sleepNeedMin(days: repo.days)
        let scale = max(asleep, need, 1)
        return VivaCard(title: String(localized: "Hours vs. Needed")) {
            VivaValueHeader(value: model.hoursVsNeeded.latest.map { "\(Int($0.rounded()))%" } ?? "--",
                            baseline: model.hoursVsNeeded.typical.map { "\(Int($0.rounded()))%" },
                            trend: .comparing(model.hoursVsNeeded.latest, to: model.hoursVsNeeded.typical))
            VivaAmountBar(label: String(localized: "Hours of sleep"), value: VivaTrendMetric.clock(minutes: asleep),
                          fraction: asleep / scale, color: VivaStyle.sleep)
            VivaAmountBar(label: String(localized: "Sleep needed"), value: VivaTrendMetric.clock(minutes: need),
                          fraction: need / scale, color: VivaStyle.textTertiary)
        }
    }

    private func consistencyCard(_ model: SleepModel) -> some View {
        VivaCard(title: String(localized: "Sleep Consistency")) {
            if let value = model.consistency.latest {
                VivaValueHeader(value: "\(Int(value.rounded()))%",
                                baseline: model.consistency.typical.map { "\(Int($0.rounded()))%" },
                                trend: .comparing(value, to: model.consistency.typical))
                HStack {
                    Text("Bed").vivaLabel(); Text(model.night.onsetText).font(VivaFont.number(12)).foregroundStyle(VivaStyle.textPrimary)
                    Spacer()
                    Text("Wake").vivaLabel(); Text(model.night.wakeText).font(VivaFont.number(12)).foregroundStyle(VivaStyle.textPrimary)
                }
            } else {
                Text("--%").font(VivaFont.valueLarge).foregroundStyle(VivaStyle.textPrimary)
                VivaCallout(text: String(localized: "A few more nights are needed to compare your bed and wake times."),
                            fill: VivaStyle.inset)
            }
        }
    }

    private func efficiencyCard(_ model: SleepModel) -> some View {
        let stages = model.night.stages
        let total = max(stages.total, 1)
        return VivaCard(title: String(localized: "Sleep Efficiency")) {
            VivaValueHeader(value: model.efficiency.latest.map { "\(Int($0.rounded()))%" } ?? "--",
                            baseline: model.efficiency.typical.map { "\(Int($0.rounded()))%" },
                            trend: .comparing(model.efficiency.latest, to: model.efficiency.typical))
            VivaAmountBar(label: String(localized: "Asleep"), value: VivaTrendMetric.clock(minutes: stages.asleep),
                          fraction: stages.asleep / total, color: VivaStyle.sleep)
            VivaAmountBar(label: String(localized: "Awake"), value: VivaTrendMetric.clock(minutes: stages.awake),
                          fraction: stages.awake / total, color: VivaStyle.stageAwake)
        }
    }

    @ViewBuilder private var wakeEventsCard: some View {
        if let events = repo.today?.disturbances ?? repo.days.last?.disturbances {
            VivaCard(padding: 14) {
                HStack {
                    RoundedRectangle(cornerRadius: 2).fill(VivaStyle.stageAwake).frame(width: 10, height: 10)
                    Text("Wake events").vivaCardTitle()
                    Spacer()
                    Text("\(events)").font(VivaFont.valueSmall).foregroundStyle(VivaStyle.textPrimary)
                }
            }
        }
    }
}
#endif
