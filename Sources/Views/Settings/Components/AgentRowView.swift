// MARK: - AgentRowView
// Reusable subagent list row shared by the Claude and Qwen settings panes.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - AgentRowView

/// A single subagent row rendered inside a settings agents list.
///
/// Wraps ``SettingsItemRow`` with the standard agent presentation: the agent
/// name as the title, its description as the muted subtitle (hidden when
/// empty), and edit / delete actions. Used by both ``ClaudeSettingsPane`` and
/// ``QwenSettingsPane`` so the two lists stay visually identical.
///
/// Dividers are added by the parent list, not by this row.
struct AgentRowView: View {

    // MARK: Properties

    /// The agent to display.
    let agent: AgentEntry

    /// Action invoked when the edit (pencil) button is tapped.
    let onEdit: () -> Void

    /// Action invoked when the delete (trash) button is tapped.
    let onDelete: () -> Void

    // MARK: - Body

    var body: some View {
        SettingsItemRow(
            name: agent.name,
            subtitle: agent.description.isEmpty ? nil : agent.description,
            showDelete: true,
            onEdit: onEdit,
            onDelete: onDelete
        )
    }
}
