import XCTest
@testable import VibeStudio

/// Unit tests for ``SpecStatsLineParser`` — the pure `codespeak build` summary
/// line parser extracted from `SpecBuildPanelViewModel`. Covers the three
/// recognised patterns plus the documented `"0 passing"` → `nil` edge case
/// (total 0 fails the `total > 0` guard).
final class SpecStatsParserTests: XCTestCase {

    // MARK: - "N passing, M failing"

    func testPassingAndFailingSumsToTotal() {
        let stats = SpecStatsLineParser.parseStats(from: "3 passing, 1 failing")
        XCTAssertEqual(stats?.passing, 3)
        XCTAssertEqual(stats?.total, 4)
    }

    func testPassingOnlyUsesPassingAsTotal() {
        let stats = SpecStatsLineParser.parseStats(from: "5 passing")
        XCTAssertEqual(stats?.passing, 5)
        XCTAssertEqual(stats?.total, 5)
    }

    // MARK: - "N/M specs passing"

    func testSlashFormParsesPassingAndTotal() {
        let stats = SpecStatsLineParser.parseStats(from: "3/4 specs passing")
        XCTAssertEqual(stats?.passing, 3)
        XCTAssertEqual(stats?.total, 4)
    }

    func testSlashFormSingularSpec() {
        // Regex allows `specs?` — singular "spec" must also match.
        let stats = SpecStatsLineParser.parseStats(from: "1/1 spec passing")
        XCTAssertEqual(stats?.passing, 1)
        XCTAssertEqual(stats?.total, 1)
    }

    func testCaseInsensitiveMatching() {
        let stats = SpecStatsLineParser.parseStats(from: "2 PASSING, 1 FAILING")
        XCTAssertEqual(stats?.passing, 2)
        XCTAssertEqual(stats?.total, 3)
    }

    // MARK: - Edge cases → nil

    func testZeroPassingYieldsNil() {
        // "0 passing": passing = 0, total = 0, total > 0 guard fails → nil.
        // Documented actual behaviour: a fully-failing summary produces no stats.
        XCTAssertNil(SpecStatsLineParser.parseStats(from: "0 passing"))
    }

    func testGarbageLineYieldsNil() {
        XCTAssertNil(SpecStatsLineParser.parseStats(from: "Building specs..."))
    }

    func testEmptyLineYieldsNil() {
        XCTAssertNil(SpecStatsLineParser.parseStats(from: ""))
    }
}
