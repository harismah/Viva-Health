import Foundation

/// Pure counting rules behind the Profile screen: streaks and initials. Day keys are `yyyy-MM-dd`.
public enum VivaProfileMath {
    /// Consecutive recorded days ending today. A day that has not been recorded yet does not break
    /// the streak, so it is counted from yesterday until today's data lands; one fully missed day
    /// ends it.
    public static func currentStreak(days: Set<String>, todayKey: String) -> Int {
        guard var cursor = date(todayKey) else { return 0 }
        if !days.contains(todayKey) {
            guard let yesterday = calendar.date(byAdding: .day, value: -1, to: cursor) else { return 0 }
            cursor = yesterday
        }
        var count = 0
        while days.contains(key(cursor)) {
            count += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = previous
        }
        return count
    }

    /// The longest run of calendar-consecutive days among the days that qualified.
    public static func longestRun(qualifyingDays: [String]) -> Int {
        let dates = Set(qualifyingDays).compactMap(date).sorted()
        var best = 0, run = 0
        var previous: Date?
        for day in dates {
            if let previous, calendar.dateComponents([.day], from: previous, to: day).day == 1 {
                run += 1
            } else {
                run = 1
            }
            best = max(best, run)
            previous = day
        }
        return best
    }

    /// Up to two initials: the first letter of the first and of the last word.
    public static func initials(_ name: String) -> String {
        let words = name.split(whereSeparator: \.isWhitespace)
        guard let first = words.first?.first else { return "" }
        let last = words.count > 1 ? words.last?.first : nil
        return String([first, last].compactMap { $0 }).uppercased()
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

    private static func key(_ date: Date) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }
}
