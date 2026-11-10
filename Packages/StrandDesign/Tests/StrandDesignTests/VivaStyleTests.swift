import XCTest
import SwiftUI
@testable import StrandDesign

final class VivaStyleTests: XCTestCase {
    func testRecoveryColorBands() {
        XCTAssertEqual(VivaStyle.recoveryColor(percent: 67), VivaStyle.recoveryGreen)
        XCTAssertEqual(VivaStyle.recoveryColor(percent: 66.9), VivaStyle.recoveryYellow)
        XCTAssertEqual(VivaStyle.recoveryColor(percent: 34), VivaStyle.recoveryYellow)
        XCTAssertEqual(VivaStyle.recoveryColor(percent: 33.9), VivaStyle.recoveryRed)
        XCTAssertEqual(VivaStyle.recoveryColor(percent: nil), VivaStyle.textSecondary)
    }
}
