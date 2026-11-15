#if os(iOS)
import SwiftUI
import StrandDesign
import StrandAnalytics
import WhoopStore

// The pages behind the App Settings list. Each one edits a setting that already existed, through
// the same stored key or the same function the full Settings page uses, so there is one value
// per setting however you reach it.

// MARK: - Shared pieces

/// A full-width navigation row: icon, uppercase title.
struct VivaSettingsRow: View {
    let icon: String
    let title: LocalizedStringKey
    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon).font(.system(size: 16, weight: .light)).foregroundStyle(VivaStyle.textSecondary)
                .frame(width: 26)
            Text(title).vivaCardTitle()
            Spacer()
        }
        .padding(.horizontal, 14)
        .frame(height: 46)
        .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 3, style: .continuous))
    }
}

/// An uppercase title with a switch on the right and a grey explanation underneath.
struct VivaToggleBlock: View {
    let title: LocalizedStringKey
    let detail: LocalizedStringKey
    @Binding var isOn: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title).vivaCardTitle()
                Spacer()
                Toggle("", isOn: $isOn).labelsHidden().tint(VivaStyle.positive).accessibilityLabel(Text(title))
            }
            Text(detail).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.trailing, 60)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
    }
}

// MARK: - Activity settings

struct VivaActivitySettingsView: View {
    var body: some View {
        VivaDetailScaffold(title: "Activity Settings", spacing: 6) {
            Color.clear.frame(height: 8)
            NavigationLink { VivaActivityDetectionView() } label: {
                VivaSettingsRow(icon: "figure.run", title: "Activity detection")
            }
            .buttonStyle(VivaPressStyle())
            NavigationLink { VivaHeartRateSettingsView() } label: {
                VivaSettingsRow(icon: "heart", title: "Heart rate settings")
            }
            .buttonStyle(VivaPressStyle())
        }
    }
}

struct VivaActivityDetectionView: View {
    @AppStorage(PuffinExperiment.autoDetectWorkoutsKey) private var autoDetect = false
    @AppStorage("workoutKeepScreenOn") private var keepScreenOn = false

    var body: some View {
        VivaDetailScaffold(title: "Activity Detection") {
            VivaToggleBlock(
                title: "Activity detection",
                detail: "After a sync, Viva looks over your recent heart rate for a sustained, raised stretch that looks like exercise.",
                isOn: $autoDetect)
            illustration.padding(.vertical, 28)
            Text("When it finds one, Viva offers to save it as a workout. It never saves one on its own, and it skips anything that overlaps a workout you already have.")
                .font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 12)
            VivaDivider().padding(.vertical, 18)
            VivaToggleBlock(
                title: "Keep screen on during a workout",
                detail: "Holds the screen awake while you are recording, so your live heart rate stays visible.",
                isOn: $keepScreenOn)
        }
    }

    /// A heart-rate trace with one raised stretch picked out: what detection looks for.
    private var illustration: some View {
        let heights: [CGFloat] = [8, 10, 7, 9, 12, 9, 11, 30, 36, 34, 38, 35, 37, 33, 12, 9, 14, 18, 12, 9, 11, 8]
        return HStack(alignment: .bottom, spacing: 3) {
            ForEach(Array(heights.enumerated()), id: \.offset) { index, height in
                RoundedRectangle(cornerRadius: 1)
                    .fill((7...13).contains(index) ? VivaStyle.strain : VivaStyle.ringTrack)
                    .frame(width: 10, height: height * 2)
            }
        }
        .frame(maxWidth: .infinity)
        .overlay(alignment: .top) {
            Image(systemName: "figure.run").font(.system(size: 12, weight: .bold)).foregroundStyle(VivaStyle.textPrimary)
                .frame(width: 26, height: 26)
                .background(VivaStyle.strain, in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                .offset(y: -34)
        }
        .padding(.top, 34)
        .accessibilityHidden(true)
    }
}

/// Heart Rate Zones: maximum heart rate and, optionally, hand-set zone boundaries. Edits are held
/// in a draft and written only on Save, through the same `ProfileStore` values the Settings page
/// writes; switching manual zones on or off asks first.
struct VivaHeartRateSettingsView: View {
    @EnvironmentObject private var profile: ProfileStore
    @EnvironmentObject private var repo: Repository
    @Environment(\.dismiss) private var dismiss

    @State private var maxHR = 0
    @State private var manual = false
    @State private var bounds: [Int] = []
    @State private var loaded = false
    @State private var confirmEnable = false
    @State private var confirmReset = false

    private static let colors: [Color] = [VivaStyle.link, VivaStyle.sleep, VivaStyle.positive, VivaStyle.warning, VivaStyle.recoveryRed]

    private var restingHR: Int? { (repo.today ?? repo.days.last(where: { $0.restingHr != nil }))?.restingHr }

    /// The five lower bounds shown: the draft when manual, otherwise the automatic ones for the draft max.
    private var shownBounds: [Int] {
        manual && bounds.count == 5 ? bounds : HRZones.defaultLowerBounds(maxHR: Double(maxHR))
    }

    private var changed: Bool {
        maxHR != profile.hrMax || manual != profile.hasCustomHRZones || (manual && bounds != profile.hrZoneThresholds)
    }

    private var valid: Bool {
        HRZones.customBPMRange.contains(maxHR) && (!manual || ProfileStore.validZoneThresholds(bounds))
    }

    private func load() {
        maxHR = profile.hrMax
        manual = profile.hasCustomHRZones
        bounds = manual ? profile.hrZoneThresholds : HRZones.defaultLowerBounds(maxHR: Double(maxHR))
    }

    private func save() {
        // A max equal to the age-based estimate is stored as "automatic" (0), as Settings does.
        let estimated = Int((208 - 0.7 * Double(profile.age)).rounded())
        let override = maxHR == estimated ? 0 : maxHR
        if profile.hrMaxOverride != override { profile.hrMaxOverride = override }
        let target = manual ? bounds : []
        if profile.hrZoneThresholds != target { profile.hrZoneThresholds = target }
        dismiss()
    }

    private func step(_ index: Int, up: Bool) {
        guard bounds.indices.contains(index) else { return }
        let floor = index == 0 ? HRZones.customBPMRange.lowerBound : bounds[index - 1] + 1
        let ceiling = index == bounds.count - 1 ? HRZones.customBPMRange.upperBound : bounds[index + 1] - 1
        guard floor <= ceiling else { return }
        bounds[index] = min(max(bounds[index] + (up ? 1 : -1), floor), ceiling)
    }

    var body: some View {
        VivaDetailScaffold(title: "Heart Rate Settings") {
            Text("Heart Rate Zones").font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
            Text("Your five zones are set from your maximum heart rate. They decide how much each minute of effort adds to Strain and how workout time is split by intensity.")
                .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack(alignment: .top, spacing: VivaStyle.cardGap) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Resting HR").vivaLabel()
                    box { Text(restingHR.map(String.init) ?? "--").foregroundStyle(VivaStyle.textTertiary) }
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("Max HR").vivaLabel()
                    box {
                        Text("\(maxHR)").foregroundStyle(VivaStyle.textPrimary)
                        Spacer()
                        stepper(down: { maxHR = max(maxHR - 1, 100) }, up: { maxHR = min(maxHR + 1, 230) })
                    }
                }
            }
            .padding(.top, 6)
            HStack {
                Text("Manual heart rate zones").font(.system(size: 14, weight: .semibold)).foregroundStyle(VivaStyle.textPrimary)
                Spacer()
                Toggle("", isOn: Binding(get: { manual }, set: { on in
                    if on { confirmEnable = true } else { confirmReset = true }
                }))
                .labelsHidden().tint(VivaStyle.positive)
                .accessibilityLabel(Text("Manual heart rate zones"))
            }
            .padding(.top, 14)
            Text("Zones follow your maximum heart rate automatically. Switch this on to set the boundaries yourself if the automatic ones do not feel right.")
                .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Text("Zone").vivaLabel().frame(width: 70, alignment: .leading)
                Text("Zone min").vivaLabel().frame(maxWidth: .infinity, alignment: .leading)
                Text("Zone max").vivaLabel().frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.top, 12)
            ForEach((0..<5).reversed(), id: \.self) { index in
                zoneRow(index)
            }
            if manual && !ProfileStore.validZoneThresholds(bounds) {
                Text("Each zone must start above the one below it.").font(VivaFont.caption).foregroundStyle(VivaStyle.warning)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 14) {
                Button(action: save) {
                    Text("Save HR zones").font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                        .foregroundStyle(changed && valid ? VivaStyle.textPrimary : VivaStyle.textTertiary)
                        .frame(maxWidth: .infinity).frame(height: 44)
                        .overlay(Capsule().strokeBorder(changed && valid ? VivaStyle.textPrimary : VivaStyle.divider, lineWidth: 1))
                }
                .buttonStyle(VivaPressStyle())
                .disabled(!changed || !valid)
                .padding(.horizontal, 48)
                Button { load() } label: {
                    Text("Cancel").font(.system(size: 10, weight: .bold)).tracking(1.4).textCase(.uppercase)
                        .foregroundStyle(changed ? VivaStyle.textPrimary : VivaStyle.textTertiary)
                }
                .buttonStyle(.plain)
                .disabled(!changed)
            }
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity)
            .background(VivaStyle.canvasBottom)
        }
        .onAppear { if !loaded { loaded = true; load() } }
        .alert("Change HR zones?", isPresented: $confirmEnable) {
            Button("Confirm") {
                manual = true
                bounds = HRZones.defaultLowerBounds(maxHR: Double(maxHR))
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Your own zone boundaries will be used for time in zones from now on. Nothing changes until you press Save, and you can go back to the automatic zones at any time.")
        }
        .alert("Reset HR zones?", isPresented: $confirmReset) {
            Button("Yes, reset HR zones", role: .destructive) {
                manual = false
                bounds = HRZones.defaultLowerBounds(maxHR: Double(maxHR))
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Your hand-set boundaries will be replaced by the automatic zones when you press Save.")
        }
    }

    private func zoneRow(_ index: Int) -> some View {
        let lower = shownBounds.indices.contains(index) ? shownBounds[index] : 0
        let upper = index == 4 ? maxHR : (shownBounds.indices.contains(index + 1) ? shownBounds[index + 1] - 1 : 0)
        return HStack(spacing: 8) {
            HStack(spacing: 8) {
                Rectangle().fill(Self.colors[index]).frame(width: 2, height: 30)
                Text("Zone \(index + 1)").font(.system(size: 13, weight: .semibold)).foregroundStyle(VivaStyle.textPrimary)
            }
            .frame(width: 70, alignment: .leading)
            box {
                Text("\(lower)").foregroundStyle(manual ? VivaStyle.textPrimary : VivaStyle.textTertiary)
                Spacer()
                if manual { stepper(down: { step(index, up: false) }, up: { step(index, up: true) }) }
                else { Text("bpm").font(.system(size: 11)).foregroundStyle(VivaStyle.textTertiary) }
            }
            box {
                Text("\(upper)").foregroundStyle(VivaStyle.textTertiary)
                Spacer()
                Text("bpm").font(.system(size: 11)).foregroundStyle(VivaStyle.textTertiary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func box<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        HStack(spacing: 4) { content() }
            .font(VivaFont.number(14))
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity, minHeight: 34, alignment: .leading)
            .background(VivaStyle.canvasBottom, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }

    private func stepper(down: @escaping () -> Void, up: @escaping () -> Void) -> some View {
        HStack(spacing: 2) {
            Button(action: down) { Image(systemName: "minus").frame(width: 26, height: 28) }
                .accessibilityLabel(Text("Decrease"))
            Button(action: up) { Image(systemName: "plus").frame(width: 26, height: 28) }
                .accessibilityLabel(Text("Increase"))
        }
        .font(.system(size: 11, weight: .bold))
        .buttonStyle(.plain)
        .foregroundStyle(VivaStyle.textPrimary)
    }
}

// MARK: - Data export

/// Data Export: write a complete backup, or a CSV archive, to a file you choose. Runs the same
/// exporters the Backup card on the Settings page runs.
struct VivaDataExportView: View {
    @EnvironmentObject private var model: AppModel
    @State private var busy = false
    @State private var result: String?
    @State private var failed = false

    var body: some View {
        VivaDetailScaffold(title: "Export Viva Data") {
            Text("Export a complete copy of your Sleep, Recovery, Strain, workout and journal data to a file you choose. It never leaves your phone unless you move it.")
                .font(VivaFont.body).foregroundStyle(VivaStyle.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 14)
            Text("A backup restores everything into Viva on this or another phone. A CSV archive opens in a spreadsheet.")
                .font(VivaFont.body).foregroundStyle(VivaStyle.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            if let result {
                VivaCard {
                    Text(failed ? "Export problem" : "Export saved").font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                    Text(verbatim: result).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 10)
            }
            NavigationLink {
                SettingsView(focus: .dataExport).background(VivaCanvas())
                    .navigationBarTitleDisplayMode(.inline).toolbarBackground(.hidden, for: .navigationBar)
            } label: {
                VivaLinkLabel(String(localized: "Restore a backup and more options"), color: VivaStyle.textPrimary)
            }
            .buttonStyle(VivaPressStyle())
            .padding(.top, 14)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 10) {
                Button { runBackup() } label: {
                    Text(busy ? "Preparing your files" : "Create export")
                        .font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                        .foregroundStyle(Color.black)
                        .frame(maxWidth: .infinity).frame(height: 44)
                        .background(VivaStyle.positive, in: Capsule())
                }
                .buttonStyle(VivaPressStyle())
                Button { runCSV() } label: {
                    Text("Export CSV archive")
                        .font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                        .foregroundStyle(VivaStyle.positive)
                        .frame(maxWidth: .infinity).frame(height: 44)
                        .overlay(Capsule().strokeBorder(VivaStyle.positive, lineWidth: 1.5))
                }
                .buttonStyle(VivaPressStyle())
            }
            .disabled(busy)
            .padding(.horizontal, VivaStyle.pagePadding)
            .padding(.vertical, 12)
            .background(VivaStyle.canvasBottom)
        }
    }

    private func runBackup() {
        busy = true
        Task {
            let outcome = await DataBackup.runExport(checkpoint: { await model.repo.checkpointForBackup() })
            busy = false
            switch outcome {
            case .exported(let url):
                failed = false
                result = String(localized: "Saved to \(url.lastPathComponent).")
            case .exportedOversize(let url, _, _):
                failed = false
                result = String(localized: "Saved to \(url.lastPathComponent). This backup is large; restoring it will ask you to confirm.")
            case .cancelled:
                break
            default:
                failed = true
                result = String(localized: "The backup could not be written. Open \"Restore a backup and more options\" for details.")
            }
        }
    }

    private func runCSV() {
        busy = true
        Task {
            let outcome = await CsvExport.run(repo: model.repo)
            busy = false
            switch outcome {
            case .cancelled: break
            case .exported(let url):
                failed = false
                result = String(localized: "Saved to \(url.lastPathComponent).")
            case .failure(let message):
                failed = true
                result = message
            }
        }
    }
}

// MARK: - Integrations

struct VivaIntegrationsView: View {
    @EnvironmentObject private var health: HealthKitBridge

    var body: some View {
        VivaDetailScaffold(title: "Integrations", spacing: 8) {
            NavigationLink { VivaAppleHealthConnectView() } label: {
                ZStack(alignment: .bottomLeading) {
                    LinearGradient(colors: [VivaStyle.chipActive, VivaStyle.card], startPoint: .topTrailing, endPoint: .bottomLeading)
                    Image(systemName: "heart.fill").font(.system(size: 70)).foregroundStyle(VivaStyle.recoveryRed.opacity(0.28))
                        .blur(radius: 5).frame(maxWidth: .infinity, alignment: .trailing).padding(.trailing, 60).padding(.bottom, 44)
                        .accessibilityHidden(true)
                    HStack(spacing: 12) {
                        Image(systemName: "heart.fill").font(.system(size: 11)).foregroundStyle(VivaStyle.card)
                            .frame(width: 22, height: 22)
                            .background(VivaStyle.textSecondary, in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                        Text("Apple Health").vivaCardTitle()
                        if health.auth == .authorized {
                            Text("Connected").vivaLabel(VivaStyle.positive)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 14, weight: .bold)).foregroundStyle(VivaStyle.textPrimary)
                    }
                    .padding(14)
                }
                .frame(height: 118)
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            .buttonStyle(VivaPressStyle())
            HStack(spacing: 10) {
                Text("Other sources").vivaLabel(VivaStyle.textTertiary).fixedSize()
                Rectangle().fill(VivaStyle.divider).frame(height: 1)
            }
            .padding(.top, 18).padding(.bottom, 6)
            NavigationLink { pushed(XiaomiBandView()) } label: { sourceRow("figure.walk.motion", "Mi Band") }
                .buttonStyle(VivaPressStyle())
            NavigationLink { pushed(ShortcutExportSettingsView()) } label: { sourceRow("square.and.arrow.up", "Shortcuts export") }
                .buttonStyle(VivaPressStyle())
            NavigationLink { pushed(DataSourcesView()) } label: { sourceRow("externaldrive", "All data sources") }
                .buttonStyle(VivaPressStyle())
            Text("Viva reads from these on your phone. It has no accounts with outside services and sends them nothing.")
                .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary).padding(.top, 14).padding(.horizontal, 4)
        }
    }

    private func sourceRow(_ icon: String, _ title: LocalizedStringKey) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon).font(.system(size: 17, weight: .light)).foregroundStyle(VivaStyle.textSecondary).frame(width: 26)
            Text(title).vivaCardTitle()
            Spacer()
        }
        .padding(.horizontal, 14)
        .frame(height: 54)
        .background(VivaStyle.card, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }

    private func pushed<V: View>(_ view: V) -> some View {
        view.background(VivaCanvas())
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.hidden, for: .navigationBar)
    }
}

/// Apple Health: a picture of the link, what connecting does, and one Connect button. Connecting
/// and syncing call exactly what the full Apple Health screen calls.
struct VivaAppleHealthConnectView: View {
    @EnvironmentObject private var health: HealthKitBridge
    @EnvironmentObject private var model: AppModel
    @State private var working = false

    private func run(requesting: Bool) {
        working = true
        Task {
            if requesting { await health.requestAuthorization() }
            await HealthSyncRefreshCoordinator.run(
                sync: { await health.sync() },
                refresh: { await model.refreshAfterAppleHealthSync(authorized: health.auth == .authorized) })
            working = false
        }
    }

    var body: some View {
        VivaDetailScaffold(title: "Apple Health") {
            diagram.padding(.top, 40).padding(.bottom, 130)
            switch health.auth {
            case .authorized:
                headline("Apple Health is connected")
                paragraph("Viva reads your heart rate, HRV, blood oxygen, respiratory rate, sleep, steps and energy from Apple Health, and writes what your strap records back to it.")
                if let last = health.lastSync {
                    paragraph("Last synced \(last.formatted(.relative(presentation: .named)))")
                }
                paragraph("You can change what Viva may read at any time in iOS Settings, under Health, Data Access & Devices.")
                NavigationLink {
                    AppleHealthView().background(VivaCanvas())
                        .navigationBarTitleDisplayMode(.inline).toolbarBackground(.hidden, for: .navigationBar)
                } label: { VivaLinkLabel(String(localized: "See imported data"), color: VivaStyle.textPrimary) }
                .buttonStyle(VivaPressStyle()).padding(.top, 8)
            case .unavailable:
                headline("Apple Health is not available")
                paragraph("This device does not offer Apple Health.")
            case .entitlementMissing:
                headline("This install cannot connect directly")
                paragraph("It was signed with a profile that does not include Apple's Health permission, so the Connect prompt can never appear.")
                paragraph("You can still bring your data in: import a Health export in Data Sources, or turn on Shortcuts Export.")
            case .unknown, .denied:
                headline("Turn on all Apple Health metrics")
                paragraph("Connecting lets Viva read your Apple Health data, from sleep to steps, to fill gaps your strap leaves, and write your strap's readings back. Everything stays on your phone.")
                if health.auth == .denied {
                    paragraph("If no prompt appears, switch Viva on in iOS Settings under Health, Data Access & Devices.")
                }
            }
            if let error = health.lastError {
                Text(verbatim: error).font(VivaFont.caption).foregroundStyle(VivaStyle.warning)
                    .fixedSize(horizontal: false, vertical: true).padding(.horizontal, 4)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            Group {
                switch health.auth {
                case .unknown, .denied: bottomButton("Connect") { run(requesting: true) }
                case .authorized: bottomButton(health.syncing || working ? "Syncing" : "Sync now") { run(requesting: false) }
                case .unavailable, .entitlementMissing: EmptyView()
                }
            }
            .disabled(working || health.syncing)
        }
    }

    private func bottomButton(_ title: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.system(size: 11, weight: .bold)).tracking(1.6).textCase(.uppercase)
                .foregroundStyle(Color.black)
                .frame(maxWidth: .infinity).frame(height: 44)
                .background(VivaStyle.textPrimary, in: RoundedRectangle(cornerRadius: 4, style: .continuous))
        }
        .buttonStyle(VivaPressStyle())
        .padding(.horizontal, 30)
        .padding(.top, 12)
        .padding(.bottom, 28)
        .background(VivaStyle.canvasBottom)
    }

    private func headline(_ text: LocalizedStringKey) -> some View {
        Text(text).font(.system(size: 17, weight: .semibold)).foregroundStyle(VivaStyle.textPrimary).padding(.horizontal, 14)
    }

    private func paragraph(_ text: LocalizedStringKey) -> some View {
        Text(text).font(.system(size: 15)).foregroundStyle(VivaStyle.textSecondary).lineSpacing(3)
            .fixedSize(horizontal: false, vertical: true).padding(.horizontal, 14)
    }

    /// Health on the left, Viva on the right, joined by a line; underneath, the kinds of data that cross.
    private var diagram: some View {
        VStack(spacing: 16) {
            HStack(spacing: 8) {
                Image(systemName: "heart.fill").font(.system(size: 24)).foregroundStyle(VivaStyle.recoveryRed)
                    .frame(width: 62, height: 62)
                    .background(VivaStyle.textPrimary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                HStack(spacing: 0) {
                    Circle().fill(VivaStyle.textTertiary).frame(width: 7, height: 7)
                    Rectangle().fill(health.auth == .authorized ? VivaStyle.positive : VivaStyle.textTertiary).frame(width: 46, height: 1)
                    Circle().fill(VivaStyle.textTertiary).frame(width: 7, height: 7)
                }
                BrandMark(size: 50)
                    .frame(width: 62, height: 62)
                    .background(Color.black, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(VivaStyle.divider, lineWidth: 1))
            }
            HStack(spacing: 16) {
                ForEach(["scalemass", "figure.run", "flame", "figure.mind.and.body", "heart"], id: \.self) { symbol in
                    Image(systemName: symbol).font(.system(size: 17, weight: .light)).foregroundStyle(VivaStyle.textPrimary)
                        .frame(width: 40, height: 40)
                        .background(VivaStyle.card, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
            }
            .padding(.horizontal, 18).padding(.vertical, 14)
            .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous)
                .strokeBorder(VivaStyle.textTertiary, style: StrokeStyle(lineWidth: 1, dash: [3, 3])))
        }
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }
}

// MARK: - Notifications

struct VivaNotificationsView: View {
    @EnvironmentObject private var coach: AICoachEngine
    @AppStorage(PuffinExperiment.journalReminderKey) private var journalReminder = true
    @AppStorage(UnitPrefs.liveActivityKey) private var liveHeartRate = true
    @AppStorage(UnitPrefs.liftLiveActivityKey) private var liftSession = true
    @AppStorage(UnitPrefs.syncLiveActivityKey) private var strapSync = true
    @AppStorage("noop.coachEnabled") private var coachEnabled = true
    @State private var morningBrief = CoachBriefScheduler.isEnabled
    @State private var windDown = WindDownNudge.isEnabled
    @State private var denied = false

    var body: some View {
        VivaDetailScaffold(title: "Notifications", spacing: 2) {
            if coachEnabled {
                VivaToggleBlock(title: "Morning brief",
                                detail: "A short coach summary of your night, delivered each morning.",
                                isOn: $morningBrief)
            }
            VivaToggleBlock(title: "Wind-down reminder",
                            detail: "A nudge in the evening when it is time to start getting ready for bed.",
                            isOn: $windDown)
            VivaToggleBlock(title: "Journal reminder",
                            detail: "A card on Home reminding you to log your journal.",
                            isOn: $journalReminder)
            VivaToggleBlock(title: "Live heart rate",
                            detail: "Shows your live heart rate on the Lock Screen while it is streaming.",
                            isOn: $liveHeartRate)
            VivaToggleBlock(title: "Lift session",
                            detail: "Shows a running gym session on the Lock Screen.",
                            isOn: $liftSession)
            VivaToggleBlock(title: "Strap sync",
                            detail: "Shows progress on the Lock Screen while the strap is syncing.",
                            isOn: $strapSync)
        }
        .onChangeCompat(of: morningBrief) { on in
            CoachBriefScheduler.setEnabled(on, generateBrief: { await coach.generateBrief() }) { outcome in
                if outcome == .denied { morningBrief = false; denied = true }
            }
        }
        .onChangeCompat(of: windDown) { on in
            WindDownNudge.setEnabled(on) { outcome in
                if outcome == .denied { windDown = false; denied = true }
            }
        }
        .alert("Notifications are off", isPresented: $denied) {
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            }
            Button("Not now", role: .cancel) {}
        } message: {
            Text("Turn on notifications for Viva in iOS Settings to get this reminder.")
        }
    }
}

// MARK: - Language

struct VivaLanguageView: View {
    @AppStorage(AppLanguage.storageKey) private var languageRaw = AppLanguage.system.rawValue

    var body: some View {
        VivaDetailScaffold(title: "Language Preferences", spacing: 6) {
            Color.clear.frame(height: 8)
            ForEach(AppLanguage.allCases) { language in
                Button { languageRaw = language.rawValue } label: {
                    HStack(spacing: 14) {
                        Circle().strokeBorder(VivaStyle.textPrimary, lineWidth: 1.5).frame(width: 18, height: 18)
                            .overlay(Circle().fill(languageRaw == language.rawValue ? VivaStyle.textPrimary : Color.clear)
                                .frame(width: 9, height: 9))
                        Text(verbatim: language == .system ? String(localized: "System default") : language.autonym)
                            .font(.system(size: 14, weight: .semibold)).foregroundStyle(VivaStyle.textPrimary)
                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 46)
                    .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 3, style: .continuous))
                }
                .buttonStyle(VivaPressStyle())
                .accessibilityAddTraits(languageRaw == language.rawValue ? .isSelected : [])
            }
            Text("A language change takes effect the next time you open Viva.")
                .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary).padding(.top, 12).padding(.horizontal, 4)
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            } label: {
                VivaLinkLabel(String(localized: "Open iOS Settings for Viva"), color: VivaStyle.textPrimary)
            }
            .buttonStyle(VivaPressStyle())
            .padding(.top, 10).padding(.horizontal, 4)
        }
        .onChangeCompat(of: languageRaw) { AppLanguage.apply($0) }
    }
}
#endif
