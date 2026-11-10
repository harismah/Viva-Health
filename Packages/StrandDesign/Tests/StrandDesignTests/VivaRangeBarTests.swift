import XCTest
@testable import StrandDesign

final class VivaRangeBarTests: XCTestCase {
    func testRangeBarClamps() {
        XCTAssertEqual(VivaRangeBar.layout(value: 150, range: nil, maximum: 100, width: 200).fill, 200)
        XCTAssertEqual(VivaRangeBar.layout(value: -5, range: nil, maximum: 100, width: 200).fill, 0)
        XCTAssertEqual(VivaRangeBar.layout(value: 50, range: nil, maximum: 0, width: 200).fill, 0)
    }

    func testRangeBracketPosition() {
        let layout = VivaRangeBar.layout(value: 50, range: 20...40, maximum: 100, width: 200)
        XCTAssertEqual(layout.fill, 100)
        XCTAssertEqual(layout.hatchStart, 40)
        XCTAssertEqual(layout.hatchWidth, 40)
    }

    func testRangeBracketClampsToBar() {
        let layout = VivaRangeBar.layout(value: 10, range: 80...140, maximum: 100, width: 200)
        XCTAssertEqual(layout.hatchStart, 160)
        XCTAssertEqual(layout.hatchWidth, 40)
    }

    func testWeekCardScaleIgnoresEmptyDays() {
        let days = [
            VivaWeekBarCard.Day(label: "Sun", date: 8, segments: [], valueText: nil, isToday: false),
            VivaWeekBarCard.Day(label: "Mon", date: 9, segments: [(4, .white), (2, .white)], valueText: "6", isToday: true),
        ]
        XCTAssertEqual(VivaWeekBarCard.peak(days), 6)
        XCTAssertFalse(VivaWeekBarCard.isEmpty(days))
        XCTAssertTrue(VivaWeekBarCard.isEmpty([days[0]]))
    }

    func testHalfDialFractionClamps() {
        XCTAssertEqual(VivaHalfDial.fraction(value: 4.2, maximum: 3), 1)
        XCTAssertEqual(VivaHalfDial.fraction(value: nil, maximum: 3), 0)
        XCTAssertEqual(VivaHalfDial.fraction(value: 1.5, maximum: 3), 0.5)
    }
}
