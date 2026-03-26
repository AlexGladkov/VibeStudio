// MARK: - AICommitService
// Generates commit messages via Anthropic API.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Implementation

/// Background actor that calls the Anthropic Messages API to generate
/// a conventional commit message from a git diff.
///
/// Uses `URLSession.shared` for networking. The diff is truncated to
/// 8 000 characters before being sent to keep token usage low.
actor AICommitService: AICommitServicing {

    /// Maximum number of diff characters sent to the API.
    static let maxDiffLength = 8_000

    func generateCommitMessage(for diff: String) async throws -> String {
        let apiKey = try resolveAPIKey()
        let request = try buildRequest(apiKey: apiKey, diff: diff)
        let (data, response) = try await URLSession.shared.data(for: request)
        return try parseResponse(data: data, response: response)
    }

    // MARK: - Private Helpers

    private func resolveAPIKey() throws -> String {
        guard let apiKey = ProcessInfo.processInfo.environment["ANTHROPIC_API_KEY"],
              !apiKey.isEmpty else {
            throw AICommitServiceError.missingAPIKey
        }
        return apiKey
    }

    private func buildRequest(apiKey: String, diff: String) throws -> URLRequest {
        let url = URL(string: "https://api.anthropic.com/v1/messages")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")

        let truncatedDiff = String(diff.prefix(Self.maxDiffLength))
        let body: [String: Any] = [
            "model": "claude-haiku-4-5-20251001",
            "max_tokens": 200,
            "messages": [
                [
                    "role": "user",
                    "content": """
                    Write a concise conventional commit message for this git diff. \
                    Output ONLY the commit message, nothing else:\n\n\(truncatedDiff)
                    """
                ]
            ]
        ]

        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        return request
    }

    private func parseResponse(data: Data, response: URLResponse) throws -> String {
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw AICommitServiceError.apiError(statusCode: statusCode)
        }

        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let content = (json["content"] as? [[String: Any]])?.first,
              let text = content["text"] as? String else {
            throw AICommitServiceError.invalidResponseFormat
        }

        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
