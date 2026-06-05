// MARK: - StateFlowProxyTests
// Manual update path used by tests and the SKIE AsyncSequence bridge.

import XCTest
@testable import VibeStudio

@MainActor
final class StateFlowProxyTests: XCTestCase {

    func test_init_seedsValue() {
        let proxy = StateFlowProxy<Int>(initial: 7)
        XCTAssertEqual(proxy.value, 7)
    }

    func test_update_replacesValue() {
        let proxy = StateFlowProxy<String>(initial: "a")
        proxy.update("b")
        XCTAssertEqual(proxy.value, "b")
    }

    func test_update_multipleTimes_keepsLast() {
        let proxy = StateFlowProxy<Int>(initial: 0)
        for i in 1...100 { proxy.update(i) }
        XCTAssertEqual(proxy.value, 100)
    }
}
