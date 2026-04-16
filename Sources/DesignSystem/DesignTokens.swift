// MARK: - VibeStudio Design Tokens
// Single source of truth for all visual tokens.
// See .specs/design-system.md for rationale and specifications.
// macOS 14+, Swift 5.10
//
// Tokens are split across focused files:
//   DSColor.swift        — color tokens (surfaces, text, borders, syntax, git, diff…)
//   DSFont.swift         — typography tokens (system + terminal fonts)
//   DSLayout.swift       — layout constants (sizes, widths, heights)
//   DSTerminalColors.swift — ANSI palette + GitFileStatus color mapping
//   DesignTokens.swift   — spacing + radius tokens (this file)

import SwiftUI

// MARK: - Spacing Tokens

/// Spacing scale based on a 4pt grid.
///
/// Allowed values: 2, 4, 8, 12, 16, 20, 24.
/// 2pt is reserved for micro-gaps only (between tabs, icon-text inside buttons).
enum DSSpacing {
    /// 2pt -- micro-gaps between tabs.
    static let xxs: CGFloat = 2
    /// 4pt -- padding inside small elements.
    static let xs: CGFloat = 4
    /// 8pt -- standard gap between elements, section padding.
    static let sm: CGFloat = 8
    /// 12pt -- sidebar horizontal padding, button padding.
    static let md: CGFloat = 12
    /// 16pt -- tree indent per level, large gaps.
    static let lg: CGFloat = 16
    /// 20pt -- vertical padding between large blocks.
    static let xl: CGFloat = 20
    /// 24pt -- edge-of-window padding (reserved for future).
    static let xxl: CGFloat = 24
}

// MARK: - Border Radius Tokens

/// Corner radius tokens.
enum DSRadius {
    /// 4pt -- hover/selection on sidebar rows.
    static let sm: CGFloat = 4
    /// 6pt -- tabs, buttons, input fields, dropdowns.
    static let md: CGFloat = 6
    /// 8pt -- modals, popovers.
    static let lg: CGFloat = 8
}
