#if os(iOS)
import SwiftUI
import StrandDesign

// MARK: - Home data
//
// Everything the Viva home renders, as plain values. `LiquidTodayView` owns the loading, the day
// navigation and the sheets; it maps its state into this struct so the layout below stays free of
// data access and can be read top to bottom as the screen.
//
// Source of each value (all read in LiquidTodayView.vivaHomeData):
//   sleepPercent     restScore (sleep_performance for the selected day)
//   recoveryPercent  chargeDisplay.pct
//   strain           effortStrain(displayDay), shown on the 0-21 scale
//   stress           StressModel score (0-3)
//   lastSleep        newest repo.sleeps session ending on the selected day
//   alarm            BehaviorStore smart alarm + WindDownNudge sleep need, via VivaAlarmReadout
//   journal          VivaJournalWeekCard loads repo.nativeJournalDays itself
//   metrics          the wearer's enabled Key Metrics, baselines from the prior 30 repo.days
//   week             last seven repo.days (strain + recovery)

struct VivaHomeData {
    struct Activity: Identifiable {
        let id = UUID()
        let icon: String
        let title: String
        let value: String
        let start: String?
        let end: String?
        let color: Color
        let route: TabRoute
    }

    struct Metric: Identifiable {
        let id: String
        let icon: String
        let label: String
        let value: String
        let unit: String?
        let baseline: String?
        let trend: VivaMetricRow.Trend
        let route: TabRoute?
    }

    struct WeekDay: Identifiable {
        let id: String
        let label: String
        let date: Int
        let strain: Double?
        let recovery: Double?
        let isToday: Bool
    }

    var dayTitle: String
    var canGoForward: Bool
    var canGoBack: Bool
    var avatar: Data?

    var sleepPercent: Double?
    var recoveryPercent: Double?
    var strain: Double?

    var sleepDuration: String?
    var healthStatus: String
    var stress: Double?
    var stressLabel: String

    var activities: [Activity]
    var alarm: VivaAlarmReadout
    var isToday: Bool
    var coachEnabled: Bool
    var calibrationNights: Int?
    var metrics: [Metric]
    var week: [WeekDay]

    var onPreviousDay: () -> Void
    var onNextDay: () -> Void
    var onPickDay: () -> Void
    var onProfile: () -> Void
    var onAdd: () -> Void
    var onStartActivity: () -> Void
    var onCoach: () -> Void
    var onCustomize: () -> Void
}

/// The screens Home pushes. Registered once on the Home stack (`vivaHomeDestinations`).
enum VivaHomeRoute: Hashable {
    case sleep, recovery, strain, healthMonitor, stressMonitor, sleepPlanner, behaviorInsights
}

extension View {
    func vivaHomeDestinations() -> some View {
        navigationDestination(for: VivaHomeRoute.self) { route in
            switch route {
            case .sleep: VivaSleepDetailView()
            case .recovery: VivaRecoveryDetailView()
            case .strain: VivaStrainDetailView()
            case .healthMonitor: VivaHealthMonitorView()
            case .stressMonitor: VivaStressMonitorView()
            case .sleepPlanner: VivaSleepPlannerView()
            case .behaviorInsights: VivaBehaviorInsightsView()
            }
        }
    }
}

// MARK: - Home layout

struct VivaHomeContent: View {
    let data: VivaHomeData
    /// Hosts the existing strap battery control so its sync behaviour is unchanged.
    let battery: AnyView

    var body: some View {
        VStack(alignment: .leading, spacing: VivaStyle.cardGap) {
            topBar
            Text("VIVA")
                .font(VivaFont.wordmark)
                .tracking(VivaFont.wordmarkTracking + 2)
                .foregroundStyle(VivaStyle.textPrimary)
                .frame(maxWidth: .infinity)
                .padding(.top, 14)
                .accessibilityHidden(true)
            rings.padding(.vertical, 6)
            if data.sleepDuration != nil { sleepPrompt }
            monitors
            VivaSectionHeader(title: String(localized: "My Day"),
                              action: .init(title: String(localized: "Add"), style: .plus, handler: data.onAdd))
                .padding(.top, VivaStyle.sectionGap - VivaStyle.cardGap)
            if data.coachEnabled { askRow }
            activitiesCard
            if data.isToday {
                tonightCard.id("tonight")
                VivaJournalWeekCard()
            }
            if let nights = data.calibrationNights { calibrationCard(nights) }
            VivaSectionHeader(title: String(localized: "My Dashboard"),
                              action: .init(title: String(localized: "Customize"), style: .chip, handler: data.onCustomize))
                .padding(.top, VivaStyle.sectionGap - VivaStyle.cardGap)
                .id("dashboard")
            ForEach(data.metrics) { metric in metricCard(metric) }
            NavigationLink(value: TabRoute.metricExplorer) {
                VivaCard(padding: 14) {
                    VivaMetricRow(icon: "square.grid.2x2", label: String(localized: "All metrics"),
                                  value: "", showsChevron: true)
                }
            }
            .buttonStyle(VivaPressStyle())
            weekCard.padding(.top, 6).id("week")
        }
    }

    // MARK: Top bar

    private var topBar: some View {
        HStack(spacing: 8) {
            Button(action: data.onProfile) {
                ProfileAvatarView(imageData: data.avatar, size: 30)
                    .overlay(Circle().strokeBorder(VivaStyle.textPrimary, lineWidth: 1.5))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Profile and settings"))
            Spacer(minLength: 0)
            battery
        }
        .frame(height: 36)
        .overlay { dayStepper }
    }

    private var dayStepper: some View {
        HStack(spacing: 0) {
            stepButton("chevron.left", enabled: data.canGoBack, action: data.onPreviousDay)
                .accessibilityLabel(Text("Previous day"))
            Button(action: data.onPickDay) {
                Text(data.dayTitle)
                    .font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                    .foregroundStyle(VivaStyle.textPrimary)
                    .lineLimit(1)
                    .padding(.horizontal, 18)
                    .frame(height: 28)
                    .background(VivaStyle.chipActive, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            }
            .buttonStyle(.plain)
            stepButton("chevron.right", enabled: data.canGoForward, action: data.onNextDay)
                .accessibilityLabel(Text("Next day"))
        }
        .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
    }

    private func stepButton(_ symbol: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(enabled ? VivaStyle.textPrimary : VivaStyle.textTertiary)
                .frame(width: 30, height: 28)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    // MARK: Rings

    private var rings: some View {
        HStack(alignment: .top, spacing: 0) {
            ring(String(localized: "Sleep"), route: .sleep,
                 VivaScoreRing(value: data.sleepPercent, maximum: 100, color: VivaStyle.sleep,
                               format: .percent, size: .small, label: String(localized: "Sleep")))
            ring(String(localized: "Recovery"), route: .recovery,
                 VivaScoreRing(value: data.recoveryPercent, maximum: 100,
                               color: VivaStyle.recoveryColor(percent: data.recoveryPercent),
                               format: .percent, size: .small, label: String(localized: "Recovery")))
            ring(String(localized: "Strain"), route: .strain,
                 VivaScoreRing(value: data.strain, maximum: 21, color: VivaStyle.strain,
                               format: .decimal1, size: .small, label: String(localized: "Strain")))
        }
    }

    private func ring(_ label: String, route: VivaHomeRoute, _ ring: VivaScoreRing) -> some View {
        NavigationLink(value: route) {
            VStack(spacing: 10) {
                ring
                HStack(spacing: 5) {
                    Text(label).vivaCardTitle()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 8, weight: .heavy))
                        .foregroundStyle(VivaStyle.textSecondary)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(VivaPressStyle())
    }

    // MARK: Prompt + monitors

    private var sleepPrompt: some View {
        NavigationLink(value: VivaHomeRoute.sleep) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("How did you sleep?").font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                    Text("You slept \(data.sleepDuration ?? ""). See how the night broke down.")
                        .font(VivaFont.caption)
                        .foregroundStyle(VivaStyle.textSecondary)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                    VivaLinkLabel(String(localized: "Open sleep details"), color: VivaStyle.magenta)
                        .padding(.top, 2)
                }
                Spacer(minLength: 8)
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(VivaStyle.chipActive)
                        .frame(width: 58, height: 58)
                        .rotationEffect(.degrees(12))
                    Image(systemName: "moon.stars")
                        .font(.system(size: 22, weight: .light))
                        .foregroundStyle(VivaStyle.magenta)
                }
                .padding(.trailing, 8)
                .accessibilityHidden(true)
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(VivaStyle.inset, in: RoundedRectangle(cornerRadius: VivaStyle.cardRadius, style: .continuous))
        }
        .buttonStyle(VivaPressStyle())
    }

    private var monitors: some View {
        HStack(alignment: .top, spacing: VivaStyle.cardGap) {
            NavigationLink(value: VivaHomeRoute.healthMonitor) {
                VivaCard(title: String(localized: "Health Monitor"), accessory: .chevron) {
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 4, style: .continuous).fill(VivaStyle.chipActive)
                            .frame(width: 24, height: 24)
                            .overlay(Circle().fill(VivaStyle.textSecondary).frame(width: 6, height: 6))
                        Text(data.healthStatus).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                    }
                    .frame(height: 30)
                }
            }
            .buttonStyle(VivaPressStyle())
            NavigationLink(value: VivaHomeRoute.stressMonitor) {
                VivaCard(title: String(localized: "Stress Monitor"), accessory: .chevron) {
                    HStack(spacing: 8) {
                        Text(data.stress.map { String(format: "%.1f", $0) } ?? "--")
                            .font(VivaFont.number(13))
                            .foregroundStyle(VivaStyle.textPrimary)
                            .frame(width: 28, height: 24)
                            .background(VivaStyle.chipActive, in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        Text(data.stressLabel).vivaCardTitle().lineLimit(1).minimumScaleFactor(0.7)
                    }
                    .frame(height: 30)
                }
            }
            .buttonStyle(VivaPressStyle())
        }
    }

    // MARK: My Day

    private var askRow: some View {
        VivaCard(padding: 14, action: data.onCoach) {
            HStack(spacing: 10) {
                Circle().strokeBorder(VivaStyle.textSecondary, lineWidth: 1)
                    .frame(width: 20, height: 20)
                    .overlay(BrandMark(size: 10).opacity(0.8))
                Text("Ask a question, get support...").font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                Spacer(minLength: 4)
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(VivaStyle.textSecondary)
            }
        }
    }

    private var activitiesCard: some View {
        VivaCard(title: data.isToday ? String(localized: "Today's Activities") : String(localized: "Activities"),
                 accessory: .none, padding: 12) {
            if data.activities.isEmpty {
                Text("Nothing logged yet").font(VivaFont.caption).foregroundStyle(VivaStyle.textTertiary)
                    .frame(maxWidth: .infinity, minHeight: 40, alignment: .leading)
            } else {
                VStack(spacing: 8) {
                    ForEach(data.activities) { activity in
                        NavigationLink(value: activity.route) { activityRow(activity) }
                            .buttonStyle(VivaPressStyle())
                    }
                }
            }
            HStack(spacing: VivaStyle.cardGap) {
                VivaCardButton(String(localized: "Add activity"), icon: "plus", action: data.onAdd)
                VivaCardButton(String(localized: "Start activity"), icon: "stopwatch", action: data.onStartActivity)
            }
        }
    }

    private func activityRow(_ activity: VivaHomeData.Activity) -> some View {
        HStack(spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: activity.icon).font(.system(size: 14, weight: .semibold))
                Text(activity.value).font(VivaFont.number(15))
            }
            .foregroundStyle(VivaStyle.textPrimary)
            .frame(width: 92, height: 38)
            .background(activity.color, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
            Text(activity.title).vivaCardTitle().lineLimit(1).minimumScaleFactor(0.7)
            Spacer(minLength: 4)
            if activity.start != nil || activity.end != nil {
                VStack(alignment: .trailing, spacing: 6) {
                    Text(activity.start ?? "")
                    Text(activity.end ?? "")
                }
                .font(VivaFont.number(10, weight: .medium))
                .foregroundStyle(VivaStyle.textSecondary)
                Rectangle().fill(activity.color).frame(width: 1.5, height: 26)
            }
        }
        .padding(8)
        .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var tonightCard: some View {
        NavigationLink(value: VivaHomeRoute.sleepPlanner) {
            VivaCard(title: String(localized: "Tonight's Sleep"), accessory: .chevron) {
                HStack(alignment: .top, spacing: 0) {
                    planColumn(icon: "bed.double", time: data.alarm.bedtimeText,
                               caption: String(localized: "Recommended bedtime"), status: nil)
                    Rectangle().fill(VivaStyle.divider).frame(width: 44, height: 1).padding(.top, 12)
                    planColumn(icon: "alarm", time: data.alarm.wakeText,
                               caption: data.alarm.modeText, status: data.alarm.statusText)
                }
                .frame(maxWidth: .infinity)
                HStack(spacing: 8) {
                    Image(systemName: "pencil").font(.system(size: 13, weight: .semibold))
                    Text("Edit alarm").font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                }
                .foregroundStyle(VivaStyle.textPrimary)
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
        }
        .buttonStyle(VivaPressStyle())
    }

    private func planColumn(icon: String, time: String, caption: String, status: String?) -> some View {
        VStack(spacing: 5) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 13)).foregroundStyle(VivaStyle.textSecondary)
                Text(time).font(VivaFont.number(19)).foregroundStyle(VivaStyle.textPrimary)
            }
            if let status {
                HStack(spacing: 4) {
                    Circle().fill(data.alarm.isArmed ? VivaStyle.positive : VivaStyle.warning).frame(width: 5, height: 5)
                    Text(status).vivaLabel(data.alarm.isArmed ? VivaStyle.positive : VivaStyle.warning)
                }
            }
            Text(caption).vivaLabel().multilineTextAlignment(.center).frame(maxWidth: 110)
        }
        .frame(maxWidth: .infinity)
    }

    private func calibrationCard(_ nights: Int) -> some View {
        VivaCard(title: String(localized: "Calibration timeline")) {
            HStack(spacing: 12) {
                Text("Your scores sharpen as Viva learns your baseline. \(nights) of 7 nights recorded.")
                    .font(VivaFont.caption)
                    .foregroundStyle(VivaStyle.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 4)
                ZStack {
                    Circle().stroke(VivaStyle.ringTrack, style: StrokeStyle(lineWidth: 3, dash: [14, 4]))
                    Circle().trim(from: 0, to: CGFloat(min(nights, 7)) / 7)
                        .stroke(VivaStyle.magenta, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Text("\(min(nights, 7))/7").font(VivaFont.number(13)).foregroundStyle(VivaStyle.textPrimary)
                }
                .frame(width: 50, height: 50)
            }
        }
    }

    // MARK: Dashboard

    @ViewBuilder private func metricCard(_ metric: VivaHomeData.Metric) -> some View {
        let row = VivaCard(padding: 14) {
            VivaMetricRow(icon: metric.icon, label: metric.label, value: metric.value, unit: metric.unit,
                          baseline: metric.baseline, trend: metric.trend)
        }
        if let route = metric.route {
            NavigationLink(value: route) { row }.buttonStyle(VivaPressStyle())
        } else {
            row
        }
    }

    private var weekCard: some View {
        VivaCard(title: String(localized: "Strain & Recovery")) {
            if data.week.allSatisfy({ $0.strain == nil && $0.recovery == nil }) {
                Text("No data").vivaLabel(VivaStyle.textPrimary).frame(maxWidth: .infinity, minHeight: 120)
            } else {
                VivaStrainRecoveryChart(days: data.week)
            }
        }
    }
}

/// The three pillar rings in miniature, pinned under the status bar once the hero trio scrolls away.
struct VivaHomePinnedRings: View {
    let data: VivaHomeData

    var body: some View {
        HStack(spacing: 0) {
            item(String(localized: "Sleep"), .sleep,
                 VivaScoreRing(value: data.sleepPercent, maximum: 100, color: VivaStyle.sleep,
                               format: .percent, size: .mini))
            item(String(localized: "Recovery"), .recovery,
                 VivaScoreRing(value: data.recoveryPercent, maximum: 100,
                               color: VivaStyle.recoveryColor(percent: data.recoveryPercent),
                               format: .percent, size: .mini))
            item(String(localized: "Strain"), .strain,
                 VivaScoreRing(value: data.strain, maximum: 21, color: VivaStyle.strain,
                               format: .decimal1, size: .mini))
        }
        .padding(.horizontal, VivaStyle.pagePadding)
        .frame(height: 40)
        .background(VivaStyle.canvasTop.ignoresSafeArea(edges: .top))
    }

    private func item(_ label: String, _ route: VivaHomeRoute, _ ring: VivaScoreRing) -> some View {
        NavigationLink(value: route) {
            HStack(spacing: 8) {
                ring
                Text(label).vivaCardTitle()
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(VivaPressStyle())
    }
}

extension View {
    /// Sets `pinned` once the enclosing scroll view has moved more than `threshold` points from its
    /// resting top, and clears it on the way back. Written only on the crossing.
    @ViewBuilder func vivaPinnedOnScroll(_ pinned: Binding<Bool>, past threshold: CGFloat) -> some View {
        if #available(iOS 18.0, *) {
            onScrollGeometryChange(for: Bool.self) { geometry in
                geometry.contentOffset.y + geometry.contentInsets.top > threshold
            } action: { _, isPast in
                pinned.wrappedValue = isPast
            }
        } else {
            self
        }
    }
}

/// Seven days of strain (blue line, left 0-21 axis) against recovery (banded dots, right % axis).
struct VivaStrainRecoveryChart: View {
    let days: [VivaHomeData.WeekDay]
    private let height: CGFloat = 150

    var body: some View {
        HStack(alignment: .top, spacing: 6) {
            axis(["21", "14", "7", "0"], colors: Array(repeating: VivaStyle.strain, count: 4))
            VStack(spacing: 6) {
                GeometryReader { proxy in
                    let step = proxy.size.width / CGFloat(max(days.count, 1))
                    ZStack(alignment: .topLeading) {
                        ForEach(0..<4, id: \.self) { index in
                            Rectangle().fill(VivaStyle.divider.opacity(0.7)).frame(height: 0.5)
                                .offset(y: proxy.size.height * CGFloat(index) / 3)
                        }
                        if let todayIndex = days.firstIndex(where: \.isToday) {
                            Rectangle().fill(VivaStyle.chip.opacity(0.75))
                                .frame(width: 22, height: proxy.size.height)
                                .offset(x: step * (CGFloat(todayIndex) + 0.5) - 11)
                        }
                        Path { path in
                            var started = false
                            for (index, day) in days.enumerated() {
                                guard let strain = day.strain else { continue }
                                let point = CGPoint(x: step * (CGFloat(index) + 0.5),
                                                    y: proxy.size.height * (1 - CGFloat(min(max(strain / 21, 0), 1))))
                                if started { path.addLine(to: point) } else { path.move(to: point); started = true }
                            }
                        }
                        .stroke(VivaStyle.strain, lineWidth: 1.5)
                        ForEach(Array(days.enumerated()), id: \.element.id) { index, day in
                            let x = step * (CGFloat(index) + 0.5)
                            if let strain = day.strain {
                                marker(VivaStyle.strain, text: String(format: "%.1f", strain), showText: true)
                                    .position(x: x, y: proxy.size.height * (1 - CGFloat(min(max(strain / 21, 0), 1))))
                            }
                            if let recovery = day.recovery {
                                marker(VivaStyle.recoveryColor(percent: recovery),
                                       text: "\(Int(recovery.rounded()))%", showText: day.isToday)
                                    .position(x: x, y: proxy.size.height * (1 - CGFloat(min(max(recovery / 100, 0), 1))))
                            }
                        }
                    }
                }
                .frame(height: height)
                HStack(spacing: 0) {
                    ForEach(days) { day in
                        VStack(spacing: 1) {
                            Text(day.label)
                            Text("\(day.date)")
                        }
                        .font(.system(size: 9, weight: day.isToday ? .bold : .regular))
                        .foregroundStyle(day.isToday ? VivaStyle.textPrimary : VivaStyle.textTertiary)
                        .frame(maxWidth: .infinity)
                    }
                }
            }
            axis(["100%", "66%", "33%", "0%"],
                 colors: [VivaStyle.recoveryGreen, VivaStyle.recoveryYellow, VivaStyle.recoveryRed, VivaStyle.recoveryRed])
        }
    }

    private func marker(_ color: Color, text: String, showText: Bool) -> some View {
        Circle().fill(VivaStyle.card).overlay(Circle().stroke(color, lineWidth: 1.5))
            .frame(width: 7, height: 7)
            .overlay(alignment: .bottom) {
                if showText {
                    Text(text).font(VivaFont.number(9)).foregroundStyle(color).fixedSize().offset(y: -9)
                }
            }
    }

    private func axis(_ labels: [String], colors: [Color]) -> some View {
        VStack {
            ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                Text(label).font(VivaFont.number(8, weight: .semibold)).foregroundStyle(colors[index])
                if index < labels.count - 1 { Spacer(minLength: 0) }
            }
        }
        .frame(height: height + 6)
        .offset(y: -3)
    }
}

/// The week strip of journal completion with the entry point to behaviour insights.
struct VivaJournalWeekCard: View {
    @EnvironmentObject private var repo: Repository
    @EnvironmentObject private var router: NavRouter
    @State private var logged: Set<String> = []

    private static func dayKeys() -> [(key: String, label: String)] {
        let calendar = Calendar.current
        let today = Date()
        return (0..<7).reversed().map { back in
            let date = calendar.date(byAdding: .day, value: -back, to: today) ?? today
            return (Repository.localDayKey(date),
                    date.formatted(.dateTime.weekday(.abbreviated).locale(AppLanguage.activeLocale)))
        }
    }

    var body: some View {
        let days = Self.dayKeys()
        VivaCard(title: String(localized: "My Journal"), accessory: .chevron, action: { router.openJournal() }) {
            HStack(spacing: 0) {
                ForEach(Array(days.enumerated()), id: \.element.key) { index, day in
                    let isToday = index == days.count - 1
                    VStack(spacing: 8) {
                        Text(day.label).vivaLabel(isToday ? VivaStyle.textPrimary : VivaStyle.textTertiary)
                        Circle()
                            .fill(logged.contains(day.key) ? VivaStyle.positive
                                  : (isToday ? VivaStyle.textTertiary : Color.clear))
                            .overlay(Circle().stroke(isToday ? VivaStyle.textPrimary : VivaStyle.textTertiary.opacity(0.6),
                                                     lineWidth: 1))
                            .frame(width: 20, height: 20)
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(day.label))
                    .accessibilityValue(Text(logged.contains(day.key) ? "Logged" : "Not logged"))
                }
            }
            .padding(.vertical, 8)
            NavigationLink(value: VivaHomeRoute.behaviorInsights) {
                HStack(spacing: 8) {
                    Image(systemName: "lightbulb").font(.system(size: 13, weight: .semibold))
                    Text("Behavior insights").font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                }
                .foregroundStyle(VivaStyle.textPrimary)
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            .buttonStyle(VivaPressStyle())
        }
        .task(id: repo.refreshSeq) {
            let keys = Self.dayKeys().map(\.key)
            logged = await repo.nativeJournalDays(from: keys.first ?? "", to: keys.last ?? "")
        }
    }
}
#endif
