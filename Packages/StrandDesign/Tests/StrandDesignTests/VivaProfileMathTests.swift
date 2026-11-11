import XCTest
@testable import StrandDesign

final class VivaProfileMathTests: XCTestCase {
    func testCurrentStreakCountsBackFromToday() {
        let days: Set<String> = ["2026-11-10", "2026-11-09", "2026-11-08", "2026-11-06"]
        XCTAssertEqual(VivaProfileMath.currentStreak(days: days, todayKey: "2026-11-10"), 3)
    }

    func testCurrentStreakSurvivesTodayNotRecordedYet() {
        let days: Set<String> = ["2026-11-09", "2026-11-08"]
        XCTAssertEqual(VivaProfileMath.currentStreak(days: days, todayKey: "2026-11-10"), 2)
    }

    func testCurrentStreakIsZeroAfterAMissedDay() {
        XCTAssertEqual(VivaProfileMath.currentStreak(days: ["2026-11-07"], todayKey: "2026-11-10"), 0)
        XCTAssertEqual(VivaProfileMath.currentStreak(days: [], todayKey: "2026-11-10"), 0)
        XCTAssertEqual(VivaProfileMath.currentStreak(days: ["2026-11-10"], todayKey: "garbage"), 0)
    }

    func testCurrentStreakCrossesMonthBoundary() {
        XCTAssertEqual(VivaProfileMath.currentStreak(days: ["2026-11-01", "2026-10-31", "2026-10-30"],
                                                     todayKey: "2026-11-01"), 3)
    }

    func testLongestRunCountsConsecutiveDaysOnly() {
        // 8th and 9th qualify, 10th missing from the list entirely, 11th qualifies.
        let days = ["2026-11-08", "2026-11-09", "2026-11-11"]
        XCTAssertEqual(VivaProfileMath.longestRun(qualifyingDays: days), 2)
        XCTAssertEqual(VivaProfileMath.longestRun(qualifyingDays: []), 0)
        XCTAssertEqual(VivaProfileMath.longestRun(qualifyingDays: ["2026-11-08"]), 1)
    }

    func testInitials() {
        XCTAssertEqual(VivaProfileMath.initials("Haris Mahmood"), "HM")
        XCTAssertEqual(VivaProfileMath.initials("  haris  "), "H")
        XCTAssertEqual(VivaProfileMath.initials("Ana Maria de Souza"), "AS")
        XCTAssertEqual(VivaProfileMath.initials(""), "")
    }
}
