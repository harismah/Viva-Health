import XCTest
@testable import StrandDesign

/// Twins of the Android rules in `VivaDetailScreensTest.kt` (`sleepNightOffset…`) and
/// `VivaVitals.summary`, so both phones title a night and summarise vitals the same way.
final class VivaConsistencyTests: XCTestCase {
    func testNightOffsetCountsDaysBackAndNeverGoesNegative() {
        XCTAssertEqual(VivaConsistency.nightOffset(dayKey: "2026-11-10", todayKey: "2026-11-10"), 0)
        XCTAssertEqual(VivaConsistency.nightOffset(dayKey: "2026-11-09", todayKey: "2026-11-10"), 1)
        XCTAssertEqual(VivaConsistency.nightOffset(dayKey: "2026-10-31", todayKey: "2026-11-02"), 2)
        XCTAssertEqual(VivaConsistency.nightOffset(dayKey: "2026-11-11", todayKey: "2026-11-10"), 0)
        XCTAssertEqual(VivaConsistency.nightOffset(dayKey: nil, todayKey: "2026-11-10"), 0)
        XCTAssertEqual(VivaConsistency.nightOffset(dayKey: "not a day", todayKey: "2026-11-10"), 0)
    }

    func testVitalsSummaryCountsOnlyReadingsThatExist() {
        XCTAssertEqual(VivaConsistency.vitalsSummary(inRange: 3, read: 4), "3/4 metrics within range")
        XCTAssertEqual(VivaConsistency.vitalsSummary(inRange: 5, read: 5), "5/5 metrics within range")
    }

    func testVitalsSummaryIsNilWithNoReadings() {
        XCTAssertNil(VivaConsistency.vitalsSummary(inRange: 0, read: 0))
    }

    func testRecoveryGapPrefersCalibrationThenTheMissingInput() {
        XCTAssertEqual(VivaConsistency.recoveryGap(calibrationNights: 2, hasSleepToday: true, hasHrvToday: false),
                       .calibrating(nights: 2))
        XCTAssertEqual(VivaConsistency.recoveryGap(calibrationNights: nil, hasSleepToday: false, hasHrvToday: false),
                       .noSleep)
        XCTAssertEqual(VivaConsistency.recoveryGap(calibrationNights: nil, hasSleepToday: true, hasHrvToday: false),
                       .noHRV)
        XCTAssertEqual(VivaConsistency.recoveryGap(calibrationNights: nil, hasSleepToday: true, hasHrvToday: true),
                       .notScoredYet)
    }

    func testCarriedValueIsStampedOnlyWhenNotTodays() {
        XCTAssertNil(VivaConsistency.carriedDay(todayValue: 66, carriedFrom: "2026-11-09"))
        XCTAssertEqual(VivaConsistency.carriedDay(todayValue: nil, carriedFrom: "2026-11-09"), "2026-11-09")
        XCTAssertNil(VivaConsistency.carriedDay(todayValue: nil, carriedFrom: nil))
    }
}
