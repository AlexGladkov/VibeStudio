// MARK: - AgentEditorViewModel
// File I/O + frontmatter-derived naming for the Claude subagent editor sheet.
// macOS 14+, Swift 5.10

import Foundation
import Observation

// MARK: - AgentEditorViewModel

/// Backs ``AgentEditorSheet``: edits an existing agent file in place, or creates
/// a new one in `~/.claude/agents/` deriving the filename from the `name:`
/// frontmatter field.
@Observable
@MainActor
final class AgentEditorViewModel {

    // MARK: - Save Outcome

    /// Result of a successful ``save()``.
    enum SaveOutcome {
        /// Existing file overwritten — keep the sheet open.
        case saved
        /// New file created — the sheet should dismiss.
        case savedAndDismiss
    }

    // MARK: - State

    var content: String = ""
    private(set) var savedContent: String = ""
    private(set) var saveError: String?

    var hasUnsavedChanges: Bool { content != savedContent }

    /// Parsed agent name from frontmatter (for the toolbar title).
    var agentName: String {
        let parsed = parseFrontmatter(content).name
        return parsed.isEmpty ? "Новый агент" : parsed
    }

    /// Subtitle shown below the agent name in the toolbar.
    var agentSubtitle: String {
        guard let url = fileURL else { return "Новый файл" }
        return url.tildeAbbreviatedPath
    }

    // MARK: - Configuration

    /// File to edit, or `nil` to create a new agent.
    let fileURL: URL?

    /// Directory new agent files are written to. Injectable for testing;
    /// defaults to the real `~/.claude/agents/`.
    let agentsDirectoryURL: URL

    // MARK: - Constants

    /// Real on-disk agents directory used in production.
    static let defaultAgentsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/agents")

    private static let newAgentTemplate = """
        ---
        name: my-agent
        description:
        model: sonnet
        color: blue
        ---

        Ты — агент, выполняющий...
        """

    // MARK: - Init

    init(fileURL: URL?, agentsDirectoryURL: URL = AgentEditorViewModel.defaultAgentsDirectoryURL) {
        self.fileURL = fileURL
        self.agentsDirectoryURL = agentsDirectoryURL
    }

    // MARK: - File I/O

    func load() {
        if let url = fileURL {
            let text = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
            content = text
            savedContent = text
        } else {
            content = Self.newAgentTemplate
            savedContent = ""
        }
    }

    /// Persists the agent. Returns `nil` when validation fails or the write
    /// throws (in which case ``saveError`` is populated).
    func save() -> SaveOutcome? {
        saveError = nil

        if let url = fileURL {
            // Editing existing file — overwrite in place.
            do {
                try content.write(to: url, atomically: true, encoding: .utf8)
                savedContent = content
                return .saved
            } catch {
                saveError = "Ошибка: \(error.localizedDescription)"
                return nil
            }
        }

        // New agent — derive filename from frontmatter name:.
        let parsed = parseFrontmatter(content)
        let rawName = parsed.name
        guard !rawName.isEmpty else {
            saveError = "Укажите поле name в frontmatter"
            return nil
        }
        let sanitized = AgentNameSanitizer.sanitize(rawName)
        guard !sanitized.isEmpty else {
            saveError = "Недопустимое имя агента: используйте латинские буквы, цифры и дефисы"
            return nil
        }

        let dir = agentsDirectoryURL
        let targetURL = dir.appendingPathComponent("\(sanitized).md")

        do {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try content.write(to: targetURL, atomically: true, encoding: .utf8)
            savedContent = content
            return .savedAndDismiss
        } catch {
            saveError = "Ошибка: \(error.localizedDescription)"
            return nil
        }
    }
}
