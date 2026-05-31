// MARK: - StatusBadgeView
// Reusable badge component for status labels (PASS, FAIL, STAGED, etc.)
// macOS 14+, Swift 5.10

import SwiftUI

/// Background fill strategy for ``StatusBadgeView``.
enum StatusBadgeBackground {
    /// Tinted background derived from the foreground color with `opacity: 0.15`.
    /// This is the default — matches the original component behaviour.
    case tinted
    /// Explicit solid background color (e.g. `DSColor.diffAddedBg`).
    case solid(Color)
}

/// A compact badge view for displaying status text.
///
/// Replaces duplicated badge code with `.font(.system(size: 9))`,
/// `.padding(.horizontal, 5)`, `.padding(.vertical, 2)`, `cornerRadius: 3`.
///
/// Parameterisable to cover three concrete call-sites:
/// * PASS/FAIL spec badge — `.solid(DSColor.diffAddedBg / diffDeletedBg)`
/// * CodeSpeak stats `12/15` — `.solid(...)` + `smallButtonLabel` font
/// * TabItem `CS:N/M` — `.solid(DSColor.surfaceOverlay)` + 1pt vertical padding
///
/// Usage:
/// ```swift
/// StatusBadgeView("PASS", color: DSColor.gitAdded)                       // tinted default
/// StatusBadgeView("STAGED", color: DSColor.accentPrimary)                // tinted default
/// StatusBadgeView("PASS", color: DSColor.gitAdded,
///                 background: .solid(DSColor.diffAddedBg))
/// ```
struct StatusBadgeView: View {
    let text: String
    let color: Color
    let font: Font
    let background: StatusBadgeBackground
    let verticalPadding: CGFloat
    let horizontalPadding: CGFloat

    init(
        _ text: String,
        color: Color,
        font: Font = DSFont.badgeSmall,
        background: StatusBadgeBackground = .tinted,
        verticalPadding: CGFloat = DSSpacing.xxs,
        horizontalPadding: CGFloat = DSSpacing.xs
    ) {
        self.text = text
        self.color = color
        self.font = font
        self.background = background
        self.verticalPadding = verticalPadding
        self.horizontalPadding = horizontalPadding
    }

    var body: some View {
        Text(text)
            .font(font)
            .foregroundStyle(color)
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, verticalPadding)
            .background(backgroundFill, in: RoundedRectangle(cornerRadius: DSRadius.sm))
    }

    private var backgroundFill: Color {
        switch background {
        case .tinted:
            return color.opacity(0.15)
        case .solid(let fill):
            return fill
        }
    }
}
