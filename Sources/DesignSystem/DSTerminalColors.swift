// MARK: - DSTerminalColors
// ANSI color palette for SwiftTerm terminal integration.
// macOS 14+, Swift 5.10

import AppKit
import SwiftUI

/// ANSI color palette for SwiftTerm integration.
///
/// Provides separate dark and light palettes. The `palette` computed property
/// resolves the correct one from `NSApp.effectiveAppearance` at the call site,
/// so terminal views always receive theme-appropriate colors.
///
/// 16 colors: Normal (0-7) + Bright (8-15),
/// plus foreground, background, cursor, and selection colors.
enum DSTerminalColors {

    // MARK: - Dark Palette

    /// ANSI 16-color palette for dark theme.
    ///
    /// Index 0-7: Normal colors (Black, Red, Green, Yellow, Blue, Magenta, Cyan, White).
    /// Index 8-15: Bright variants.
    static let darkPalette: [NSColor] = [
        // Normal (0-7)
        NSColor(hex: "#1A1B1E"),  // Black
        NSColor(hex: "#F85149"),  // Red
        NSColor(hex: "#3FB950"),  // Green
        NSColor(hex: "#E2B93D"),  // Yellow
        NSColor(hex: "#4A9EFF"),  // Blue
        NSColor(hex: "#BC8CFF"),  // Magenta
        NSColor(hex: "#39C5CF"),  // Cyan
        NSColor(hex: "#D4D4D8"),  // White
        // Bright (8-15)
        NSColor(hex: "#55565C"),  // Bright Black
        NSColor(hex: "#FF7B72"),  // Bright Red
        NSColor(hex: "#56D364"),  // Bright Green
        NSColor(hex: "#E3C04B"),  // Bright Yellow
        NSColor(hex: "#79C0FF"),  // Bright Blue
        NSColor(hex: "#D2A8FF"),  // Bright Magenta
        NSColor(hex: "#56D4DD"),  // Bright Cyan
        NSColor(hex: "#FFFFFF"),  // Bright White
    ]

    // MARK: - Light Palette

    /// ANSI 16-color palette for light theme.
    static let lightPalette: [NSColor] = [
        // Normal (0-7)
        NSColor(hex: "#FFFFFF"),  // Black (background)
        NSColor(hex: "#C42B2B"),  // Red
        NSColor(hex: "#28843B"),  // Green
        NSColor(hex: "#B59400"),  // Yellow
        NSColor(hex: "#0066FF"),  // Blue
        NSColor(hex: "#9C37CC"),  // Magenta
        NSColor(hex: "#0E8C8C"),  // Cyan
        NSColor(hex: "#1D1D1F"),  // White (foreground)
        // Bright (8-15)
        NSColor(hex: "#AEAEB2"),  // Bright Black
        NSColor(hex: "#FF3A30"),  // Bright Red
        NSColor(hex: "#34C759"),  // Bright Green
        NSColor(hex: "#D4A017"),  // Bright Yellow
        NSColor(hex: "#1E90FF"),  // Bright Blue
        NSColor(hex: "#AF52DE"),  // Bright Magenta
        NSColor(hex: "#5AC8FA"),  // Bright Cyan
        NSColor(hex: "#000000"),  // Bright White
    ]

    /// ANSI palette appropriate for the current effective appearance.
    ///
    /// Reads `NSApp.effectiveAppearance` at the call site — call this
    /// whenever you need to install colors into a terminal view.
    static var palette: [NSColor] {
        let isDark = NSApp.effectiveAppearance
            .bestMatch(from: [.darkAqua, .accessibilityHighContrastDarkAqua]) != nil
        return isDark ? darkPalette : lightPalette
    }

    // MARK: - Dark Special Colors

    /// Default foreground for dark theme.
    static let darkForeground = NSColor(hex: "#D4D4D8")
    /// Default background for dark theme.
    static let darkBackground = NSColor(hex: "#1A1B1E")
    /// Cursor color for dark theme.
    static let darkCursor     = NSColor(hex: "#D4D4D8")
    /// Selection highlight for dark theme.
    static let darkSelection  = NSColor(hex: "#264F78")

    // MARK: - Light Special Colors

    /// Default foreground for light theme.
    static let lightForeground = NSColor(hex: "#1D1D1F")
    /// Default background for light theme.
    static let lightBackground = NSColor(hex: "#FFFFFF")
    /// Cursor color for light theme.
    static let lightCursor     = NSColor(hex: "#1D1D1F")
    /// Selection highlight for light theme.
    static let lightSelection  = NSColor(hex: "#BDD5FB")

    // MARK: - Effective Colors

    /// Terminal foreground resolved from current effective appearance.
    static var foreground: NSColor {
        let isDark = NSApp.effectiveAppearance
            .bestMatch(from: [.darkAqua, .accessibilityHighContrastDarkAqua]) != nil
        return isDark ? darkForeground : lightForeground
    }

    /// Terminal background resolved from current effective appearance.
    static var background: NSColor {
        let isDark = NSApp.effectiveAppearance
            .bestMatch(from: [.darkAqua, .accessibilityHighContrastDarkAqua]) != nil
        return isDark ? darkBackground : lightBackground
    }

    /// Cursor color resolved from current effective appearance.
    static var cursor: NSColor {
        let isDark = NSApp.effectiveAppearance
            .bestMatch(from: [.darkAqua, .accessibilityHighContrastDarkAqua]) != nil
        return isDark ? darkCursor : lightCursor
    }

    /// Selection color resolved from current effective appearance.
    static var selection: NSColor {
        let isDark = NSApp.effectiveAppearance
            .bestMatch(from: [.darkAqua, .accessibilityHighContrastDarkAqua]) != nil
        return isDark ? darkSelection : lightSelection
    }
}

// MARK: - GitFileStatus Color Mapping

extension GitFileStatus {
    /// Design-system color for this git file status.
    var color: Color {
        switch self {
        case .modified:  return DSColor.gitModified
        case .added:     return DSColor.gitAdded
        case .deleted:   return DSColor.gitDeleted
        case .renamed:   return DSColor.gitRenamed
        case .copied:    return DSColor.gitRenamed
        case .untracked: return DSColor.gitUntracked
        }
    }
}
