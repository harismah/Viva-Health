import XCTest
@testable import StrandDesign

final class VivaScoreRingTests: XCTestCase {
    func testFractionClamps() {
        XCTAssertEqual(VivaScoreRing.fraction(value: 25, maximum: 21), 1)
        XCTAssertEqual(VivaScoreRing.fraction(value: -3, maximum: 21), 0)
        XCTAssertEqual(VivaScoreRing.fraction(value: nil, maximum: 21), 0)
        XCTAssertEqual(VivaScoreRing.fraction(value: 10.5, maximum: 21), 0.5)
        XCTAssertEqual(VivaScoreRing.fraction(value: 5, maximum: 0), 0)
    }

    func testRingWithNilValueShowsDashes() {
        XCTAssertEqual(VivaScoreRing.text(value: nil, format: .percent), "--")
        XCTAssertEqual(VivaScoreRing.text(value: nil, format: .decimal1), "--")
    }

    func testText() {
        XCTAssertEqual(VivaScoreRing.text(value: 85, format: .percent), "85")
        XCTAssertEqual(VivaScoreRing.text(value: 84.6, format: .percent), "85")
        XCTAssertEqual(VivaScoreRing.text(value: 7.46, format: .decimal1), "7.5")
    }
}
