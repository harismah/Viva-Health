import Foundation

/// The one answer to "when do I go to bed, when do I wake, and is the alarm really set" that every
/// screen showing those facts renders from. Home's Tonight card and the Sleep Planner both take a
/// value of this type, so the two can never print different times or a different armed state.
///
/// `nextFire` is the date the app's own alarm resolver says the strap will buzz, or nil when no
/// alarm will arm. A nil makes no promise: the wake time falls back to the wearer's base time and
/// the status reads off.
public struct VivaAlarmReadout: Equatable, Sendable {
    public let nextFire: Date?
    public let wakeMinutes: Int
    public let bedtimeMinutes: Int
    public let sleepNeedMinutes: Int
    public let wakeText: String
    public let bedtimeText: String

    public var isArmed: Bool { nextFire != nil }
    public var statusText: String { isArmed ? "Alarm on" : "Alarm off" }
    public var modeText: String { "Exact time" }
    public var timeInBedText: String { String(format: "%d:%02d", sleepNeedMinutes / 60, sleepNeedMinutes % 60) }

    public static func resolve(nextFire: Date?, baseWakeMinutes: Int, sleepNeedMinutes: Int,
                               calendar: Calendar = .current, locale: Locale = .current) -> VivaAlarmReadout {
        let wake: Int
        if let nextFire {
            let parts = calendar.dateComponents([.hour, .minute], from: nextFire)
            wake = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        } else {
            wake = ((baseWakeMinutes % 1440) + 1440) % 1440
        }
        let need = max(sleepNeedMinutes, 0)
        let bedtime = (((wake - need) % 1440) + 1440) % 1440
        return VivaAlarmReadout(
            nextFire: nextFire, wakeMinutes: wake, bedtimeMinutes: bedtime, sleepNeedMinutes: need,
            wakeText: clockText(minutes: wake, locale: locale),
            bedtimeText: clockText(minutes: bedtime, locale: locale)
        )
    }

    /// A minute of the day as the locale writes a clock time ("10:00 PM", "22:00").
    public static func clockText(minutes: Int, locale: Locale = .current) -> String {
        let parts = clockParts(minutes: minutes, locale: locale)
        return parts.period.isEmpty ? parts.time : "\(parts.time) \(parts.period)"
    }

    /// The same clock time split into digits and day period, so a screen can set them in two sizes.
    public static func clockParts(minutes: Int, locale: Locale = .current) -> (time: String, period: String) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let date = calendar.date(from: DateComponents(year: 2001, month: 1, day: 1,
                                                      hour: minutes / 60, minute: minutes % 60)) ?? Date()
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.timeZone = calendar.timeZone
        formatter.setLocalizedDateFormatFromTemplate("jmm")
        let full = formatter.string(from: date)
        let period = date < calendar.date(from: DateComponents(year: 2001, month: 1, day: 1, hour: 12))!
            ? formatter.amSymbol ?? "" : formatter.pmSymbol ?? ""
        guard !period.isEmpty, full.contains(period) else { return (full, "") }
        let time = full.replacingOccurrences(of: period, with: "")
            .trimmingCharacters(in: .whitespaces.union(CharacterSet(charactersIn: "\u{202F}\u{00A0}")))
        return (time, period)
    }
}
