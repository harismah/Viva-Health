#if os(iOS)
import SwiftUI
import StrandDesign
import StrandAnalytics
import WhoopStore

/// The vitals the Health Monitor shows, read through the same `BodyVitalSigns` banding the full
/// Health screen uses, so "within range" means the same thing on both.
@MainActor
enum VivaVitals {
    struct Item: Identifiable {
        let id: String
        let icon: String
        let label: String
        let shortLabel: String
        let value: String?
        let unit: String
        let status: String
        let statusColor: Color
        let about: String
        let trend: VivaTrendMetric?
    }

    /// The one sentence for "how many vitals are in range", shared by the Health tab and Home's
    /// Health Monitor card so their counts cannot differ; nil with no readings.
    /// Twin of `VivaVitals.summary` in VivaHealthScreens.kt.
    static func summary(_ items: [Item]) -> String? {
        VivaConsistency.vitalsSummary(inRange: items.filter { $0.statusColor == VivaStyle.positive }.count,
                                      read: items.filter { $0.value != nil }.count)
    }

    static func items(repo: Repository, temperatureUnit: TemperatureUnit) -> [Item] {
        let readings = BodyVitalSigns.readings(days: repo.days, today: repo.today, temperatureUnit: temperatureUnit)
        let order: [(key: String, icon: String, label: String, short: String, about: String, trend: VivaTrendMetric?)] = [
            ("resp", "lungs", String(localized: "Respiratory Rate"), String(localized: "Resp"),
             String(localized: "Breaths per minute while you sleep. It is normally very steady from night to night, so a clear shift from your usual range is worth noticing."), .respiratoryRate),
            ("spo2", "drop", String(localized: "Blood Oxygen (SpO₂)"), "SpO₂",
             String(localized: "How much oxygen your red blood cells are carrying while you sleep."), .bloodOxygen),
            ("rhr", "heart", String(localized: "RHR"), "RHR",
             String(localized: "Resting heart rate: your heart rate at its calmest during sleep. A lower figure usually goes with better cardiovascular fitness."), .restingHR),
            ("hrv", "waveform.path.ecg", String(localized: "HRV"), "HRV",
             String(localized: "Heart rate variability: the variation in time between heartbeats during sleep. It reflects how well your body is adapting to the load on it."), .hrv),
            ("skin", "thermometer.medium", String(localized: "Skin Temp"), String(localized: "Temp"),
             String(localized: "The temperature at the surface of your skin during sleep. It moves with illness, cycle phase and room temperature."), nil),
        ]
        return order.map { entry in
            let reading = readings.first { $0.key == entry.key }
            let status: (String, Color)
            switch (reading?.value, reading?.banding.band) {
            case (.none, _): status = (String(localized: "No reading"), VivaStyle.textTertiary)
            case (_, .inRange?): status = (String(localized: "Within range"), VivaStyle.positive)
            case (_, .outOfRange?): status = (String(localized: "Out of range"), VivaStyle.warning)
            default: status = (String(localized: "Calibrating range"), VivaStyle.textPrimary)
            }
            // A reading carried from an earlier night says which, the same stamp Home and the detail
            // screens put under the value.
            let stamp: String? = (reading?.value != nil && reading?.day != nil
                                  && reading?.day != BodyVitalSigns.logicalDayKey(Date()))
                ? BodyVitalReading.dayLabel(reading!.day!) : nil
            return Item(id: entry.key, icon: entry.icon, label: entry.label, shortLabel: entry.short,
                        value: reading.flatMap { r in r.value.map { r.format($0) } },
                        unit: reading?.unit ?? "", status: stamp.map { "\(status.0) · \($0)" } ?? status.0,
                        statusColor: status.1,
                        about: entry.about, trend: entry.trend)
        }
    }
}

struct VivaHealthMonitorView: View {
    @EnvironmentObject private var repo: Repository
    @AppStorage(UnitPrefs.systemKey) private var unitSystemRaw = UnitSystem.metric.rawValue
    @AppStorage(UnitPrefs.temperatureKey) private var temperatureRaw = ""
    @State private var selected: VivaVitals.Item?
    @State private var trendTarget: VivaTrendMetric?

    private var temperatureUnit: TemperatureUnit {
        UnitPrefs.resolveTemperature(system: UnitSystem(rawValue: unitSystemRaw) ?? .metric, override: temperatureRaw)
    }

    var body: some View {
        let items = VivaVitals.items(repo: repo, temperatureUnit: temperatureUnit)
        VivaDetailScaffold(title: "Health Monitor") {
            VivaLiveHeartRateStrip()
            if items.contains(where: { $0.value != nil && $0.statusColor == VivaStyle.textPrimary }) {
                VivaCallout(text: String(localized: "Your typical ranges firm up as more nights are recorded."))
            }
            LazyVGrid(columns: [GridItem(.flexible(), spacing: VivaStyle.cardGap), GridItem(.flexible())],
                      spacing: VivaStyle.cardGap) {
                ForEach(items) { item in
                    VivaVitalsTile(icon: item.icon, label: item.label, value: item.value, unit: item.unit,
                                   status: item.status, statusColor: item.statusColor) { selected = item }
                }
            }
            NavigationLink(value: TabRoute.health) {
                VivaCard(padding: 14) {
                    VivaMetricRow(icon: "list.bullet.rectangle", label: String(localized: "Full health report"),
                                  value: "", showsChevron: true)
                }
            }
            .buttonStyle(VivaPressStyle())
            Text("Viva is not a medical device. Talk to a medical professional before making medical decisions.")
                .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                .padding(.horizontal, 4)
        }
        .overlay {
            if let item = selected {
                VivaExplainerDialog(
                    icon: item.icon, title: item.label, status: item.status, body: item.about,
                    footnote: String(localized: "This reading is not a medical measurement."),
                    buttonTitle: item.trend == nil ? nil : String(localized: "Open trend view"),
                    onButton: { selected = nil; trendTarget = item.trend },
                    onClose: { selected = nil })
            }
        }
        .animation(.easeInOut(duration: 0.18), value: selected?.id)
        .navigationDestination(item: $trendTarget) { VivaTrendView(metric: $0) }
    }
}

/// Live heart rate from the strap, or dashes when it is not streaming.
struct VivaLiveHeartRateStrip: View {
    @EnvironmentObject private var live: LiveState

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Heart rate").vivaCardTitle()
            Image(systemName: "heart.fill").font(.system(size: 18)).foregroundStyle(VivaStyle.strain)
            Text(live.connected ? (live.heartRate.map(String.init) ?? "--") : "--")
                .font(VivaFont.number(34)).foregroundStyle(VivaStyle.textPrimary)
            Text("BPM").vivaLabel()
            Text(live.connected ? String(localized: "Live from your strap") : String(localized: "Strap not connected"))
                .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// A centred card over a dimmed screen that explains one reading, with an optional action.
struct VivaExplainerDialog: View {
    let icon: String
    let title: String
    let status: String
    let body_: String
    let footnote: String?
    let buttonTitle: String?
    let onButton: () -> Void
    let onClose: () -> Void

    init(icon: String, title: String, status: String, body: String, footnote: String? = nil,
         buttonTitle: String? = nil, onButton: @escaping () -> Void = {}, onClose: @escaping () -> Void) {
        self.icon = icon; self.title = title; self.status = status; self.body_ = body
        self.footnote = footnote; self.buttonTitle = buttonTitle; self.onButton = onButton; self.onClose = onClose
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea().onTapGesture(perform: onClose)
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Image(systemName: icon).font(.system(size: 22, weight: .light)).foregroundStyle(VivaStyle.textSecondary)
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark").font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(VivaStyle.textPrimary).frame(width: 32, height: 32)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text("Close"))
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).vivaCardTitle()
                    Text(status).vivaCardTitle(VivaStyle.textSecondary)
                }
                Text(body_).font(VivaFont.body).foregroundStyle(VivaStyle.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                if let footnote {
                    Text(footnote).font(.system(size: 10)).foregroundStyle(VivaStyle.textTertiary)
                }
                if let buttonTitle {
                    Button(action: onButton) {
                        Text(buttonTitle).font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                            .foregroundStyle(VivaStyle.textPrimary)
                            .frame(maxWidth: .infinity).frame(height: 44)
                            .overlay(RoundedRectangle(cornerRadius: 6, style: .continuous)
                                .strokeBorder(VivaStyle.textPrimary, lineWidth: 1))
                    }
                    .buttonStyle(VivaPressStyle())
                    .padding(.top, 4)
                }
            }
            .padding(18)
            .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            .padding(.horizontal, 44)
        }
        .transition(.opacity)
    }
}
#endif
