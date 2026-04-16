// MARK: - StatusBadgeView
// Reusable badge component for status labels (PASS, FAIL, STAGED, etc.)
// macOS 14+, Swift 5.10

import SwiftUI

/// A compact badge view for displaying status text.
///
/// Replaces duplicated badge code with `.font(.system(size: 9))`,
/// `.padding(.horizontal, 5)`, `.padding(.vertical, 2)`, `cornerRadius: 3`.
///
/// Usage:
/// ```swift
/// StatusBadgeView("PASS", color: DSColor.gitAdded)
/// StatusBadgeView("STAGED", color: DSColor.accentPrimary)
/// ```
struct StatusBadgeView: View {
    let text: String
    let color: Color

    init(_ text: String, color: Color) {
        self.text = text
        self.color = color
    }

    var body: some View {
        Text(text)
            .font(DSFont.badgeSmall)
            .foregroundStyle(color)
            .padding(.horizontal, DSSpacing.xs)
            .padding(.vertical, DSSpacing.xxs)
            .background(color.opacity(0.15), in: RoundedRectangle(cornerRadius: DSRadius.sm))
    }
}
