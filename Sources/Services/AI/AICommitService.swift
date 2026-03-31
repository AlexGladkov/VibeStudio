// MARK: - AICommitService
// Generates commit messages via Anthropic API.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Private Codable Structs

private struct AnthropicRequest: Encodable {
    let model: String
    let maxTokens: Int
    let messages: [Message]

    enum CodingKeys: String, CodingKey {
        case model
        case maxTokens = "max_tokens"
        case messages
    }

    struct Message: Encodable {
        let role: String
        let content: String
    }
}

private struct AnthropicResponse: Decodable {
    let content: [ContentBlock]

    struct ContentBlock: Decodable {
        let text: String
    }
}

// MARK: - Implementation

/// Background actor that calls the Anthropic Messages API to generate
/// a conventional commit message from a git diff.
///
/// Uses `URLSession.shared` for networking. The diff is truncated to
/// `AIConstants.maxDiffLength` characters before being sent to keep token usage low.
actor AICommitService: AICommitServicing {

    func generateCommitMessage(for diff: String) async throws -> String {
        let apiKey = try resolveAPIKey()
        let request = try buildRequest(apiKey: apiKey, diff: diff)
        let (data, response) = try await URLSession.shared.data(for: request)
        return try parseResponse(data: data, response: response)
    }

    // MARK: - Private Helpers

    private func resolveAPIKey() throws -> String {
        // Keychain first (consistent with ToolbarViewModel.startAssistant).
        if let keychainKey = KeychainHelper.load(account: "ANTHROPIC_API_KEY"),
           !keychainKey.isEmpty {
            return keychainKey
        }
        // Fallback: environment variable.
        guard let envKey = ProcessInfo.processInfo.environment["ANTHROPIC_API_KEY"],
              !envKey.isEmpty else {
            throw AICommitServiceError.missingAPIKey
        }
        return envKey
    }

    private func buildRequest(apiKey: String, diff: String) throws -> URLRequest {
        guard let url = URL(string: AIConstants.anthropicAPIURL) else {
            throw AICommitServiceError.invalidConfiguration
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue(AIConstants.anthropicVersion, forHTTPHeaderField: "anthropic-version")

        let truncatedDiff = String(diff.prefix(AIConstants.maxDiffLength))
        let body = AnthropicRequest(
            model: AIConstants.commitModel,
            maxTokens: AIConstants.commitMaxTokens,
            messages: [
                AnthropicRequest.Message(
                    role: "user",
                    content: """
                    Write a concise conventional commit message for this git diff. \
                    Output ONLY the commit message, nothing else:

                    <diff>
                    \(truncatedDiff)
                    </diff>
                    """
                )
            ]
        )

        request.httpBody = try JSONEncoder().encode(body)
        return request
    }

    private func parseResponse(data: Data, response: URLResponse) throws -> String {
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw AICommitServiceError.apiError(statusCode: statusCode)
        }

        let decoded = try JSONDecoder().decode(AnthropicResponse.self, from: data)
        guard let text = decoded.content.first?.text else {
            throw AICommitServiceError.invalidResponseFormat
        }

        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
