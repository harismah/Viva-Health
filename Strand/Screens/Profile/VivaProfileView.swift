#if os(iOS)
import SwiftUI
import PhotosUI
import StrandDesign
import StrandAnalytics
import WhoopStore

/// Everything the Profile screen counts, derived once from the stored days and workouts.
/// Thresholds are stated where they are used so a number on screen can be traced to its rule:
/// a "strong sleep" is Sleep Performance of 70% or more, a "green recovery" is 67% or more, and a
/// "big effort" is Strain of 10 or more on the 0-21 scale.
struct VivaProfileStats {
    enum Period: String, CaseIterable, Identifiable {
        case month = "1M", quarter = "3M", allTime = "All time"
        var id: String { rawValue }
        var days: Int? { switch self { case .month: 30; case .quarter: 90; case .allTime: nil } }
    }

    struct Badge: Identifiable {
        enum Group: String, CaseIterable { case sleep = "Sleep", recovery = "Recovery", strain = "Strain" }
        let id: String
        let group: Group
        let title: String
        let detail: String
        let count: Int
    }

    let days: [DailyMetric]
    let workouts: [WorkoutRow]

    static let strongSleep = 70.0, optimalSleep = 85.0, greenRecovery = 67.0, bigEffort = 10.0

    private static func sleep(_ day: DailyMetric) -> Double? { VivaTrendMetric.sleepPerformance.value(day) }
    private static func strain(_ day: DailyMetric) -> Double? { VivaTrendMetric.strain.value(day) }

    @MainActor func window(_ period: Period) -> [DailyMetric] {
        guard let span = period.days else { return days }
        let cutoff = Repository.localDayKey(
            Calendar.current.date(byAdding: .day, value: -(span - 1), to: Repository.logicalDay(Date())) ?? Date())
        return days.filter { $0.day >= cutoff }
    }

    func recoveriesLogged() -> Int { days.filter { $0.recovery != nil }.count }

    @MainActor func dayStreak() -> Int {
        VivaProfileMath.currentStreak(
            days: Set(days.filter { $0.recovery != nil || $0.totalSleepMin != nil || $0.strain != nil }.map(\.day)),
            todayKey: Repository.logicalDayKey(Date()))
    }

    static func run(_ days: [DailyMetric], _ qualifies: (DailyMetric) -> Bool) -> Int {
        VivaProfileMath.longestRun(qualifyingDays: days.filter(qualifies).map(\.day))
    }

    var badges: [Badge] {
        let workoutDays = Set(workouts.map {
            Repository.localDayKey(Date(timeIntervalSince1970: TimeInterval($0.startTs)))
        })
        return [
            Badge(id: "sleep.optimal", group: .sleep, title: String(localized: "Deep Rest"),
                  detail: String(localized: "Nights at 85% Sleep Performance or more"),
                  count: days.filter { (Self.sleep($0) ?? 0) >= Self.optimalSleep }.count),
            Badge(id: "sleep.run", group: .sleep, title: String(localized: "Steady Sleeper"),
                  detail: String(localized: "Longest run of nights at 70% or more"),
                  count: Self.run(days) { (Self.sleep($0) ?? 0) >= Self.strongSleep }),
            Badge(id: "sleep.long", group: .sleep, title: String(localized: "Eight Hours"),
                  detail: String(localized: "Nights with eight hours asleep or more"),
                  count: days.filter { ($0.totalSleepMin ?? 0) >= 480 }.count),
            Badge(id: "recovery.green", group: .recovery, title: String(localized: "Green Light"),
                  detail: String(localized: "Days with Recovery of 67% or more"),
                  count: days.filter { ($0.recovery ?? 0) >= Self.greenRecovery }.count),
            Badge(id: "recovery.run", group: .recovery, title: String(localized: "Green Streak"),
                  detail: String(localized: "Longest run of green Recovery days"),
                  count: Self.run(days) { ($0.recovery ?? 0) >= Self.greenRecovery }),
            Badge(id: "recovery.peak", group: .recovery, title: String(localized: "Fully Charged"),
                  detail: String(localized: "Days with Recovery of 90% or more"),
                  count: days.filter { ($0.recovery ?? 0) >= 90 }.count),
            Badge(id: "strain.big", group: .strain, title: String(localized: "Big Effort"),
                  detail: String(localized: "Days with Strain of 10 or more"),
                  count: days.filter { (Self.strain($0) ?? 0) >= Self.bigEffort }.count),
            Badge(id: "strain.all", group: .strain, title: String(localized: "All Out"),
                  detail: String(localized: "Days with Strain of 18 or more"),
                  count: days.filter { (Self.strain($0) ?? 0) >= 18 }.count),
            Badge(id: "strain.active", group: .strain, title: String(localized: "On the Move"),
                  detail: String(localized: "Days with a logged workout"),
                  count: workoutDays.count),
        ]
    }
}

/// Profile: who you are, how long you have been recording, what you have built up, and your best
/// and most notable figures over a month, a quarter or all time.
struct VivaProfileView: View {
    @EnvironmentObject private var repo: Repository
    @EnvironmentObject private var profile: ProfileStore
    @EnvironmentObject private var router: NavRouter
    @AppStorage(VivaProfilePrefs.nameKey) private var name = ""
    @AppStorage(VivaProfilePrefs.countryKey) private var country = ""
    @AppStorage(VivaProfilePrefs.cityKey) private var city = ""

    @State private var workouts: [WorkoutRow] = []
    @State private var fitnessAge: Double?
    @State private var highlightPeriod: VivaProfileStats.Period = .allTime
    @State private var activityPeriod: VivaProfileStats.Period = .allTime

    private var stats: VivaProfileStats { VivaProfileStats(days: repo.days, workouts: workouts) }

    var body: some View {
        let stats = stats
        VivaDetailScaffold(title: "Profile", trailing: .gear, onTrailing: { showSettings = true }) {
            header
            HStack(alignment: .top, spacing: VivaStyle.cardGap) {
                tile(value: "\(stats.recoveriesLogged())", title: String(localized: "Recoveries"),
                     detail: String(localized: "scored so far"))
                NavigationLink(value: TabRoute.health) {
                    tile(value: fitnessAge.map { String(format: "%.1f", locale: AppLanguage.activeLocale, $0) } ?? "--",
                         title: String(localized: "Fitness age"),
                         detail: fitnessAge == nil ? String(localized: "needs more nights") : String(localized: "see how it is built"))
                }
                .buttonStyle(VivaPressStyle())
            }
            VivaCard(padding: 14) {
                HStack {
                    Text("Day streak").vivaCardTitle()
                    Spacer()
                    Image(systemName: "flame.fill").font(.system(size: 11)).foregroundStyle(VivaStyle.warning)
                    let streak = stats.dayStreak()
                    Text(streak == 1 ? "1 day" : "\(streak) days").font(VivaFont.number(13))
                        .foregroundStyle(VivaStyle.textPrimary)
                }
            }
            Button { showAI = true } label: {
                HStack(spacing: 12) {
                    Image(systemName: "sparkles").font(.system(size: 14)).foregroundStyle(VivaStyle.coachRing)
                    Text("Coach memory").vivaCardTitle()
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(VivaStyle.textSecondary)
                }
                .padding(.horizontal, 14).frame(height: 46)
                .background(VivaStyle.coachButton, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            .buttonStyle(VivaPressStyle())
            achievements(stats)
            VivaSectionHeader(title: String(localized: "Data Highlights"))
                .padding(.top, VivaStyle.sectionGap - VivaStyle.cardGap).id("highlights")
            highlights(stats)
            VivaSectionHeader(title: String(localized: "Activity Summary"))
                .padding(.top, VivaStyle.sectionGap - VivaStyle.cardGap).id("activity")
            activity(stats)
        }
        .task(id: repo.refreshSeq) {
            async let rows = repo.workoutRows()
            async let fit = repo.exploreSeries(key: "fitness_age", source: "my-whoop")
            let (loadedRows, fitSeries) = await (rows, fit)
            guard !Task.isCancelled else { return }
            workouts = loadedRows
            fitnessAge = fitSeries.last?.value
        }
        .navigationDestination(isPresented: $showSettings) { VivaSettingsHubView() }
        .navigationDestination(isPresented: $showEdit) { VivaEditProfileView() }
        .navigationDestination(isPresented: $showAllBadges) { VivaAchievementsView(badges: stats.badges) }
        .fullScreenCover(isPresented: $showAI) { NavigationStack { VivaAISettingsView() } }
    }

    @State private var showSettings = false
    @State private var showEdit = false
    @State private var showAllBadges = false
    @State private var showAI = false

    // MARK: Header

    private var memberSince: String? {
        guard let first = repo.days.first?.day, let date = BodyVitalSigns.dayParser.date(from: first) else { return nil }
        var style = Date.FormatStyle().month(.wide).year().locale(AppLanguage.activeLocale)
        style.timeZone = TimeZone(identifier: "UTC") ?? .current
        return date.formatted(style)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            VivaProfileAvatar(imageData: profile.avatarImageData, name: name, size: 64)
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(verbatim: name.isEmpty ? String(localized: "Your profile") : name)
                        .font(VivaFont.sectionTitle).foregroundStyle(VivaStyle.textPrimary)
                    Text(verbatim: ([String(localized: "\(profile.age) years old")] + [city, country].filter { !$0.isEmpty }).joined(separator: " · "))
                        .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                }
                Spacer()
                Button { showEdit = true } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "pencil").font(.system(size: 11, weight: .bold))
                        Text("Edit").font(.system(size: 10, weight: .bold)).tracking(1.2).textCase(.uppercase)
                    }
                    .foregroundStyle(VivaStyle.textPrimary)
                    .padding(.horizontal, 12).frame(height: 30)
                    .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                }
                .buttonStyle(VivaPressStyle())
            }
            if let memberSince {
                HStack(spacing: 8) {
                    BrandMark(size: 11)
                    Text("Recording since").font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                    Text(memberSince).font(.system(size: 12, weight: .semibold)).foregroundStyle(VivaStyle.textPrimary)
                    Spacer()
                }
                .padding(.horizontal, 12).frame(height: 34)
                .background(VivaStyle.inset, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
        }
        .padding(.horizontal, 4)
        .padding(.bottom, 8)
        .background(alignment: .top) {
            LinearGradient(colors: [VivaStyle.magenta.opacity(0.35), VivaStyle.magenta.opacity(0)],
                           startPoint: .top, endPoint: .bottom)
                .frame(height: 260)
                .padding(.horizontal, -VivaStyle.pagePadding)
                .offset(y: -90)
                .allowsHitTesting(false)
        }
    }

    private func tile(value: String, title: String, detail: String) -> some View {
        VStack(spacing: 6) {
            Text(value).font(VivaFont.number(26)).foregroundStyle(VivaStyle.textPrimary)
            Text(title).vivaCardTitle()
            Text(detail).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.vertical, 18)
        .frame(maxWidth: .infinity, minHeight: 118)
        .background(VivaStyle.card, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }

    // MARK: Achievements

    @ViewBuilder private func achievements(_ stats: VivaProfileStats) -> some View {
        let badges = stats.badges
        VivaSectionHeader(title: String(localized: "Achievements"),
                          action: .init(title: String(localized: "View all"), style: .link) { showAllBadges = true })
            .padding(.top, VivaStyle.sectionGap - VivaStyle.cardGap)
        HStack(alignment: .top, spacing: 0) {
            ForEach(["sleep.optimal", "strain.big", "recovery.green"], id: \.self) { id in
                if let badge = badges.first(where: { $0.id == id }) {
                    VivaBadgeView(badge: badge).frame(maxWidth: .infinity)
                }
            }
            Spacer(minLength: 0)
        }
    }

    // MARK: Highlights

    private func periodControl(_ selection: Binding<VivaProfileStats.Period>) -> some View {
        HStack(spacing: 0) {
            ForEach(VivaProfileStats.Period.allCases) { option in
                Button { selection.wrappedValue = option } label: {
                    Text(option.rawValue).font(.system(size: 10, weight: .bold)).textCase(.uppercase)
                        .foregroundStyle(selection.wrappedValue == option ? VivaStyle.textPrimary : VivaStyle.textSecondary)
                        .frame(maxWidth: .infinity).frame(height: 28)
                        .background(selection.wrappedValue == option ? VivaStyle.chipActive : Color.clear,
                                    in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selection.wrappedValue == option ? .isSelected : [])
            }
        }
        .padding(2)
        .background(VivaStyle.inset, in: RoundedRectangle(cornerRadius: 5, style: .continuous))
    }

    private func highlights(_ stats: VivaProfileStats) -> some View {
        let window = stats.window(highlightPeriod)
        let sleep = window.compactMap(VivaTrendMetric.sleepPerformance.value).max()
        let recovery = window.compactMap(\.recovery).max()
        let strain = window.compactMap(VivaTrendMetric.strain.value).max()
        let rhr = window.compactMap { $0.restingHr.map(Double.init) }
        let hrv = window.compactMap(\.avgHrv)
        return VivaCard {
            periodControl($highlightPeriod)
            HStack(alignment: .top, spacing: 0) {
                best(String(localized: "Best Sleep"),
                     VivaScoreRing(value: sleep, maximum: 100, color: VivaStyle.sleep, format: .percent, size: .small))
                best(String(localized: "Peak Recovery"),
                     VivaScoreRing(value: recovery, maximum: 100, color: VivaStyle.recoveryColor(percent: recovery),
                                   format: .percent, size: .small))
                best(String(localized: "Max Strain"),
                     VivaScoreRing(value: strain, maximum: 21, color: VivaStyle.strain, format: .decimal1, size: .small))
            }
            .padding(.vertical, 6)
            VivaDivider()
            Text("Longest streaks").vivaLabel()
            HStack(alignment: .top, spacing: 0) {
                streak("moon", VivaStyle.sleep, VivaProfileStats.run(window) {
                    (VivaTrendMetric.sleepPerformance.value($0) ?? 0) >= VivaProfileStats.strongSleep
                }, String(localized: "70%+ Sleep"))
                streak("leaf", VivaStyle.recoveryGreen, VivaProfileStats.run(window) {
                    ($0.recovery ?? 0) >= VivaProfileStats.greenRecovery
                }, String(localized: "Green Recovery"))
                streak("bolt", VivaStyle.strain, VivaProfileStats.run(window) {
                    (VivaTrendMetric.strain.value($0) ?? 0) >= VivaProfileStats.bigEffort
                }, String(localized: "10+ Strain"))
            }
            VivaDivider()
            Text("Notable stats").vivaLabel()
            VStack(spacing: 0) {
                stat("heart", String(localized: "Lowest RHR"), rhr.min().map { String(Int($0)) }, "bpm")
                stat("heart", String(localized: "Highest RHR"), rhr.max().map { String(Int($0)) }, "bpm")
                stat("waveform.path.ecg", String(localized: "Lowest HRV"), hrv.min().map { String(Int($0.rounded())) }, "ms")
                stat("waveform.path.ecg", String(localized: "Highest HRV"), hrv.max().map { String(Int($0.rounded())) }, "ms")
                stat("moon", String(localized: "Longest Sleep"),
                     window.compactMap(\.totalSleepMin).max().map { VivaTrendMetric.clock(minutes: $0) }, "hr")
                stat("arrow.triangle.2.circlepath", String(localized: "Lowest Recovery"),
                     window.compactMap(\.recovery).min().map { String(Int($0.rounded())) }, "%")
            }
        }
    }

    private func best(_ label: String, _ ring: VivaScoreRing) -> some View {
        VStack(spacing: 8) {
            ring.scaleEffect(0.82).frame(width: 72, height: 72)
            Text(label).font(VivaFont.caption).foregroundStyle(VivaStyle.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    private func streak(_ icon: String, _ color: Color, _ count: Int, _ label: String) -> some View {
        VStack(spacing: 5) {
            Image(systemName: icon).font(.system(size: 13)).foregroundStyle(color)
                .frame(width: 28, height: 28)
                .overlay(Circle().stroke(color.opacity(0.6), lineWidth: 1))
            Text(count == 1 ? "1 day" : "\(count) days").font(VivaFont.number(13)).foregroundStyle(VivaStyle.textPrimary)
            Text(label).font(.system(size: 10)).foregroundStyle(VivaStyle.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    private func stat(_ icon: String, _ label: String, _ value: String?, _ unit: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 12)).foregroundStyle(VivaStyle.warning)
                .frame(width: 26, height: 26)
                .overlay(Circle().stroke(VivaStyle.warning.opacity(0.5), lineWidth: 1))
            Text(label).font(.system(size: 14, weight: .semibold)).foregroundStyle(VivaStyle.textPrimary)
            Spacer()
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value ?? "--").font(VivaFont.number(17)).foregroundStyle(VivaStyle.textPrimary)
                if value != nil { Text(unit).font(.system(size: 10)).foregroundStyle(VivaStyle.textSecondary) }
            }
        }
        .frame(height: 42)
        .accessibilityElement(children: .combine)
    }

    // MARK: Activity

    private func activity(_ stats: VivaProfileStats) -> some View {
        let cutoffTs: Int? = activityPeriod.days.map { Int(Date().timeIntervalSince1970) - $0 * 86_400 }
        let rows = stats.workouts.filter { cutoffTs == nil || $0.startTs >= cutoffTs! }
        let bySport = Dictionary(grouping: rows) { WorkoutSource.displaySport($0.sport) }
            .map { (sport: $0.key, count: $0.value.count,
                    minutes: $0.value.reduce(0.0) { $0 + ($1.durationS ?? Double(max($1.endTs - $1.startTs, 0))) } / 60) }
            .sorted { $0.count > $1.count }
        return VivaCard {
            periodControl($activityPeriod)
            if bySport.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "figure.run").font(.system(size: 26, weight: .light)).foregroundStyle(VivaStyle.magenta)
                    Text("Start logging activities").font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                    Text("Workouts you log or that Viva detects are counted here by sport.")
                        .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary).multilineTextAlignment(.center)
                    Button { router.requestQuickActions() } label: {
                        Text("Add activity").font(.system(size: 10, weight: .bold)).tracking(1.2).textCase(.uppercase)
                            .foregroundStyle(VivaStyle.textPrimary)
                            .padding(.horizontal, 36).frame(height: 36)
                            .overlay(RoundedRectangle(cornerRadius: 6, style: .continuous).strokeBorder(VivaStyle.textPrimary, lineWidth: 1))
                    }
                    .buttonStyle(VivaPressStyle())
                    .padding(.top, 6)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 18)
            } else {
                VStack(spacing: 0) {
                    ForEach(bySport, id: \.sport) { entry in
                        HStack {
                            Text(verbatim: entry.sport).font(.system(size: 14, weight: .semibold)).foregroundStyle(VivaStyle.textPrimary)
                            Spacer()
                            Text(entry.count == 1 ? "1 session" : "\(entry.count) sessions").font(VivaFont.caption)
                                .foregroundStyle(VivaStyle.textSecondary)
                            Text(VivaTrendMetric.clock(minutes: entry.minutes)).font(VivaFont.number(15))
                                .foregroundStyle(VivaStyle.textPrimary).frame(minWidth: 52, alignment: .trailing)
                        }
                        .frame(height: 42)
                    }
                }
            }
        }
    }
}

enum VivaProfilePrefs {
    /// The display name on the Profile screen. Kept on this phone only.
    static let nameKey = "viva.profile.name"
    /// First and last name as entered on Edit Profile; `nameKey` holds the two joined.
    static let firstNameKey = "viva.profile.firstName"
    static let lastNameKey = "viva.profile.lastName"
    static let countryKey = "viva.profile.country"
    static let cityKey = "viva.profile.city"
}

/// The profile photo, or the wearer's initials on a tinted disc when there is none.
struct VivaProfileAvatar: View {
    let imageData: Data?
    let name: String
    let size: CGFloat

    var body: some View {
        let initials = VivaProfileMath.initials(name)
        if imageData == nil, !initials.isEmpty {
            Text(initials).font(.system(size: size * 0.4, weight: .bold)).foregroundStyle(VivaStyle.textPrimary)
                .frame(width: size, height: size)
                .background(LinearGradient(colors: [VivaStyle.chipGradientStart, VivaStyle.magenta],
                                           startPoint: .top, endPoint: .bottom), in: Circle())
                .accessibilityHidden(true)
        } else {
            ProfileAvatarView(imageData: imageData, size: size)
        }
    }
}

/// One achievement: a hexagon carrying how many times it has been earned, greyed until the first.
struct VivaBadgeView: View {
    let badge: VivaProfileStats.Badge

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "hexagon.fill").font(.system(size: 44, weight: .ultraLight))
                .foregroundStyle(badge.count > 0 ? VivaStyle.chipActive : VivaStyle.inset)
                .overlay(Image(systemName: "hexagon").font(.system(size: 44, weight: .ultraLight))
                    .foregroundStyle(badge.count > 0 ? VivaStyle.textSecondary : VivaStyle.ringTrack))
                .overlay(Text("\(badge.count)").font(VivaFont.number(15))
                    .foregroundStyle(badge.count > 0 ? VivaStyle.textPrimary : VivaStyle.textTertiary))
            Text(badge.title).font(.system(size: 11, weight: .medium)).foregroundStyle(VivaStyle.textPrimary)
                .multilineTextAlignment(.center)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(badge.title))
        .accessibilityValue(Text("\(badge.count). \(badge.detail)"))
    }
}

/// Every achievement, filterable by pillar, each with the rule that earns it.
struct VivaAchievementsView: View {
    let badges: [VivaProfileStats.Badge]
    @State private var filter: VivaProfileStats.Badge.Group?

    var body: some View {
        VivaDetailScaffold(title: "Achievements") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    chip(String(localized: "All"), nil)
                    ForEach(VivaProfileStats.Badge.Group.allCases, id: \.self) { chip($0.rawValue, $0) }
                }
            }
            ForEach(VivaProfileStats.Badge.Group.allCases.filter { filter == nil || filter == $0 }, id: \.self) { group in
                Text(group.rawValue).vivaLabel().padding(.top, 14)
                ForEach(badges.filter { $0.group == group }) { badge in
                    VivaCard(padding: 12) {
                        HStack(spacing: 14) {
                            VivaBadgeView(badge: badge).frame(width: 84)
                            Text(badge.detail).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 0)
                        }
                    }
                }
            }
        }
    }

    private func chip(_ title: String, _ group: VivaProfileStats.Badge.Group?) -> some View {
        Button { filter = group } label: {
            Text(title).font(.system(size: 12, weight: .semibold))
                .foregroundStyle(filter == group ? Color.black : VivaStyle.textPrimary)
                .padding(.horizontal, 12).frame(height: 30)
                .background(filter == group ? VivaStyle.textPrimary : VivaStyle.chip,
                            in: RoundedRectangle(cornerRadius: 6, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(filter == group ? .isSelected : [])
    }
}

/// Edit Profile: a form of labelled fields with one Save button. Nothing is written until Save,
/// so backing out discards the edit. Birthday, gender, height and weight save to the same
/// `ProfileStore` values Settings edits (they feed heart-rate zones, calories and Fitness Age);
/// the names, country and city are kept on this phone only.
struct VivaEditProfileView: View {
    @EnvironmentObject private var profile: ProfileStore
    @Environment(\.dismiss) private var dismiss
    @AppStorage(VivaProfilePrefs.nameKey) private var storedName = ""
    @AppStorage(VivaProfilePrefs.firstNameKey) private var storedFirst = ""
    @AppStorage(VivaProfilePrefs.lastNameKey) private var storedLast = ""
    @AppStorage(VivaProfilePrefs.countryKey) private var storedCountry = ""
    @AppStorage(VivaProfilePrefs.cityKey) private var storedCity = ""
    @AppStorage(UnitPrefs.systemKey) private var storedUnits = UnitSystem.metric.rawValue

    private struct Draft: Equatable {
        var first = "", last = "", country = "", city = ""
        var birthday = Date()
        var sex = "male"
        var imperial = false
        var heightCm = 170.0
        var weightKg = 70.0
    }
    private enum Sheet: String, Identifiable { case birthday, height, weight; var id: String { rawValue } }

    @State private var draft = Draft()
    @State private var saved = Draft()
    @State private var loaded = false
    @State private var sheet: Sheet?
    @State private var photoItem: PhotosPickerItem?

    private func current() -> Draft {
        // An install that only has the single-name preference shows it split at the first space.
        let parts = storedName.split(separator: " ", maxSplits: 1).map(String.init)
        return Draft(first: storedFirst.isEmpty ? (parts.first ?? "") : storedFirst,
                     last: storedLast.isEmpty && storedFirst.isEmpty ? (parts.count > 1 ? parts[1] : "") : storedLast,
                     country: storedCountry, city: storedCity,
                     birthday: profile.dateOfBirth, sex: profile.sex,
                     imperial: storedUnits == UnitSystem.imperial.rawValue,
                     heightCm: profile.heightCm, weightKg: profile.weightKg)
    }

    private func save() {
        let first = draft.first.trimmingCharacters(in: .whitespaces)
        let last = draft.last.trimmingCharacters(in: .whitespaces)
        storedFirst = first
        storedLast = last
        storedName = [first, last].filter { !$0.isEmpty }.joined(separator: " ")
        storedCountry = draft.country.trimmingCharacters(in: .whitespaces)
        storedCity = draft.city.trimmingCharacters(in: .whitespaces)
        storedUnits = (draft.imperial ? UnitSystem.imperial : UnitSystem.metric).rawValue
        if profile.dateOfBirth != draft.birthday { profile.dateOfBirth = draft.birthday }
        if profile.sex != draft.sex { profile.sex = draft.sex }
        if profile.heightCm != draft.heightCm { profile.heightCm = draft.heightCm }
        if profile.weightKg != draft.weightKg { profile.weightKg = draft.weightKg }
        dismiss()
    }

    private static func genderLabel(_ sex: String) -> String {
        switch sex {
        case "female": String(localized: "Woman")
        case "nonbinary": String(localized: "Non-binary")
        default: String(localized: "Man")
        }
    }

    private var heightText: String {
        if draft.imperial {
            let parts = UnitFormatter.cmToFeetInches(draft.heightCm)
            return "\(parts.feet)' \(parts.inches)\""
        }
        return String(Int(draft.heightCm.rounded()))
    }

    private var weightText: String {
        draft.imperial ? String(Int((draft.weightKg * 2.20462).rounded()))
                       : String(format: "%.1f", locale: AppLanguage.activeLocale, draft.weightKg)
    }

    var body: some View {
        let changed = draft != saved
        VivaDetailScaffold(title: "Edit Profile", spacing: 0) {
            // The photo and name scroll away with the form; only the top bar and Save stay put.
            PhotosPicker(selection: $photoItem, matching: .images) {
                VivaProfileAvatar(imageData: profile.avatarImageData,
                                  name: [draft.first, draft.last].joined(separator: " "), size: 98)
                    .overlay(alignment: .bottomTrailing) {
                        Image(systemName: "pencil").font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(VivaStyle.textPrimary)
                            .frame(width: 36, height: 36)
                            .background(VivaStyle.card, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                            .offset(x: 18, y: 0)
                    }
            }
            .buttonStyle(VivaPressStyle())
            .accessibilityLabel(Text("Change photo"))
            .padding(.top, 6)
            Text(verbatim: {
                let full = [draft.first, draft.last].map { $0.trimmingCharacters(in: .whitespaces) }
                    .filter { !$0.isEmpty }.joined(separator: " ")
                return full.isEmpty ? String(localized: "Your profile") : full
            }())
                .font(VivaFont.sectionTitle).foregroundStyle(VivaStyle.textPrimary)
                .padding(.top, 18)
            label("First name")
            textField($draft.first, contentType: .givenName)
            label("Last name")
            textField($draft.last, contentType: .familyName)
            label("Birthday")
            valueField(draft.birthday.formatted(.dateTime.month(.abbreviated).day().year().locale(AppLanguage.activeLocale))) {
                sheet = .birthday
            }
            label("Country")
            textField($draft.country, contentType: .countryName)
            label("City")
            textField($draft.city, contentType: .addressCity)
            label("Gender")
            Menu {
                ForEach(["male", "female", "nonbinary"], id: \.self) { option in
                    Button(Self.genderLabel(option)) { draft.sex = option }
                }
            } label: {
                fieldBody { Text(Self.genderLabel(draft.sex)).font(Self.valueFont).foregroundStyle(VivaStyle.textPrimary) }
            }
            label("Units")
            HStack(spacing: 0) {
                unitButton("Imperial", selected: draft.imperial) { draft.imperial = true }
                unitButton("Metric", selected: !draft.imperial) { draft.imperial = false }
            }
            .padding(4)
            .background(VivaStyle.canvasBottom, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            HStack(alignment: .top, spacing: 32) {
                VStack(alignment: .leading, spacing: 0) {
                    label("Height")
                    measureField(heightText, unit: draft.imperial ? "ft" : "cm") { sheet = .height }
                }
                VStack(alignment: .leading, spacing: 0) {
                    label("Weight")
                    measureField(weightText, unit: draft.imperial ? "lbs" : "kg") { sheet = .weight }
                }
            }
        }
        // Save is pinned under the form, so it is always in reach however far the fields scroll.
        .safeAreaInset(edge: .bottom, spacing: 0) {
            Button(action: save) {
                Text("Save").font(.system(size: 12, weight: .bold)).tracking(1.6).textCase(.uppercase)
                    .foregroundStyle(changed ? Color.black : VivaStyle.textTertiary)
                    .frame(maxWidth: .infinity).frame(height: 48)
                    .background(changed ? VivaStyle.textPrimary : VivaStyle.chipActive,
                                in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(VivaPressStyle())
            .disabled(!changed)
            .padding(.horizontal, VivaStyle.pagePadding)
            .padding(.top, 10)
            .padding(.bottom, 8)
            .background(VivaStyle.canvasBottom)
        }
        .scrollDismissesKeyboard(.interactively)
        .onChangeCompat(of: photoItem) { item in
            guard let item else { return }
            // The photo saves at once, like the photo control in App Settings; it is not part of the draft.
            Task {
                let data = try? await item.loadTransferable(type: Data.self)
                await MainActor.run {
                    if let data { profile.setAvatar(data) }
                    photoItem = nil   // so the same photo can be picked again
                }
            }
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            saved = current()
            draft = saved
        }
        .sheet(item: $sheet) { which in
            VStack(spacing: 8) {
                Text(which == .birthday ? "Birthday" : which == .height ? "Height" : "Weight").vivaCardTitle().padding(.top, 22)
                switch which {
                case .birthday:
                    DatePicker("", selection: $draft.birthday, in: ProfileStore.dateOfBirthRange, displayedComponents: .date)
                        .datePickerStyle(.wheel).labelsHidden()
                case .height: heightPicker
                case .weight: weightPicker
                }
                VivaCardButton(String(localized: "Done")) { sheet = nil }.padding(.horizontal, 20)
            }
            .presentationDetents([.height(340)])
            .presentationBackground(VivaStyle.card)
        }
    }

    // MARK: Pickers

    @ViewBuilder private var heightPicker: some View {
        if draft.imperial {
            let totalInches = Binding<Int>(
                get: { Int((draft.heightCm / 2.54).rounded()) },
                set: { draft.heightCm = Double($0) * 2.54 })
            Picker("", selection: totalInches) {
                ForEach(40...90, id: \.self) { inches in Text("\(inches / 12)' \(inches % 12)\"").tag(inches) }
            }
            .pickerStyle(.wheel).labelsHidden()
        } else {
            let centimetres = Binding<Int>(get: { Int(draft.heightCm.rounded()) }, set: { draft.heightCm = Double($0) })
            Picker("", selection: centimetres) {
                ForEach(100...230, id: \.self) { Text("\($0) cm").tag($0) }
            }
            .pickerStyle(.wheel).labelsHidden()
        }
    }

    @ViewBuilder private var weightPicker: some View {
        if draft.imperial {
            let pounds = Binding<Int>(
                get: { Int((draft.weightKg * 2.20462).rounded()) },
                set: { draft.weightKg = Double($0) / 2.20462 })
            Picker("", selection: pounds) {
                ForEach(66...550, id: \.self) { Text("\($0) lbs").tag($0) }
            }
            .pickerStyle(.wheel).labelsHidden()
        } else {
            let halfKilos = Binding<Int>(
                get: { Int((draft.weightKg * 2).rounded()) },
                set: { draft.weightKg = Double($0) / 2 })
            Picker("", selection: halfKilos) {
                ForEach(60...500, id: \.self) { Text(String(format: "%.1f kg", Double($0) / 2)).tag($0) }
            }
            .pickerStyle(.wheel).labelsHidden()
        }
    }

    // MARK: Field pieces

    private static let valueFont = Font.system(size: 16, weight: .medium)

    private func label(_ text: LocalizedStringKey) -> some View {
        Text(text).font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
            .foregroundStyle(VivaStyle.textSecondary)
            .padding(.leading, 4).padding(.top, 20).padding(.bottom, 8)
    }

    private func fieldBody<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        content()
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 46, alignment: .leading)
            .background(VivaStyle.canvasBottom, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(VivaStyle.divider, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func textField(_ text: Binding<String>, contentType: UITextContentType) -> some View {
        fieldBody {
            TextField("", text: text).textContentType(contentType).autocorrectionDisabled()
                .font(Self.valueFont).foregroundStyle(VivaStyle.textPrimary).tint(VivaStyle.link)
        }
    }

    private func valueField(_ value: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            fieldBody { Text(value).font(Self.valueFont).foregroundStyle(VivaStyle.textPrimary) }
        }
        .buttonStyle(VivaPressStyle())
    }

    private func measureField(_ value: String, unit: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            fieldBody {
                HStack {
                    Text(value).font(VivaFont.number(17)).foregroundStyle(VivaStyle.textPrimary)
                    Spacer()
                    Text(unit).font(.system(size: 14)).foregroundStyle(VivaStyle.textTertiary)
                }
            }
        }
        .buttonStyle(VivaPressStyle())
        .accessibilityValue(Text("\(value) \(unit)"))
    }

    private func unitButton(_ title: LocalizedStringKey, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                .foregroundStyle(selected ? VivaStyle.textPrimary : VivaStyle.textSecondary)
                .frame(maxWidth: .infinity).frame(height: 40)
                .background(selected ? VivaStyle.card : Color.clear, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Settings: the short list of places to go, in place of one long page.
struct VivaSettingsHubView: View {
    @State private var showDevice = false
    @State private var showHow = false
    @State private var showApp = false

    private var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "") (\(info?["CFBundleVersion"] as? String ?? ""))"
    }

    var body: some View {
        VivaDetailScaffold(title: "Settings", leading: .back) {
            Text("Account & settings").vivaLabel().padding(.top, 8)
            Button { showDevice = true } label: { row("applewatch.side.right", String(localized: "Device settings")) }
                .buttonStyle(VivaPressStyle())
            Button { showApp = true } label: { row("gearshape", String(localized: "App settings")) }
                .buttonStyle(VivaPressStyle())
            NavigationLink { pushed(DataSourcesView()) } label: { row("externaldrive", String(localized: "Data sources")) }
                .buttonStyle(VivaPressStyle())
            NavigationLink { pushed(BackupSyncView()) } label: { row("arrow.triangle.2.circlepath", String(localized: "Backup")) }
                .buttonStyle(VivaPressStyle())
            Text("Support").vivaLabel().padding(.top, 18)
            Button { showHow = true } label: { row("book", String(localized: "How Viva works")) }
                .buttonStyle(VivaPressStyle())
            NavigationLink { pushed(TestCentreView()) } label: { row("stethoscope", String(localized: "Test centre")) }
                .buttonStyle(VivaPressStyle())
            Text("App version: \(version)").vivaLabel(VivaStyle.textTertiary)
                .frame(maxWidth: .infinity).padding(.top, 24)
        }
        .fullScreenCover(isPresented: $showDevice) { NavigationStack { VivaDeviceSettingsView() } }
        .sheet(isPresented: $showHow) { HowNoopWorksView(onClose: { showHow = false }) }
        .fullScreenCover(isPresented: $showApp) { NavigationStack { VivaAppSettingsView() } }
    }

    private func pushed<V: View>(_ view: V) -> some View {
        view.background(VivaCanvas())
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.hidden, for: .navigationBar)
    }

    private func row(_ icon: String, _ title: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 15)).foregroundStyle(VivaStyle.textSecondary).frame(width: 24)
            Text(title).vivaCardTitle()
            Spacer()
        }
        .padding(.horizontal, 14)
        .frame(height: 46)
        .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 3, style: .continuous))
    }
}
/// AI Settings: whether the coach keeps your conversation, and where your data goes when you use it.
struct VivaAISettingsView: View {
    @EnvironmentObject private var coach: AICoachEngine
    @State private var showCoachSettings = false

    var body: some View {
        VivaDetailScaffold(title: "AI Settings", leading: .close) {
            HStack {
                Text("Memory").font(.system(size: 14, weight: .bold)).tracking(1.6).textCase(.uppercase)
                    .foregroundStyle(VivaStyle.textPrimary)
                Spacer()
                Toggle("", isOn: $coach.memoryEnabled).labelsHidden().tint(VivaStyle.positive)
                    .accessibilityLabel(Text("Memory"))
            }
            .padding(.horizontal, 4).padding(.top, 14)
            Text("Keep your conversation with the coach on this phone, so it is still there the next time you open Viva. Each new day starts a fresh conversation. Switching this off deletes the saved conversation and stops saving new ones.")
                .font(VivaFont.body).foregroundStyle(VivaStyle.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)
            Rectangle().fill(VivaStyle.divider).frame(height: 1).padding(.vertical, 18)
            VivaCard {
                Text("Data privacy").font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                Text(coach.dataConsent
                     ? "Viva has no server. Your conversation is stored only on this phone. When you ask the coach something, your message and a summary of your health data go to the AI provider you chose, because you allowed it."
                     : "Viva has no server. Your conversation is stored only on this phone. When you ask the coach something, only your message goes to the AI provider you chose; your health data is not sent unless you allow it.")
                    .font(VivaFont.body).foregroundStyle(VivaStyle.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Button { showCoachSettings = true } label: {
                    VivaLinkLabel(String(localized: "Coach data settings"), color: VivaStyle.textPrimary)
                }
                .buttonStyle(VivaPressStyle())
                .padding(.top, 4)
            }
        }
        .sheet(isPresented: $showCoachSettings) { CoachSettingsView() }
    }
}
/// App Settings: one row per topic. Each row opens the existing screen or settings cards for
/// that topic, so nothing here is a second copy of a setting.
struct VivaAppSettingsView: View {
    @EnvironmentObject private var profile: ProfileStore
    @State private var showAI = false
    @State private var showHide = false

    var body: some View {
        VivaDetailScaffold(title: "App Settings", leading: .close, spacing: 10) {
            Color.clear.frame(height: 26)
            link("figure.run", "Activity settings") { VivaActivitySettingsView() }
            Button { showAI = true } label: { row("sparkles", "AI settings") }.buttonStyle(VivaPressStyle())
            link("square.and.arrow.down", "Data export") { VivaDataExportView() }
            link("link", "Integrations") { VivaIntegrationsView() }
            link("square.and.pencil", "Journal") { pushed(InsightsView()) }
            link("bell", "Notifications") { VivaNotificationsView() }
            link("globe", "Language preferences") { VivaLanguageView() }
            if profile.cycleAwarenessApplies {
                link("drop.circle", "Cycle insights") {
                    VivaDetailScaffold(title: "Cycle insights") { MenstrualCycleHomeCard() }
                }
            }
            Button { showHide = true } label: { row("eye.slash", "Hide metrics") }.buttonStyle(VivaPressStyle())
            link("gearshape", "All settings") { pushed(SettingsView()) }
        }
        .fullScreenCover(isPresented: $showAI) { NavigationStack { VivaAISettingsView() } }
        .sheet(isPresented: $showHide) { VivaHideMetricsSheet() }
    }

    private func pushed<V: View>(_ view: V) -> some View {
        view.background(VivaCanvas())
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.hidden, for: .navigationBar)
    }

    private func link<Destination: View>(_ icon: String, _ title: LocalizedStringKey,
                                         @ViewBuilder destination: @escaping () -> Destination) -> some View {
        NavigationLink(destination: destination) { row(icon, title) }.buttonStyle(VivaPressStyle())
    }

    private func row(_ icon: String, _ title: LocalizedStringKey) -> some View {
        HStack(spacing: 18) {
            Image(systemName: icon).font(.system(size: 19, weight: .light)).foregroundStyle(VivaStyle.textSecondary)
                .frame(width: 30)
            Text(title).font(.system(size: 13, weight: .bold)).tracking(1.8).textCase(.uppercase)
                .foregroundStyle(VivaStyle.textPrimary)
            Spacer()
        }
        .padding(.horizontal, 16)
        .frame(height: 56)
        .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

/// Opens the existing Home customisation editor, where metrics and sections are shown or hidden.
/// Holds the same stored preferences Home reads, so a change here is the change Home shows.
struct VivaHideMetricsSheet: View {
    @AppStorage(TodayLayoutPrefs.orderKey) private var sectionOrderRaw = ""
    @AppStorage(TodayLayoutPrefs.hiddenKey) private var hiddenSectionsRaw = ""
    @AppStorage(KeyMetricPrefs.layoutKey) private var keyMetricsRaw = ""
    @AppStorage("today.keyMetricsDetailed") private var keyMetricsDetailed = false
    @AppStorage("today.keyMetricsWindowDays") private var keyMetricsWindowDays = 14
    @AppStorage(DashboardCardPrefs.selectionKey) private var dashboardCardsRaw = ""
    @AppStorage(HostedCardPrefs.selectionKey) private var hostedCardsRaw = ""

    var body: some View {
        TodayCustomizationSheet(
            initialDestination: .keyMetrics,
            sectionOrderRaw: $sectionOrderRaw,
            hiddenSectionsRaw: $hiddenSectionsRaw,
            keyMetricsRaw: $keyMetricsRaw,
            keyMetricsDetailed: $keyMetricsDetailed,
            keyMetricsWindowDays: $keyMetricsWindowDays,
            dashboardCardsRaw: $dashboardCardsRaw,
            hostedCardsRaw: $hostedCardsRaw
        )
    }
}
#endif
