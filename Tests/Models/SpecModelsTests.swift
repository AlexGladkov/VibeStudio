// MARK: - SpecModelsTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class SpecModelsTests: XCTestCase {

    // MARK: - SpecFile

    func test_specFile_init_stripsDoubleExtension() {
        let url = URL(fileURLWithPath: "/tmp/feature-a.cs.md")
        let sut = SpecFile(url: url)
        XCTAssertEqual(sut.name, "feature-a")
        XCTAssertEqual(sut.status, .unknown)
        XCTAssertEqual(sut.passCount, 0)
        XCTAssertEqual(sut.totalCount, 0)
    }

    func test_specFile_distinctUUIDs() {
        let url = URL(fileURLWithPath: "/tmp/a.cs.md")
        let a = SpecFile(url: url)
        let b = SpecFile(url: url)
        XCTAssertNotEqual(a.id, b.id)
    }

    // MARK: - SpecStats

    func test_specStats_allPassing_trueOnlyWhenFullyGreen() {
        let date = Date()
        XCTAssertTrue(SpecStats(passing: 5, total: 5, buildDate: date).allPassing)
        XCTAssertFalse(SpecStats(passing: 4, total: 5, buildDate: date).allPassing)
        XCTAssertFalse(SpecStats(passing: 0, total: 0, buildDate: date).allPassing,
                       "Zero total must NOT count as all-passing")
    }

    func test_specStats_failingCount() {
        let s = SpecStats(passing: 3, total: 8, buildDate: Date())
        XCTAssertEqual(s.failing, 5)
    }

    func test_specStats_equality() {
        let date = Date(timeIntervalSince1970: 0)
        let a = SpecStats(passing: 1, total: 2, buildDate: date)
        let b = SpecStats(passing: 1, total: 2, buildDate: date)
        XCTAssertEqual(a, b)
    }
}
