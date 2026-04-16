// MARK: - ToolbarIconButtonModifier
// Unified style for toolbar icon buttons (settings, changes, play/stop, etc.)
// macOS 14+, Swift 5.10

import SwiftUI

/// Applies consistent size and font to toolbar icon buttons.
///
/// Replaces duplicated `.font(.system(size: 13, weight: .medium))` + `.frame(width: 26, height: 22)`.
///
/// Usage:
/// ```swift
/// Button { action() } label: {
///     Image(systemName: "gear")
/// }
/// .toolbarIconButton()
/// ```
struct ToolbarIconButtonModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.system(size: 13, weight: .medium))
            .frame(width: DSLayout.toolbarIconButtonWidth, height: DSLayout.toolbarButtonHeight)
            .contentShape(Rectangle())
    }
}

extension View {
    /// Applies the standard toolbar icon button sizing.
    func toolbarIconButton() -> some View {
        modifier(ToolbarIconButtonModifier())
    }
}
