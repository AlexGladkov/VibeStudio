// MARK: - SheetActionButtons
// Reusable Cancel/Action button pair for sheet footers.
// macOS 14+, Swift 5.10

import SwiftUI

/// A pair of Cancel (secondary) and Action (primary) buttons for modal sheets.
///
/// Matches the VibeStudio design system button styling: secondary background
/// with border for Cancel, primary filled background for the action.
///
/// - Parameters:
///   - onCancel: Closure invoked when the Cancel button is tapped.
///   - actionLabel: Text label for the primary action button.
///   - isDisabled: When `true`, the action button is dimmed and non-interactive.
///   - isLoading: When `true`, a spinner replaces the action label text.
///   - onAction: Closure invoked when the action button is tapped.
struct SheetActionButtons: View {

    let onCancel: () -> Void
    let actionLabel: String
    var isDisabled: Bool = false
    var isLoading: Bool = false
    let onAction: () -> Void

    var body: some View {
        HStack(spacing: DSSpacing.sm) {
            // Secondary Cancel button
            Button("Cancel") { onCancel() }
                .buttonStyle(.plain)
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.buttonSecondaryText)
                .frame(maxWidth: .infinity)
                .frame(height: DSLayout.gitButtonHeight)
                .background(DSColor.buttonSecondaryBg, in: RoundedRectangle(cornerRadius: DSRadius.md))
                .overlay(
                    RoundedRectangle(cornerRadius: DSRadius.md)
                        .stroke(DSColor.borderDefault, lineWidth: 1)
                )

            // Primary Action button
            Button {
                onAction()
            } label: {
                Group {
                    if isLoading {
                        ProgressView().scaleEffect(0.65)
                    } else {
                        Text(actionLabel)
                    }
                }
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.buttonPrimaryText)
                .frame(maxWidth: .infinity)
                .frame(height: DSLayout.gitButtonHeight)
                .background(
                    (isDisabled || isLoading)
                        ? DSColor.buttonPrimaryBg.opacity(0.4)
                        : DSColor.buttonPrimaryBg,
                    in: RoundedRectangle(cornerRadius: DSRadius.md)
                )
            }
            .buttonStyle(.plain)
            .disabled(isDisabled || isLoading)
        }
    }
}
