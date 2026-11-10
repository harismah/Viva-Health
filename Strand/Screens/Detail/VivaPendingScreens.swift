#if os(iOS)
import SwiftUI

// Home routes whose Viva screens are not built yet resolve to the existing screen for the same
// job. Each alias is removed as its screen lands.
struct VivaSleepDetailView: View { var body: some View { SleepView() } }
struct VivaRecoveryDetailView: View {
    var body: some View {
        if let metric = MetricCatalog.all.first(where: { $0.key == HeroRingMetric.charge }) {
            MetricDetailView(metric: metric)
        } else { HealthView() }
    }
}
struct VivaStrainDetailView: View {
    var body: some View {
        if let metric = MetricCatalog.all.first(where: { $0.key == HeroRingMetric.effort }) {
            MetricDetailView(metric: metric)
        } else { WorkoutsView() }
    }
}
struct VivaHealthMonitorView: View { var body: some View { HealthView() } }
struct VivaStressMonitorView: View { var body: some View { StressView() } }
struct VivaSleepPlannerView: View { var body: some View { SmartAlarmView() } }
struct VivaBehaviorInsightsView: View { var body: some View { InsightsView() } }
#endif
