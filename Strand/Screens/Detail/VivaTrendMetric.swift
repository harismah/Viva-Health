#if os(iOS)
import SwiftUI
import StrandDesign
import StrandAnalytics
import WhoopStore

/// The per-day metrics the Viva weekly cards and Trend View chart. Each case knows how to read its
/// value off a stored `DailyMetric`, how to print it and how to draw it, so a weekly card and the
/// Trend View it opens can never disagree about a day's number.
enum VivaTrendMetric: String, CaseIterable, Hashable, Identifiable {
    case sleepPerformance, hoursOfSleep, restorativeSleep, sleepEfficiency
    case recovery, hrv, restingHR, respiratoryRate, bloodOxygen
    case strain, calories, steps

    enum Group: String, CaseIterable { case strain, recovery, sleep }

    var id: String { rawValue }

    var group: Group {
        switch self {
        case .strain, .calories, .steps: .strain
        case .recovery, .hrv, .restingHR, .respiratoryRate, .bloodOxygen: .recovery
        case .sleepPerformance, .hoursOfSleep, .restorativeSleep, .sleepEfficiency: .sleep
        }
    }

    var title: String {
        switch self {
        case .sleepPerformance: String(localized: "Sleep Performance")
        case .hoursOfSleep: String(localized: "Hours of Sleep")
        case .restorativeSleep: String(localized: "Restorative Sleep")
        case .sleepEfficiency: String(localized: "Sleep Efficiency")
        case .recovery: String(localized: "Recovery")
        case .hrv: String(localized: "Heart Rate Variability")
        case .restingHR: String(localized: "Resting Heart Rate")
        case .respiratoryRate: String(localized: "Respiratory Rate")
        case .bloodOxygen: String(localized: "Blood Oxygen")
        case .strain: String(localized: "Strain")
        case .calories: String(localized: "Calories")
        case .steps: String(localized: "Steps")
        }
    }

    var icon: String {
        switch self {
        case .sleepPerformance: "moon"
        case .hoursOfSleep: "clock"
        case .restorativeSleep: "sparkles"
        case .sleepEfficiency: "bed.double"
        case .recovery: "arrow.triangle.2.circlepath"
        case .hrv: "waveform.path.ecg"
        case .restingHR: "heart"
        case .respiratoryRate: "lungs"
        case .bloodOxygen: "drop"
        case .strain: "bolt"
        case .calories: "flame"
        case .steps: "shoeprints.fill"
        }
    }

    var unit: String {
        switch self {
        case .sleepPerformance, .sleepEfficiency, .recovery, .bloodOxygen: "%"
        case .hoursOfSleep, .restorativeSleep: String(localized: "hr")
        case .hrv: "ms"
        case .restingHR: "bpm"
        case .respiratoryRate: "rpm"
        case .strain, .calories, .steps: ""
        }
    }

    var color: Color {
        switch group {
        case .sleep: self == .restorativeSleep ? VivaStyle.stageREM : VivaStyle.sleep
        case .recovery: self == .recovery ? VivaStyle.textPrimary : VivaStyle.link
        case .strain: VivaStyle.strain
        }
    }

    /// Vitals read as a level (a dot against the scale); totals and scores read as a bar.
    var style: VivaWeekBarCard.Style {
        switch self {
        case .hrv, .restingHR, .respiratoryRate, .bloodOxygen: .dot
        default: .bar
        }
    }

    /// Dot metrics chart against their own range rather than from zero, so a 2 bpm move is visible.
    var chartsFromZero: Bool { style != .dot }

    func value(_ day: DailyMetric) -> Double? {
        switch self {
        case .sleepPerformance: AnalyticsEngine.Rest.composite(daily: day)
        case .hoursOfSleep: day.totalSleepMin
        case .restorativeSleep:
            (day.deepMin == nil && day.remMin == nil) ? nil : (day.deepMin ?? 0) + (day.remMin ?? 0)
        case .sleepEfficiency: day.efficiency.map { $0 <= 1 ? $0 * 100 : $0 }
        case .recovery: day.recovery
        case .hrv: day.avgHrv
        case .restingHR: day.restingHr.map(Double.init)
        case .respiratoryRate: day.respRateBpm
        case .bloodOxygen: day.spo2Pct
        case .strain: day.strain.map { UnitFormatter.effortValue($0, scale: .whoop) }
        case .calories: day.activeKcalEst
        case .steps: day.steps.map(Double.init)
        }
    }

    /// Today's value, or the freshest earlier one still inside the vitals carry window together with
    /// the day it came from. Uses the same selectors Home and the Health Monitor read, so all three
    /// screens show one answer; totals and scores (steps, strain, recovery) are never carried.
    @MainActor func todayOrCarried(days: [DailyMetric], today: DailyMetric?) -> (value: Double?, carriedDay: String?) {
        if let own = today.flatMap(value) { return (own, nil) }
        let todayKey = today?.day ?? Repository.logicalDayKey(Date())
        let row: DailyMetric?
        switch self {
        case .hrv: row = Repository.lastHrvDay(days: days, todayKey: todayKey)
        case .restingHR: row = Repository.lastRestingHrDay(days: days, todayKey: todayKey)
        case .respiratoryRate: row = Repository.lastRespDay(days: days, todayKey: todayKey)
        case .bloodOxygen: row = Repository.lastVitalsDay(days: days, todayKey: todayKey)
        default: row = nil
        }
        let carried = row.flatMap(value)
        return (carried, VivaConsistency.carriedDay(todayValue: nil, carriedFrom: carried == nil ? nil : row?.day))
    }

    func text(_ value: Double?) -> String {
        guard let value, value.isFinite else { return "--" }
        switch self {
        case .hoursOfSleep, .restorativeSleep: return Self.clock(minutes: value)
        case .respiratoryRate, .strain: return String(format: "%.1f", locale: AppLanguage.activeLocale, value)
        case .steps, .calories: return Int(value.rounded()).formatted(.number.locale(AppLanguage.activeLocale))
        case .sleepPerformance, .sleepEfficiency, .recovery, .bloodOxygen: return "\(Int(value.rounded()))%"
        case .hrv, .restingHR: return String(Int(value.rounded()))
        }
    }

    /// Colour for one day's mark; Recovery carries its band colour.
    func markColor(_ value: Double) -> Color {
        self == .recovery ? VivaStyle.recoveryColor(percent: value) : color
    }

    var summary: String {
        switch self {
        case .sleepPerformance: String(localized: "How much of the sleep you needed you actually got, adjusted for how efficiently you slept.")
        case .hoursOfSleep: String(localized: "Time asleep each night, not counting time awake in bed.")
        case .restorativeSleep: String(localized: "Deep and REM sleep together, the stages that do most of the physical and mental repair.")
        case .sleepEfficiency: String(localized: "The share of your time in bed that you spent asleep.")
        case .recovery: String(localized: "How ready your body is for load today, from last night's heart-rate variability, resting heart rate and sleep.")
        case .hrv: String(localized: "The beat-to-beat variation in your heart rhythm during sleep. Compare it with your own baseline, not with other people.")
        case .restingHR: String(localized: "Your heart rate at its calmest during sleep. It usually falls as fitness improves and rises with fatigue or illness.")
        case .respiratoryRate: String(localized: "Breaths per minute during sleep. It is normally very stable, so a clear change is worth noticing.")
        case .bloodOxygen: String(localized: "The oxygen saturation of your blood during sleep.")
        case .strain: String(localized: "The cardiovascular load you built up over the day, on a 0 to 21 scale.")
        case .calories: String(localized: "Active energy estimated from your heart rate through the day.")
        case .steps: String(localized: "Steps counted through the day.")
        }
    }

    static func clock(minutes: Double) -> String {
        let total = Int(minutes.rounded())
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    // MARK: Series

    struct Point: Identifiable {
        let id: String
        let date: Date
        let value: Double?
    }

    /// One point per calendar day for the `count` days ending on the logical today.
    @MainActor func points(days: [DailyMetric], count: Int, endingDaysAgo offset: Int = 0) -> [Point] {
        let calendar = Calendar.current
        let today = Repository.logicalDay(Date())
        let byDay = Dictionary(days.map { ($0.day, $0) }, uniquingKeysWith: { _, last in last })
        return (0..<count).reversed().map { back in
            let date = calendar.date(byAdding: .day, value: -(back + offset), to: today) ?? today
            let key = Repository.localDayKey(date)
            return Point(id: key, date: date, value: byDay[key].flatMap(value))
        }
    }

    /// The last seven days as week-card columns.
    @MainActor func weekDays(days: [DailyMetric]) -> [VivaWeekBarCard.Day] {
        let calendar = Calendar.current
        let series = points(days: days, count: 7)
        let floor = chartsFromZero ? 0 : Self.dotFloor(series.compactMap(\.value))
        return series.enumerated().map { index, point in
            VivaWeekBarCard.Day(
                label: point.date.formatted(.dateTime.weekday(.abbreviated).locale(AppLanguage.activeLocale)),
                date: calendar.component(.day, from: point.date),
                segments: point.value.map { [(value: max($0 - floor, 0.0001), color: markColor($0))] } ?? [],
                valueText: text(point.value),
                isToday: index == series.count - 1)
        }
    }

    /// A baseline a little under the lowest value, so dot charts use their vertical room.
    static func dotFloor(_ values: [Double]) -> Double {
        guard let low = values.min(), let high = values.max() else { return 0 }
        let span = max(high - low, max(high * 0.1, 1))
        return max(low - span, 0)
    }
}
#endif
