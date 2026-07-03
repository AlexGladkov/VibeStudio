// MARK: - AgentEntry
// Shared model describing a single agent markdown file from a per-tool
// `~/.<tool>/agents/` directory (Claude, Qwen, ...).
// macOS 14+, Swift 5.10

import Foundation

/// Parsed representation of a single agent markdown file.
///
/// Loaded from a per-tool `agents/` directory (e.g. `~/.claude/agents/` or
/// `~/.qwen/agents/`) via ``AgentDirectoryLoader``. Shared by every settings
/// pane view model that lists subagents so the entry shape stays consistent.
struct AgentEntry: Identifiable {
    /// File URL path used as stable identity.
    let id: String
    let fileURL: URL
    let name: String
    let description: String
}
