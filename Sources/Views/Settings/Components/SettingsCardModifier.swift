// MARK: - SettingsCardModifier
// Standard raised card appearance for settings list sections.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SettingsCardModifier

/// Applies the standard settings card appearance: raised surface background,
/// rounded corners, and a subtle border.
struct SettingsCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(DSColor.surfaceRaised)
            .clipShape(RoundedRectangle(cornerRadius: DSRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: DSRadius.md)
                    .stroke(DSColor.borderDefault, lineWidth: 1)
            )
    }
}

extension View {
    /// Applies the standard settings card appearance (raised surface, rounded corners, border).
    func settingsCard() -> some View {
        modifier(SettingsCardModifier())
    }
}
