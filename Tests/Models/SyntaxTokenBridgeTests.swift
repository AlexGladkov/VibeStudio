import XCTest
@testable import VibeStudio

final class SyntaxTokenBridgeTests: XCTestCase {

    func test_asciiRoundTrip() {
        let r = SyntaxTokenBridge.nsRange(start: 2, end: 5, in: "abcdef")
        XCTAssertEqual(r, NSRange(location: 2, length: 3))
    }

    func test_clampsPastEnd() {
        let r = SyntaxTokenBridge.nsRange(start: 4, end: 99, in: "abc")
        XCTAssertEqual(r, NSRange(location: 3, length: 0))
    }

    func test_clampsNegativeStart() {
        let r = SyntaxTokenBridge.nsRange(start: -5, end: 2, in: "abc")
        XCTAssertEqual(r, NSRange(location: 0, length: 2))
    }

    func test_surrogatePair() {
        // "👩" occupies 2 UTF-16 code units.
        let s = "x👩y"
        XCTAssertEqual(s.utf16.count, 4)
        let r = SyntaxTokenBridge.nsRange(start: 1, end: 3, in: s)
        XCTAssertEqual(r, NSRange(location: 1, length: 2))
    }

    func test_inverseOffsets() {
        let (a, b) = SyntaxTokenBridge.kmpOffsets(from: NSRange(location: 3, length: 4))
        XCTAssertEqual(a, 3)
        XCTAssertEqual(b, 7)
    }

    func test_emptyRangeWhenStartBeyondEnd() {
        let r = SyntaxTokenBridge.nsRange(start: 5, end: 3, in: "abcdefgh")
        // Clamped: start=5, end=max(5, 3) == 5  → length 0
        XCTAssertEqual(r, NSRange(location: 5, length: 0))
    }
}
