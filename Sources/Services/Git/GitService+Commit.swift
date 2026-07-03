// MARK: - GitService+Commit
// Creating commits.

import Foundation

extension GitService {

    // MARK: - GitServicing: Commit

    @discardableResult
    func commit(message: String, at repository: URL) async throws -> String {
        guard !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw GitServiceError.commandFailed(
                command: "commit",
                exitCode: 1,
                stderr: "Commit message cannot be empty"
            )
        }

        let output = try await runGit(["commit", "-m", message], in: repository)

        // Extract commit hash from output (first line usually contains it).
        if let match = output.range(of: #"[0-9a-f]{7,40}"#, options: .regularExpression) {
            return String(output[match])
        }
        return output
    }
}
