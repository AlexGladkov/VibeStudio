// MARK: - AgentDirectoryLoader
// Shared loader for agent markdown directories (Claude, Qwen, ...).
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Loads agent definitions from a directory of markdown files with YAML frontmatter.
///
/// The directory is expected to contain `*.md` files where each file begins with a
/// `---`-delimited YAML frontmatter block describing the agent (name, description, ...).
///
/// Used by ``ClaudeSettingsPaneViewModel``, ``QwenSettingsPaneViewModel`` and any
/// other pane that ships a per-tool `agents/` directory under `~/.<tool>/agents/`.
enum AgentDirectoryLoader {

    /// Scans `directory` for `*.md` files, parses the YAML frontmatter of each,
    /// and returns a list of typed entries.
    ///
    /// - Parameters:
    ///   - directory: The directory to enumerate (typically `~/.<tool>/agents/`).
    ///   - mapping: Closure producing an entry value given the file URL and parsed
    ///     frontmatter. Return `nil` to skip the file.
    /// - Returns: Mapped entries in ascending filename order, or an empty array
    ///   if the directory cannot be read.
    static func load<T>(
        from directory: URL,
        mapping: (URL, FrontmatterFields) -> T?
    ) -> [T] {
        let fileManager = FileManager.default
        guard let contents = try? fileManager.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.nameKey],
            options: [.skipsHiddenFiles]
        ) else {
            // Missing directory is the normal "no agents yet" case — log at debug.
            let path = directory.path
            Logger.settings.debug("AgentDirectoryLoader: directory not readable at \(path, privacy: .public)")
            return []
        }

        let mdFiles = contents
            .filter { $0.pathExtension == "md" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }

        return mdFiles.compactMap { url in
            do {
                let text = try String(contentsOf: url, encoding: .utf8)
                let fields = parseFrontmatter(text)
                return mapping(url, fields)
            } catch {
                let name = url.lastPathComponent
                Logger.settings.error(
                    "AgentDirectoryLoader: failed to read \(name, privacy: .public): \(error.localizedDescription, privacy: .public)"
                )
                return nil
            }
        }
    }

    /// Scans `directory` for files with the given `extension`, maps each to a
    /// typed entry, and returns the results in ascending filename order.
    ///
    /// Unlike ``load(from:mapping:)`` this variant does **not** read or parse
    /// file contents — the `mapping` closure receives only the file URL. Use it
    /// for simple "list files of type X in a directory" panes (Codex memories,
    /// opencode plugins, ...).
    ///
    /// - Parameters:
    ///   - directory: The directory to enumerate.
    ///   - ext: The file extension to keep (without the leading dot, e.g. `"md"`).
    ///   - mapping: Closure producing an entry value given the file URL. Return
    ///     `nil` to skip the file.
    /// - Returns: Mapped entries in ascending filename order, or an empty array
    ///   if the directory cannot be read.
    static func loadFiles<T>(
        from directory: URL,
        extension ext: String,
        mapping: (URL) -> T?
    ) -> [T] {
        let fileManager = FileManager.default
        guard let contents = try? fileManager.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.nameKey],
            options: [.skipsHiddenFiles]
        ) else {
            // Missing directory is the normal "nothing here yet" case — log at debug.
            let path = directory.path
            Logger.settings.debug("AgentDirectoryLoader: directory not readable at \(path, privacy: .public)")
            return []
        }

        return contents
            .filter { $0.pathExtension == ext }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .compactMap(mapping)
    }
}
