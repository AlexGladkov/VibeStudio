// MARK: - ClaudeSettingsPaneViewModel
// Observable view model for the Claude settings pane.
// Manages agents, commands, and skills data loading and deletion.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

// MARK: - AgentEntry

/// Parsed representation of a single agent markdown file from `~/.claude/agents/`.
struct AgentEntry: Identifiable {
    /// File URL used as stable identity.
    let id: String
    let fileURL: URL
    let name: String
    let description: String
}

// MARK: - CommandEntry

/// Parsed representation of a single command markdown file from `~/.claude/commands/`.
struct CommandEntry: Identifiable {
    /// File URL path used as stable identity.
    let id: String
    let fileURL: URL
    let name: String
    let filename: String
}

// MARK: - ClaudeSettingsPaneViewModel

/// Manages agents, commands, and skills state for ``ClaudeSettingsPane``.
///
/// Encapsulates all FileManager operations (loading directory listings,
/// parsing frontmatter, deleting files) so the view only handles UI concerns.
@Observable
@MainActor
final class ClaudeSettingsPaneViewModel {

    // MARK: - Published State

    /// Loaded agent entries from `~/.claude/agents/`.
    var agents: [AgentEntry] = []

    /// Loaded command entries from `~/.claude/commands/`.
    var commands: [CommandEntry] = []

    /// Loaded skill entries from `~/.claude/skills/`.
    var skills: [SkillInfo] = []

    // MARK: - Constants

    static let claudeURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/CLAUDE.md")

    private static let agentsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/agents")

    private static let commandsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/commands")

    private static let skillsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/skills")

    /// Display path with home directory replaced by `~`.
    var displayPath: String {
        Self.claudeURL.tildeAbbreviatedPath
    }

    // MARK: - Data Loading -- Agents

    /// Scans `~/.claude/agents/` for markdown files and parses their frontmatter.
    func loadAgents() {
        let fm = FileManager.default
        let dir = Self.agentsDirectoryURL
        guard let contents = try? fm.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.nameKey],
            options: [.skipsHiddenFiles]
        ) else {
            agents = []
            return
        }

        let mdFiles = contents
            .filter { $0.pathExtension == "md" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }

        agents = mdFiles.compactMap { url in
            guard let text = try? String(contentsOf: url, encoding: .utf8) else { return nil }
            let fields = parseFrontmatter(text)
            return AgentEntry(
                id: url.path,
                fileURL: url,
                name: fields.name.isEmpty ? url.deletingPathExtension().lastPathComponent : fields.name,
                description: fields.description
            )
        }
    }

    // MARK: - Data Loading -- Commands

    /// Scans `~/.claude/commands/` for markdown files and parses the first heading as name.
    func loadCommands() {
        let fm = FileManager.default
        let dir = Self.commandsDirectoryURL
        guard let contents = try? fm.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.nameKey],
            options: [.skipsHiddenFiles]
        ) else {
            commands = []
            return
        }

        let mdFiles = contents
            .filter { $0.pathExtension == "md" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }

        commands = mdFiles.compactMap { url in
            guard let text = try? String(contentsOf: url, encoding: .utf8) else { return nil }
            let parsedName = parseCommandName(text)
            let fname = url.lastPathComponent
            return CommandEntry(
                id: url.path,
                fileURL: url,
                name: parsedName ?? url.deletingPathExtension().lastPathComponent,
                filename: fname
            )
        }
    }

    // MARK: - Data Loading -- Skills

    /// Scans `~/.claude/skills/` for subdirectories containing `SKILL.md` and parses frontmatter.
    func loadSkills() {
        let fm = FileManager.default
        let dir = Self.skillsDirectoryURL
        guard let contents = try? fm.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else {
            skills = []
            return
        }

        let dirs = contents.filter { url in
            (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true
        }.sorted { $0.lastPathComponent < $1.lastPathComponent }

        skills = dirs.compactMap { dirURL in
            let skillFile = dirURL.appendingPathComponent("SKILL.md")
            guard let text = try? String(contentsOf: skillFile, encoding: .utf8) else { return nil }
            let fields = parseFrontmatter(text)
            let writable = fm.isWritableFile(atPath: skillFile.path)
            return SkillInfo(
                id: dirURL.path,
                directoryURL: dirURL,
                skillFileURL: skillFile,
                name: fields.name.isEmpty ? dirURL.lastPathComponent : fields.name,
                description: fields.description,
                isUserInvocable: fields.userInvocable,
                isWritable: writable
            )
        }
    }

    // MARK: - Delete -- Agents

    /// Deletes the agent file from disk and reloads the agent list.
    ///
    /// - Parameter agent: The agent entry to remove.
    func deleteAgent(_ agent: AgentEntry) {
        try? FileManager.default.removeItem(at: agent.fileURL)
        loadAgents()
    }

    // MARK: - Delete -- Commands

    /// Deletes the command file from disk and reloads the command list.
    ///
    /// - Parameter cmd: The command entry to remove.
    func deleteCommand(_ cmd: CommandEntry) {
        try? FileManager.default.removeItem(at: cmd.fileURL)
        loadCommands()
    }

    // MARK: - Private Helpers

    /// Extracts the command name from the first `# ` heading in the file.
    ///
    /// - Parameter content: Full markdown content.
    /// - Returns: Heading text without `# ` prefix, or `nil` if no heading found.
    private func parseCommandName(_ content: String) -> String? {
        let lines = content.components(separatedBy: "\n")
        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("# ") {
                let name = String(trimmed.dropFirst(2)).trimmingCharacters(in: .whitespaces)
                return name.isEmpty ? nil : name
            }
        }
        return nil
    }
}
