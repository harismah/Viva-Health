import XCTest
@testable import StrandAnalytics

final class ScoreComparatorTests: XCTestCase {

    func testJoinsOnlyDaysPresentInBothSeries() {
        let noop = ["2026-11-01": 60.0, "2026-11-02": 70.0, "2026-11-03": 80.0]
        let ref  = ["2026-11-02": 65.0, "2026-11-03": 85.0, "2026-11-04": 90.0]
        let a = ScoreComparator.compare(noop: noop, reference: ref)!
        XCTAssertEqual(a.n, 2)
        XCTAssertEqual(a.pairs.map(\.day), ["2026-11-02", "2026-11-03"])
    }

    func testErrorStatistics() {
        // diffs (noop − ref): +2, −2, +4, 0 → bias 1, MAE 2, RMSE sqrt(24/4)=sqrt(6)
        let noop = ["d1": 12.0, "d2": 18.0, "d3": 34.0, "d4": 40.0]
        let ref  = ["d1": 10.0, "d2": 20.0, "d3": 30.0, "d4": 40.0]
        let a = ScoreComparator.compare(noop: noop, reference: ref)!
        XCTAssertEqual(a.bias, 1.0, accuracy: 1e-9)
        XCTAssertEqual(a.mae, 2.0, accuracy: 1e-9)
        XCTAssertEqual(a.rmse, 6.0.squareRoot(), accuracy: 1e-9)
        // sample SD of diffs: mean 1, squared devs 1+9+9+1=20, /3 → sqrt(20/3)
        let sd = (20.0 / 3.0).squareRoot()
        XCTAssertEqual(a.limitsOfAgreement.lower, 1.0 - 1.96 * sd, accuracy: 1e-9)
        XCTAssertEqual(a.limitsOfAgreement.upper, 1.0 + 1.96 * sd, accuracy: 1e-9)
        XCTAssertNotNil(a.r)
    }

    func testWorstDaysSortedByAbsoluteError() {
        let noop = ["d1": 12.0, "d2": 18.0, "d3": 34.0, "d4": 40.0]
        let ref  = ["d1": 10.0, "d2": 20.0, "d3": 30.0, "d4": 40.0]
        let a = ScoreComparator.compare(noop: noop, reference: ref, worstCount: 2)!
        XCTAssertEqual(a.worstDays.map(\.day), ["d3", "d1"])  // |4|, then tie |2| broken by day
    }

    func testPerfectAgreement() {
        let s = ["d1": 50.0, "d2": 60.0, "d3": 70.0]
        let a = ScoreComparator.compare(noop: s, reference: s)!
        XCTAssertEqual(a.mae, 0, accuracy: 1e-12)
        XCTAssertEqual(a.bias, 0, accuracy: 1e-12)
        XCTAssertEqual(a.r!, 1.0, accuracy: 1e-9)
    }

    func testNonFiniteValuesAreDropped() {
        let noop = ["d1": .nan, "d2": 10.0, "d3": .infinity]
        let ref  = ["d1": 5.0, "d2": 12.0, "d3": 7.0]
        let a = ScoreComparator.compare(noop: noop, reference: ref)!
        XCTAssertEqual(a.n, 1)
        XCTAssertEqual(a.bias, -2.0, accuracy: 1e-12)
    }

    func testNoOverlapReturnsNil() {
        XCTAssertNil(ScoreComparator.compare(noop: ["d1": 1], reference: ["d2": 1]))
    }

    func testSinglePairHasNoCorrelationOrLimits() {
        let a = ScoreComparator.compare(noop: ["d1": 3], reference: ["d1": 1])!
        XCTAssertNil(a.r)
        XCTAssertEqual(a.limitsOfAgreement.lower, 2, accuracy: 1e-12)
        XCTAssertEqual(a.limitsOfAgreement.upper, 2, accuracy: 1e-12)
    }
}
