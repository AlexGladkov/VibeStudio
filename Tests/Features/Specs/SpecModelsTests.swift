import XCTest
@testable import VibeStudio

/// Unit tests for the Specs domain models: ``SpecFile`` name derivation (double
/// extension stripping) and ``SpecStats`` computed aggregates. Pure value-type
/// logic, no FS access (URLs are constructed in-memory).
final class SpecModelsTests: XCTestCase {

    // MARK: - SpecFile name derivation

    func testDoubleExtensionStripped() {
        // `.cs.md` → two deletingPathExtension calls remove both.
        let file = SpecFile(url: URL(fileURLWithPath: "/p/auth.cs.md"))
        XCTAssertEqual(file.name, "auth")
    }

    func testSingleExtensionStripsOnce() {
        // `x.md`: first delete → "x", second delete is a no-op → "x".
        let file = SpecFile(url: URL(fileURLWithPath: "/p/x.md"))
        XCTAssertEqual(file.name, "x")
    }

    func testTripleDottedNameStripsLastTwoComponents() {
        // `a.b.c.md`: delete → "a.b.c", delete → "a.b".
        let file = SpecFile(url: URL(fileURLWithPath: "/p/a.b.c.md"))
        XCTAssertEqual(file.name, "a.b")
    }

    func testInitialStatusIsUnknownWithZeroCounts() {
        let file = SpecFile(url: URL(fileURLWithPath: "/p/auth.cs.md"))
        XCTAssertEqual(file.status, .unknown)
        XCTAssertEqual(file.passCount, 0)
        XCTAssertEqual(file.totalCount, 0)
    }

    // MARK: - SpecStats.allPassing

    func testAllPassingFalseWhenZeroTotal() {
        // 0/0 → total > 0 is false → NOT all passing.
        let stats = SpecStats(passing: 0, total: 0, buildDate: Date(timeIntervalSince1970: 0))
        XCTAssertFalse(stats.allPassing)
    }

    func testAllPassingTrueWhenAllPass() {
        let stats = SpecStats(passing: 3, total: 3, buildDate: Date(timeIntervalSince1970: 0))
        XCTAssertTrue(stats.allPassing)
    }

    func testAllPassingFalseWhenSomeFail() {
        let stats = SpecStats(passing: 2, total: 3, buildDate: Date(timeIntervalSince1970: 0))
        XCTAssertFalse(stats.allPassing)
    }

    // MARK: - SpecStats.failing

    func testFailingCount() {
        let stats = SpecStats(passing: 2, total: 3, buildDate: Date(timeIntervalSince1970: 0))
        XCTAssertEqual(stats.failing, 1)
    }

    func testFailingZeroWhenAllPass() {
        let stats = SpecStats(passing: 5, total: 5, buildDate: Date(timeIntervalSince1970: 0))
        XCTAssertEqual(stats.failing, 0)
    }
}
