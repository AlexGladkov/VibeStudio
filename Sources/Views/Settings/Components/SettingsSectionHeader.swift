// MARK: - SettingsSectionHeader
// Reusable section header for settings lists with optional add button.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SettingsSectionHeader

/// Section header for settings lists with an optional add (+) button.
///
/// Used across all settings panes (Claude, Codex, OpenCode, Qwen) to display
/// a labelled section title with a consistent style, and optionally a plus
/// button to trigger item creation.
///
/// Usage:
/// ```swift
/// SettingsSectionHeader(title: "Субагенты", showAddButton: true) {
///     showNewAgent = true
/// }
/// ```
struct SettingsSectionHeader: View {

    // MARK: Properties

    /// The label displayed as the section title.
    let title: String

    /// Whether the add (+) button is visible. Defaults to `false`.
    var showAddButton: Bool = false

    /// Action invoked when the add button is tapped. Required when `showAddButton` is `true`.
    var onAdd: (() -> Void)? = nil

    // MARK: - Body

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(DSColor.textSecondary)

            Spacer()

            if showAddButton, let onAdd {
                Button(action: onAdd) {
                    Image(systemName: "plus")
                        .font(.system(size: 11, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
        }
    }
}
