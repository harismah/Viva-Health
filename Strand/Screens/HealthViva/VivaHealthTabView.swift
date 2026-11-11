#if os(iOS)
import SwiftUI
import StrandDesign
import WhoopStore

/// The Health tab: long-range fitness markers on top, then the Health Monitor and Stress Monitor
/// summaries, each opening its full screen.
struct VivaHealthTabView: View {
    @EnvironmentObject private var repo: Repository
    @AppStorage(UnitPrefs.systemKey) private var unitSystemRaw = UnitSystem.metric.rawValue
    @AppStorage(UnitPrefs.temperatureKey) private var temperatureRaw = ""
    @Environment(\.scrollToTopSignal) private var scrollToTopSignal

    @State private var fitnessAge: Double?
    @State private var vitality: Double?
    @State private var stress: StressModel?
    @State private var loaded = false

    private var temperatureUnit: TemperatureUnit {
        UnitPrefs.resolveTemperature(system: UnitSystem(rawValue: unitSystemRaw) ?? .metric, override: temperatureRaw)
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: VivaStyle.cardGap) {
                    Color.clear.frame(height: 0).id("top")
                    Text("Health")
                        .font(.system(size: 13, weight: .bold)).tracking(1.6).textCase(.uppercase)
                        .foregroundStyle(VivaStyle.textPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                    longevityCard
                    monitorCard
                    stressCard
                    Text("Viva is not a medical device and cannot diagnose or manage medical conditions. Talk to your doctor about health concerns.")
                        .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                        .padding(.horizontal, 4).padding(.top, 8)
                    Color.clear.frame(height: 90)
                }
                .padding(.horizontal, VivaStyle.pagePadding)
            }
            .onChange(of: scrollToTopSignal) { _, _ in
                withAnimation(.easeOut(duration: 0.35)) { proxy.scrollTo("top", anchor: .top) }
            }
        }
        .background {
            ZStack(alignment: .top) {
                VivaCanvas()
                LinearGradient(colors: [VivaStyle.magenta.opacity(0.32), VivaStyle.magenta.opacity(0)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: 420)
                    .ignoresSafeArea()
            }
        }
        .task(id: repo.refreshSeq) {
            async let fit = repo.exploreSeries(key: "fitness_age", source: "my-whoop")
            async let vit = repo.exploreSeries(key: "vitality", source: "my-whoop")
            async let stored = repo.series(key: "stress", source: "my-whoop")
            let (fitSeries, vitSeries, stressSeries) = await (fit, vit, stored)
            guard !Task.isCancelled else { return }
            fitnessAge = fitSeries.last?.value
            vitality = vitSeries.last?.value
            stress = StressModel(days: repo.days, stored: stressSeries)
            loaded = true
        }
    }

    private static func ageText(_ age: Double) -> String {
        // The engine reports within 20...80; a value on a bound means "at or beyond" it.
        if age <= 20 { return "≤20" }
        if age >= 80 { return "≥80" }
        return String(format: "%.1f", locale: AppLanguage.activeLocale, age)
    }

    private var longevityCard: some View {
        NavigationLink(value: TabRoute.health) {
            VStack(spacing: 18) {
                ZStack {
                    Circle()
                        .fill(RadialGradient(colors: [VivaStyle.magenta.opacity(0.55), VivaStyle.stageREM.opacity(0.25)],
                                             center: .center, startRadius: 4, endRadius: 80))
                        .overlay(Circle().strokeBorder(VivaStyle.magenta, lineWidth: 1.5))
                        .frame(width: 150, height: 150)
                    VStack(spacing: 2) {
                        Text(fitnessAge.map(Self.ageText) ?? "--").font(VivaFont.number(40))
                            .foregroundStyle(VivaStyle.textPrimary)
                        Text("Fitness age").vivaLabel(VivaStyle.textPrimary)
                    }
                }
                .padding(.top, 10)
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(fitnessAge == nil ? "Unlock fitness age" : "Fitness age").vivaCardTitle()
                        Spacer()
                        if let vitality {
                            Text("Vitality").vivaLabel()
                            Text("\(Int(vitality.rounded()))").font(VivaFont.valueSmall).foregroundStyle(VivaStyle.textPrimary)
                        }
                        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(VivaStyle.textSecondary)
                    }
                    Text(fitnessAge == nil
                         ? "Fitness age appears once enough nights and workouts are recorded."
                         : "How old your cardiovascular fitness reads, from your resting heart rate, variability and estimated VO₂ max.")
                        .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(VivaStyle.cardPadding)
            .background(VivaStyle.card.opacity(0.55), in: RoundedRectangle(cornerRadius: VivaStyle.cardRadius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: VivaStyle.cardRadius, style: .continuous)
                .strokeBorder(VivaStyle.magenta.opacity(0.7), lineWidth: 1))
        }
        .buttonStyle(VivaPressStyle())
    }

    private var monitorCard: some View {
        let items = VivaVitals.items(repo: repo, temperatureUnit: temperatureUnit)
        return NavigationLink(value: VivaHomeRoute.healthMonitor) {
            VivaCard(title: String(localized: "Health Monitor"), accessory: .chevron) {
                HStack(spacing: 0) {
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                        VStack(spacing: 6) {
                            Image(systemName: item.icon).font(.system(size: 17, weight: .light))
                                .foregroundStyle(VivaStyle.textSecondary).frame(height: 22)
                            Text(item.shortLabel).vivaLabel(VivaStyle.textPrimary)
                            RoundedRectangle(cornerRadius: 3, style: .continuous).fill(VivaStyle.chipActive)
                                .frame(width: 16, height: 16)
                                .overlay(Circle().fill(item.statusColor).frame(width: 5, height: 5))
                        }
                        .frame(maxWidth: .infinity)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text(item.label))
                        .accessibilityValue(Text(item.status))
                        if index < items.count - 1 {
                            Rectangle().fill(VivaStyle.divider).frame(width: 1, height: 50)
                        }
                    }
                }
                Text(VivaVitals.summary(items) ?? String(localized: "No readings yet. Wear your strap to sleep."))
                    .font(VivaFont.caption).foregroundStyle(VivaStyle.textPrimary)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(VivaStyle.inset, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
        }
        .buttonStyle(VivaPressStyle())
    }

    private var stressCard: some View {
        NavigationLink(value: VivaHomeRoute.stressMonitor) {
            VivaCard(title: String(localized: "Stress Monitor"), accessory: .chevron) {
                HStack(alignment: .center, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Today's stress").vivaLabel()
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text(stress.map { String(format: "%.1f", $0.score) } ?? "--")
                                .font(VivaFont.valueMedium).foregroundStyle(VivaStyle.textPrimary)
                            Text("/ 3").font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                        }
                        Text(stress == nil ? String(localized: "Calibrating")
                             : (stress!.band == .low ? String(localized: "Low")
                                : stress!.band == .medium ? String(localized: "Medium") : String(localized: "High")))
                            .font(VivaFont.number(11, weight: .medium)).foregroundStyle(VivaStyle.textSecondary)
                            .padding(.horizontal, 6).frame(height: 18)
                            .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 3, style: .continuous))
                    }
                    if let stress {
                        VivaLineChart(values: stress.sparkValues, color: VivaStyle.textPrimary, range: 0...3, height: 56)
                    }
                }
            }
        }
        .buttonStyle(VivaPressStyle())
    }
}
#endif
