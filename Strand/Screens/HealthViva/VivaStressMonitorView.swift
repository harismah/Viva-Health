#if os(iOS)
import SwiftUI
import StrandDesign
import WhoopStore

/// Stress Monitor: today's stress level on the 0-3 dial, the recent trend, and the breathing
/// sessions that bring it down or lift alertness. Reads the same `StressModel` the full Stress
/// screen builds.
struct VivaStressMonitorView: View {
    @EnvironmentObject private var repo: Repository
    @EnvironmentObject private var router: NavRouter
    @AppStorage("noop.coachEnabled") private var coachEnabled = true
    @State private var model: StressModel?
    @State private var loaded = false
    @State private var showInfo = false
    @State private var showBreathe = false

    private var caption: String {
        guard let model else { return loaded ? String(localized: "Calibrating") : String(localized: "Calculating") }
        switch model.band {
        case .low: return String(localized: "Low")
        case .medium: return String(localized: "Medium")
        case .high: return String(localized: "High")
        }
    }

    var body: some View {
        VivaDetailScaffold(title: "Stress Monitor", trailing: .info, onTrailing: { showInfo = true },
                           coachMessage: coachEnabled && model != nil
                               ? String(localized: "Stress is \(caption.lowercased()) today. Ask what is driving it.") : nil,
                           onCoach: { router.openCoach() }) {
            VivaHalfDial(value: model?.score, maximum: 3, caption: caption,
                         time: model == nil ? nil : String(localized: "Today"))
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
            if let model {
                VivaCard(title: String(localized: "Last 14 days")) {
                    VivaLineChart(values: model.sparkValues, color: VivaStyle.textPrimary, range: 0...3,
                                  yLabels: ["3.0", "2.0", "1.0", "0.0"], height: 120, fill: true)
                    Text(model.explanation).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            } else if loaded {
                VivaCallout(text: String(localized: "Stress needs a few nights of heart-rate variability and resting heart rate to calibrate."))
            }
            Text("Sessions").vivaLabel().padding(.top, 14).padding(.horizontal, 4)
            HStack(spacing: VivaStyle.cardGap) {
                session(String(localized: "Increase relaxation"),
                        colors: [VivaStyle.sleep, VivaStyle.canvasTop])
                session(String(localized: "Increase alertness"),
                        colors: [VivaStyle.recoveryRed.opacity(0.75), VivaStyle.callout])
            }
            NavigationLink(value: TabRoute.stress) {
                VivaCard(padding: 14) {
                    VivaMetricRow(icon: "chart.xyaxis.line", label: String(localized: "Full stress analysis"),
                                  value: "", showsChevron: true)
                }
            }
            .buttonStyle(VivaPressStyle())
        }
        .task(id: repo.refreshSeq) {
            let stored = await repo.series(key: "stress", source: "my-whoop")
            guard !Task.isCancelled else { return }
            model = StressModel(days: repo.days, stored: stored)
            loaded = true
        }
        .sheet(isPresented: $showBreathe) {
            NavigationStack { BreathingView() }
        }
        .sheet(isPresented: $showInfo) {
            VivaInfoSheet(title: String(localized: "What is Stress?"), paragraphs: [
                String(localized: "Stress here is your body's physiological activation, read from your heart rate variability and resting heart rate against your own baseline. It is scored from 0 to 3."),
                String(localized: "0.0 to 0.9 is low: calm, relaxed or sleepy."),
                String(localized: "1.0 to 1.9 is medium: neutral, alert or mildly activated."),
                String(localized: "2.0 to 3.0 is high: excited, stressed or highly activated."),
                String(localized: "Stress is not good or bad in itself. You want it low when you are winding down, and some activation helps when you need to perform."),
            ])
        }
    }

    private func session(_ title: String, colors: [Color]) -> some View {
        Button { showBreathe = true } label: {
            VStack(alignment: .leading, spacing: 0) {
                LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom)
                    .frame(height: 78)
                VStack(alignment: .leading, spacing: 6) {
                    Image(systemName: "wind").font(.system(size: 13)).foregroundStyle(VivaStyle.textSecondary)
                    Text(title).vivaCardTitle().multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Guided breathing").font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                }
                .padding(10)
                .frame(maxWidth: .infinity, minHeight: 86, alignment: .topLeading)
                .background(VivaStyle.card)
            }
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(VivaPressStyle())
    }
}
#endif
