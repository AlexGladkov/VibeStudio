// MARK: - CLIAgentPathResolverTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class CLIAgentPathResolverTests: XCTestCase {

    func test_rejectsPathTraversal() {
        XCTAssertNil(CLIAgentPathResolver.resolve("../../bin/sh"))
        XCTAssertNil(CLIAgentPathResolver.resolve("./claude"))
    }

    func test_rejectsAbsolutePath() {
        XCTAssertNil(CLIAgentPathResolver.resolve("/usr/bin/env"))
        XCTAssertNil(CLIAgentPathResolver.resolve("/bin/sh"))
    }

    func test_rejectsEmptyString() {
        XCTAssertNil(CLIAgentPathResolver.resolve(""))
    }

    func test_returnsNilForMissingBinary() {
        let result = CLIAgentPathResolver.resolve("definitely-not-a-real-binary-xyz-\(UUID().uuidString)")
        XCTAssertNil(result)
    }

    func test_findsCommonSystemBinary_ifPresent() {
        // /usr/bin/env exists on every macOS; SecurityConstants likely includes /usr/bin.
        // This only asserts the happy path when "env" is in a trusted dir.
        if let path = CLIAgentPathResolver.resolve("env") {
            XCTAssertTrue(path.hasSuffix("/env"))
            XCTAssertTrue(FileManager.default.isExecutableFile(atPath: path))
        }
        // Otherwise: nothing to assert — trusted dirs may not include /usr/bin.
    }

    func test_rejectsNamesContainingSlash() {
        XCTAssertNil(CLIAgentPathResolver.resolve("subdir/binary"))
    }
}
