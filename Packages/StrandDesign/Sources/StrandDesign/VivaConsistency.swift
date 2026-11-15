import Foundation

/// Small pure rules that keep two Viva screens from contradicting each other. Each has a Kotlin
/// twin in `android/app/src/main/java/com/noop/ui/viva/` (`VivaSleepDetail.nightOffset`,
/// `VivaVitals.summary`); change both together.
public enum VivaConsistency {
    /// How many days before `todayKey` the described night's day is; 0 when it is today's, in the
    /// future, missing or unreadable. A screen titles itself from this, so an older night is never
    /// presented as last night. Keys are `yyyy-MM-dd`.
    public static func nightOffset(dayKey: String?, todayKey: String) -> Int {
        guard let dayKey, let day = date(dayKey), let today = date(todayKey) else { return 0 }
        let days = calendar.dateComponents([.day], from: day, to: today).day ?? 0
        return max(days, 0)
    }

    /// The one sentence for "how many vitals are in range", shared by Home's Health Monitor card
    /// and the Health tab; nil when nothing has been read, so each caller says so in its own words.
    public static func vitalsSummary(inRange: Int, read: Int) -> String? {
        read == 0 ? nil : "\(inRange)/\(read) metrics within range"
    }

    /// Why today's Recovery has no score, most specific cause first. A wearer still calibrating
    /// is told so; otherwise the screen names the input that is actually missing instead of
    /// blaming the baseline.
    public enum RecoveryGap: Equatable, Sendable {
        case calibrating(nights: Int)
        case noSleep
        case noHRV
        case notScoredYet
    }

    public static func recoveryGap(calibrationNights: Int?, hasSleepToday: Bool, hasHrvToday: Bool) -> RecoveryGap {
        if let calibrationNights { return .calibrating(nights: calibrationNights) }
        if !hasSleepToday { return .noSleep }
        if !hasHrvToday { return .noHRV }
        return .notScoredYet
    }

    /// The day a displayed value was carried from, or nil when the value is today's own (or there
    /// is none). Every screen stamps a carried value with this, so an older reading is never
    /// passed off as today's.
    public static func carriedDay(todayValue: Double?, carriedFrom: String?) -> String? {
        todayValue == nil ? carriedFrom : nil
    }

    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    private static func date(_ key: String) -> Date? {
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3, key.count == 10 else { return nil }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }
}
