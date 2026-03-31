// MARK: - SettingsEmptyState
// Reusable empty state view for settings sections with no items.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SettingsEmptyState

/// Empty state view for settings sections that contain no items.
///
/// Displays centered muted text inside a standard settings card.
///
/// Example usage:
/// ```swift
/// if agents.isEmpty {
///     SettingsEmptyState(text: "Нет субагентов")
/// }
/// ```
struct SettingsEmptyState: View {

    /// The message to display in the empty state.
    let text: String

    var body: some View {
        HStack {
            Spacer()
            Text(text)
                .font(.system(size: 12))
                .foregroundStyle(DSColor.textMuted)
            Spacer()
        }
        .padding(.vertical, DSSpacing.md)
        .settingsCard()
    }
}
