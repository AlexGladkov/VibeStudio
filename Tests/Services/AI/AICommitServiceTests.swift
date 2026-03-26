// MARK: - AICommitServiceTests
// Unit tests for AICommitService (Phase 2).
// macOS 14+, Swift 5.10
//
// Note: AICommitService reads the API key from ProcessInfo.processInfo.environment
// which cannot be overridden in a unit test process. Tests that require a missing
// API key rely on the CI/test environment not having ANTHROPIC_API_KEY set.
// Integration tests requiring a real API key are excluded.

import XCTest
@testable import VibeStudio

final class AICommitServiceTests: XCTestCase {

    // MARK: - maxDiffLength

    func testMaxDiffLengthIsReasonable() {
        // AICommitService truncates diffs to maxDiffLength characters
        // before sending to the API. Verify it is within a sane range.
        let limit = AICommitService.maxDiffLength

        XCTAssertGreaterThan(limit, 1_000, "maxDiffLength should be at least 1000 characters")
        XCTAssertLessThan(limit, 100_000, "maxDiffLength should be less than 100000 characters")
        XCTAssertEqual(limit, 8_000, "Expected maxDiffLength to be 8000")
    }

    // MARK: - Missing API key

    func testMissingAPIKeyThrows() async throws {
        // This test only works when ANTHROPIC_API_KEY is not set in the
        // test process environment. If it is set, we skip.
        let keyExists = ProcessInfo.processInfo.environment["ANTHROPIC_API_KEY"] != nil
        try XCTSkipIf(
            keyExists,
            "ANTHROPIC_API_KEY is set in environment; cannot test missing key path"
        )

        let service = AICommitService()
        do {
            _ = try await service.generateCommitMessage(for: "diff --git a/file.swift")
            XCTFail("Expected AICommitServiceError.missingAPIKey to be thrown")
        } catch let error as AICommitServiceError {
            guard case .missingAPIKey = error else {
                return XCTFail("Expected .missingAPIKey, got \(error)")
            }
        }
    }

    // MARK: - Mock service interaction

    func testMockAICommitServiceReturnsConfiguredResult() async throws {
        let mock = MockAICommitService()
        let message = try await mock.generateCommitMessage(for: "some diff")

        XCTAssertEqual(message, "feat: test commit")

        let callCount = await mock.generateCallCount
        XCTAssertEqual(callCount, 1)

        let lastDiff = await mock.lastDiff
        XCTAssertEqual(lastDiff, "some diff")
    }

    func testMockAICommitServicePropagatesError() async throws {
        let mock = MockAICommitService()
        await mock.setGenerateResult(
            .failure(AICommitServiceError.missingAPIKey)
        )

        do {
            _ = try await mock.generateCommitMessage(for: "any diff")
            XCTFail("Expected error to be thrown")
        } catch let error as AICommitServiceError {
            guard case .missingAPIKey = error else {
                return XCTFail("Expected .missingAPIKey, got \(error)")
            }
        }
    }

    // MARK: - Error descriptions

    func testErrorDescriptions() {
        let errors: [(AICommitServiceError, String)] = [
            (.missingAPIKey, "ANTHROPIC_API_KEY not set in environment"),
            (.apiError(statusCode: 429), "Anthropic API returned status 429"),
            (.invalidResponseFormat, "Invalid API response format"),
        ]

        for (error, expectedSubstring) in errors {
            let description = error.errorDescription ?? ""
            XCTAssertTrue(
                description.contains(expectedSubstring),
                "'\(description)' should contain '\(expectedSubstring)'"
            )
        }
    }
}
