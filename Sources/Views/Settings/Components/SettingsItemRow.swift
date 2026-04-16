// MARK: - SettingsItemRow
// Reusable row for settings item lists with optional edit and delete actions.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SettingsItemRow

/// A row displaying a named item with an optional subtitle, edit button, and delete button.
///
/// Used across all settings panes (Claude, Codex, OpenCode, Qwen) to display
/// agents, commands, memories, plugins, and skills in a consistent style.
///
/// The row does **not** include dividers — those are added by the parent list.
///
/// Usage:
/// ```swift
/// SettingsItemRow(
///     name: agent.name,
///     subtitle: agent.description.isEmpty ? nil : agent.description,
///     showDelete: true,
///     onEdit: { editingAgent = agent },
///     onDelete: { agentToDelete = agent; showDeleteAlert = true }
/// )
/// ```
struct SettingsItemRow: View {

    // MARK: Properties

    /// Primary display name.
    let name: String

    /// Optional secondary line shown below the name in muted style.
    var subtitle: String? = nil

    /// Whether the trash (delete) button is shown. Defaults to `true`.
    var showDelete: Bool = true

    /// Action invoked when the pencil (edit) button is tapped.
    /// When `nil` the edit button is hidden.
    var onEdit: (() -> Void)? = nil

    /// Action invoked when the trash (delete) button is tapped.
    /// Hidden when `nil` or when `showDelete` is `false`.
    var onDelete: (() -> Void)? = nil

    // MARK: - Body

    var body: some View {
        HStack(spacing: DSSpacing.sm) {
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text(name)
                    .font(DSFont.buttonLabel)
                    .foregroundStyle(DSColor.textPrimary)
                    .lineLimit(1)

                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
            }

            Spacer()

            if let onEdit {
                Button(action: onEdit) {
                    Image(systemName: "pencil")
                        .font(DSFont.smallButtonLabel)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }

            if showDelete, let onDelete {
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .font(DSFont.smallButtonLabel)
                        .foregroundStyle(DSColor.gitDeleted)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
    }
}
