// MARK: - SpecWizardViewModel
// Multi-step wizard for creating a new .cs.md spec with AI generation.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

// MARK: - WizardStep

enum WizardStep: Int, CaseIterable {
    case nameAndDescription = 0
    case generating = 1
    case previewAndSave = 2
}

// MARK: - SpecWizardViewModel

/// ViewModel for `SpecWizardSheet`.
///
/// Steps:
/// 1. User enters spec name + description.
/// 2. AI generates spec content via Anthropic API (haiku model).
/// 3. User previews/edits content and saves to `spec/<name>.cs.md`.
@Observable
@MainActor
final class SpecWizardViewModel {

    // MARK: - State

    var currentStep: WizardStep = .nameAndDescription

    /// Spec name (becomes filename: `spec/<name>.cs.md`).
    var specName: String = ""

    /// User description of what the spec should cover.
    var description: String = ""

    /// Generated (and possibly user-edited) spec content.
    var generatedContent: String = ""

    /// Error messages.
    private(set) var errorMessage: String?

    /// True while AI generation is in progress.
    private(set) var isGenerating = false

    /// True while saving to disk.
    private(set) var isSaving = false

    // MARK: - Validation

    var canGenerate: Bool {
        !specName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var canSave: Bool {
        !generatedContent.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var sanitizedName: String {
        specName
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: " ", with: "-")
            .filter { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" }
    }

    // MARK: - Private

    private let projectPath: URL

    // MARK: - Init

    init(projectPath: URL) {
        self.projectPath = projectPath
    }

    // MARK: - Actions

    /// Step 1 → 2: generate spec content via Anthropic API.
    func generateSpec() async {
        guard canGenerate else { return }
        isGenerating = true
        errorMessage = nil
        currentStep = .generating

        do {
            generatedContent = try await generateContent()
            currentStep = .previewAndSave
        } catch {
            errorMessage = "Generation failed: \(error.localizedDescription)"
            currentStep = .nameAndDescription
            Logger.services.error("SpecWizardViewModel: \(error.localizedDescription, privacy: .public)")
        }
        isGenerating = false
    }

    /// Step 3: save generated content to `spec/<name>.cs.md`.
    func save() async -> Bool {
        guard canSave else { return false }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        let specDir = projectPath.appending(path: "spec")
        let fileURL = specDir.appending(path: "\(sanitizedName).cs.md")

        do {
            try FileManager.default.createDirectory(at: specDir, withIntermediateDirectories: true)
            try Data(generatedContent.utf8).write(to: fileURL, options: .atomic)
            Logger.services.info("SpecWizardViewModel: saved \(fileURL.lastPathComponent, privacy: .public)")
            return true
        } catch {
            errorMessage = "Save failed: \(error.localizedDescription)"
            Logger.services.error("SpecWizardViewModel: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }

    // MARK: - AI Generation

    private struct AnthropicRequest: Encodable {
        let model: String
        let maxTokens: Int
        let messages: [Message]
        enum CodingKeys: String, CodingKey {
            case model, messages
            case maxTokens = "max_tokens"
        }
        struct Message: Encodable { let role: String; let content: String }
    }

    private struct AnthropicResponse: Decodable {
        let content: [Block]
        struct Block: Decodable { let text: String }
    }

    private func generateContent() async throws -> String {
        // Resolve API key
        let apiKey: String
        if let k = KeychainHelper.load(account: "ANTHROPIC_API_KEY"), !k.isEmpty {
            apiKey = k
        } else if let k = ProcessInfo.processInfo.environment["ANTHROPIC_API_KEY"], !k.isEmpty {
            apiKey = k
        } else {
            throw SpecWizardError.missingAPIKey
        }

        guard let url = URL(string: AIConstants.anthropicAPIURL) else {
            throw SpecWizardError.invalidConfiguration
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue(AIConstants.anthropicVersion, forHTTPHeaderField: "anthropic-version")

        let body = AnthropicRequest(
            model: AIConstants.commitModel,
            maxTokens: 800,
            messages: [
                AnthropicRequest.Message(
                    role: "user",
                    content: """
                    Generate a CodeSpeak spec file for the following feature. \
                    Use Markdown with clear sections for Description, Acceptance Criteria, \
                    and Test Cases. Output ONLY the spec content, no preamble:

                    <description>
                    Feature: \(specName)
                    \(description)
                    </description>
                    """
                )
            ]
        )
        request.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw SpecWizardError.apiError(statusCode: code)
        }

        let decoded = try JSONDecoder().decode(AnthropicResponse.self, from: data)
        guard let text = decoded.content.first?.text else {
            throw SpecWizardError.invalidResponse
        }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

// MARK: - SpecWizardError

enum SpecWizardError: LocalizedError {
    case missingAPIKey
    case invalidConfiguration
    case apiError(statusCode: Int)
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .missingAPIKey:        return "ANTHROPIC_API_KEY not set. Add it in Settings → Claude."
        case .invalidConfiguration: return "Invalid Anthropic API URL configuration."
        case .apiError(let code):   return "Anthropic API error (HTTP \(code))."
        case .invalidResponse:      return "Unexpected response format from Anthropic API."
        }
    }
}
