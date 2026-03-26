import Foundation
@testable import VibeStudio

/// Mock implementation of ``AICommitServicing`` for unit tests.
///
/// Pre-configure `generateResult` to control the returned commit message.
/// Check `generateCallCount` and `lastDiff` to verify interactions.
actor MockAICommitService: AICommitServicing {

    var generateResult: Result<String, Error> = .success("feat: test commit")
    var generateCallCount = 0
    var lastDiff: String?

    func setGenerateResult(_ result: Result<String, Error>) {
        generateResult = result
    }

    func generateCommitMessage(for diff: String) async throws -> String {
        generateCallCount += 1
        lastDiff = diff
        return try generateResult.get()
    }
}
