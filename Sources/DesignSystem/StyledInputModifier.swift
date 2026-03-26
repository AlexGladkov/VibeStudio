// MARK: - StyledInputModifier
// Reusable ViewModifier for consistent styled text field appearance.
// macOS 14+, Swift 5.10

import SwiftUI

/// Applies the standard VibeStudio input field styling to a `TextField`.
///
/// Styling includes:
/// - Plain text field style
/// - `DSFont.sidebarItem` font with `DSColor.textPrimary` foreground
/// - Horizontal and vertical padding (`DSSpacing.sm` / `DSSpacing.xs`)
/// - `DSColor.surfaceInput` background
/// - Rounded rectangle clip shape and border stroke (`DSRadius.md`)
struct StyledInputModifier: ViewModifier {

    func body(content: Content) -> some View {
        content
            .textFieldStyle(.plain)
            .font(DSFont.sidebarItem)
            .foregroundStyle(DSColor.textPrimary)
            .padding(.horizontal, DSSpacing.sm)
            .padding(.vertical, DSSpacing.xs)
            .background(DSColor.surfaceInput)
            .clipShape(RoundedRectangle(cornerRadius: DSRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: DSRadius.md)
                    .stroke(DSColor.borderDefault, lineWidth: 1)
            )
    }
}

extension View {
    /// Applies the standard VibeStudio styled input appearance.
    func styledInput() -> some View {
        modifier(StyledInputModifier())
    }
}
