import XCTest
@testable import StrandDesign

final class VivaAlarmReadoutTests: XCTestCase {
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }
    private let locale = Locale(identifier: "en_US")

    func testArmedAlarmShowsNextFireTimeAndOnStatus() {
        // 2026-11-10 07:00 UTC
        let fire = calendar.date(from: DateComponents(year: 2026, month: 11, day: 10, hour: 7, minute: 0))!
        let readout = VivaAlarmReadout.resolve(nextFire: fire, baseWakeMinutes: 6 * 60 + 30,
                                               sleepNeedMinutes: 9 * 60, calendar: calendar, locale: locale)
        XCTAssertTrue(readout.isArmed)
        XCTAssertEqual(readout.wakeMinutes, 7 * 60)
        XCTAssertEqual(readout.bedtimeMinutes, 22 * 60)
        XCTAssertEqual(readout.statusText, "Alarm on")
    }

    func testUnarmedAlarmFallsBackToBaseWakeAndMakesNoPromise() {
        let readout = VivaAlarmReadout.resolve(nextFire: nil, baseWakeMinutes: 6 * 60 + 30,
                                               sleepNeedMinutes: 8 * 60, calendar: calendar, locale: locale)
        XCTAssertFalse(readout.isArmed)
        XCTAssertEqual(readout.wakeMinutes, 6 * 60 + 30)
        XCTAssertEqual(readout.bedtimeMinutes, 22 * 60 + 30)
        XCTAssertEqual(readout.statusText, "Alarm off")
        XCTAssertNil(readout.nextFire)
    }

    func testBedtimeWrapsBeforeMidnight() {
        let readout = VivaAlarmReadout.resolve(nextFire: nil, baseWakeMinutes: 5 * 60,
                                               sleepNeedMinutes: 9 * 60, calendar: calendar, locale: locale)
        XCTAssertEqual(readout.bedtimeMinutes, 20 * 60)
        XCTAssertEqual(readout.timeInBedText, "9:00")
    }

    func testClockTextSplitsTimeAndPeriod() {
        let parts = VivaAlarmReadout.clockParts(minutes: 22 * 60, locale: locale)
        XCTAssertEqual(parts.time, "10:00")
        XCTAssertEqual(parts.period, "PM")
    }
}
