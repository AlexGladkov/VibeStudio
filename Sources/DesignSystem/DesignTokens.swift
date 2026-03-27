// MARK: - VibeStudio Design Tokens
// Single source of truth for all visual tokens.
// See .specs/design-system.md for rationale and specifications.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - Hex Color Parsing

/// Shared hex color parsing logic for `Color` and `NSColor` initializers.
///
/// Supports 6-digit (`RRGGBB`) and 8-digit (`AARRGGBB`) formats.
/// Returns `nil` for unsupported formats.
private func parseHexComponents(_ hex: String) -> (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat)? {
    let cleaned = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
    var int: UInt64 = 0
    Scanner(string: cleaned).scanHexInt64(&int)

    switch cleaned.count {
    case 6:
        return (
            r: CGFloat((int >> 16) & 0xFF) / 255,
            g: CGFloat((int >> 8) & 0xFF) / 255,
            b: CGFloat(int & 0xFF) / 255,
            a: 1.0
        )
    case 8:
        return (
            r: CGFloat((int >> 16) & 0xFF) / 255,
            g: CGFloat((int >> 8) & 0xFF) / 255,
            b: CGFloat(int & 0xFF) / 255,
            a: CGFloat((int >> 24) & 0xFF) / 255
        )
    default:
        return nil
    }
}

// MARK: - Color Extension (Hex)

extension Color {
    /// Initialize a SwiftUI `Color` from a hex string.
    ///
    /// Supports 6-digit (`#RRGGBB`) and 8-digit (`#AARRGGBB`) formats.
    /// The leading `#` is optional.
    ///
    /// - Parameter hex: Hex color string.
    init(hex: String) {
        if let c = parseHexComponents(hex) {
            self.init(.sRGB, red: Double(c.r), green: Double(c.g), blue: Double(c.b), opacity: Double(c.a))
        } else {
            self.init(.sRGB, red: 0, green: 0, blue: 0, opacity: 1)
        }
    }
}

extension NSColor {
    /// Initialize an `NSColor` from a hex string.
    ///
    /// Supports 6-digit (`#RRGGBB`) and 8-digit (`#AARRGGBB`) formats.
    /// The leading `#` is optional.
    ///
    /// - Parameter hex: Hex color string.
    convenience init(hex: String) {
        if let c = parseHexComponents(hex) {
            self.init(srgbRed: c.r, green: c.g, blue: c.b, alpha: c.a)
        } else {
            self.init(srgbRed: 0, green: 0, blue: 0, alpha: 1)
        }
    }
}

// MARK: - Adaptive Color Helper

/// Creates a SwiftUI `Color` that automatically switches between dark and light
/// hex values based on the current `NSAppearance` at render time.
///
/// Uses `NSColor(name:dynamicProvider:)` which is evaluated lazily on each
/// draw pass — meaning the color responds to live `NSApp.appearance` changes
/// without requiring a view redraw trigger.
///
/// - Parameters:
///   - dark:  Hex string for dark appearance (e.g. `"#1A1B1E"`).
///   - light: Hex string for light appearance (e.g. `"#FFFFFF"`).
/// - Returns: Adaptive SwiftUI `Color`.
private func adaptiveColor(dark: String, light: String) -> Color {
    Color(NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(
            from: [.darkAqua, .accessibilityHighContrastDarkAqua]
        ) != nil
        return isDark ? NSColor(hex: dark) : NSColor(hex: light)
    })
}

// MARK: - Color Tokens

/// All color tokens for VibeStudio — adaptive for light/dark appearance.
///
/// Naming follows the pattern `category` + `variant`:
/// surfaces, text, borders, accent, git statuses, indicators, buttons.
///
/// Every token is backed by `NSColor(name:dynamicProvider:)` so it
/// automatically switches when `NSApp.appearance` changes without
/// requiring SwiftUI to redraw views.
enum DSColor {

    // MARK: Surfaces

    /// Terminal area background.
    static let surfaceBase      = adaptiveColor(dark: "#1A1B1E", light: "#FFFFFF")
    /// Sidebar background.
    static let surfaceRaised    = adaptiveColor(dark: "#212225", light: "#F5F5F7")
    /// Dropdown, popover, context menu background.
    static let surfaceOverlay   = adaptiveColor(dark: "#2A2B2F", light: "#EBEBED")
    /// Tab bar background (darker than base).
    static let surfaceTabBar    = adaptiveColor(dark: "#17181B", light: "#EBEBED")
    /// Active tab background (matches terminal area).
    static let surfaceTabActive = adaptiveColor(dark: "#1A1B1E", light: "#FFFFFF")
    /// Inactive tab background (matches tab bar).
    static let surfaceTabInactive = adaptiveColor(dark: "#17181B", light: "#EBEBED")
    /// Hover state for inactive tab.
    static let surfaceTabHover  = adaptiveColor(dark: "#1F2023", light: "#E0E0E5")
    /// Text field background (commit message, search).
    static let surfaceInput     = adaptiveColor(dark: "#16171A", light: "#F0F0F2")
    /// Text selection highlight in terminal.
    static let surfaceSelection = adaptiveColor(dark: "#264F78", light: "#BDD5FB")
    /// Quick-action toolbar background (slightly darker than tab bar).
    static let surfaceToolbar   = adaptiveColor(dark: "#1C1C1E", light: "#F5F5F7")

    // MARK: Text

    /// Primary text: filenames, terminal content.
    static let textPrimary   = adaptiveColor(dark: "#D4D4D8", light: "#1D1D1F")
    /// Secondary text: labels, paths, timestamps.
    static let textSecondary = adaptiveColor(dark: "#8B8B93", light: "#6E6E73")
    /// Muted text: placeholders, disabled elements.
    static let textMuted     = adaptiveColor(dark: "#55565C", light: "#AEAEB2")
    /// Inverse text: on bright backgrounds (badges).
    static let textInverse   = adaptiveColor(dark: "#1A1B1E", light: "#FFFFFF")

    // MARK: Borders

    /// Default border: sidebar/terminal divider, split divider.
    static let borderDefault = adaptiveColor(dark: "#2E2F33", light: "#D1D1D6")
    /// Subtle border: section separators inside sidebar.
    static let borderSubtle  = adaptiveColor(dark: "#252629", light: "#E5E5EA")
    /// Focus ring for keyboard navigation.
    static let borderFocus   = adaptiveColor(dark: "#4A9EFF", light: "#0066FF")

    // MARK: Accent

    /// Primary accent: active tab indicator, selected items.
    static let accentPrimary      = adaptiveColor(dark: "#4A9EFF", light: "#0066FF")
    /// Hover state for primary accent.
    static let accentPrimaryHover = adaptiveColor(dark: "#5BABFF", light: "#0055EE")
    /// Secondary accent (reserved for future use).
    static let accentSecondary    = adaptiveColor(dark: "#7C3AED", light: "#7C3AED")

    // MARK: Git Statuses

    /// Modified files (M).
    static let gitModified   = adaptiveColor(dark: "#E2B93D", light: "#B59400")
    /// Added files (A).
    static let gitAdded      = adaptiveColor(dark: "#3FB950", light: "#28843B")
    /// Deleted files (D).
    static let gitDeleted    = adaptiveColor(dark: "#F85149", light: "#C42B2B")
    /// Untracked files (?).
    static let gitUntracked  = adaptiveColor(dark: "#8B8B93", light: "#6E6E73")
    /// Conflicted files (U).
    static let gitConflicted = adaptiveColor(dark: "#F09000", light: "#C06000")
    /// Renamed files (R).
    static let gitRenamed    = adaptiveColor(dark: "#58A6FF", light: "#1E64C8")

    // MARK: Activity Indicators

    /// Tab is open but nothing has happened (or user already checked it).
    static let indicatorIdle    = adaptiveColor(dark: "#6E7681", light: "#8E8E93")
    /// Output is actively flowing right now.
    static let indicatorRunning = adaptiveColor(dark: "#3FB950", light: "#28843B")
    /// Output appeared since user last looked — waiting for reaction.
    static let indicatorWaiting = adaptiveColor(dark: "#E2B93D", light: "#B59400")
    /// Process exited with non-zero code.
    static let indicatorError   = adaptiveColor(dark: "#F85149", light: "#C42B2B")

    // MARK: Toolbar Actions

    /// Toolbar picker/control background.
    static let toolbarControlBackground = adaptiveColor(dark: "#252629", light: "#E5E5EA")
    /// Toolbar picker/control border.
    static let toolbarControlBorder     = adaptiveColor(dark: "#3C3F41", light: "#C7C7CC")
    /// Stop action (terminate process).
    static let actionStop = adaptiveColor(dark: "#F85149", light: "#C42B2B")
    /// Run/play action (start process).
    static let actionRun  = adaptiveColor(dark: "#3FB950", light: "#28843B")

    // MARK: Buttons

    /// Primary button background (Commit, Push).
    static let buttonPrimaryBg      = adaptiveColor(dark: "#4A9EFF", light: "#0066FF")
    /// Primary button text.
    static let buttonPrimaryText    = Color.white
    /// Primary button hover background.
    static let buttonPrimaryHoverBg = adaptiveColor(dark: "#5BABFF", light: "#0055EE")
    /// Secondary button background (Stage All, Pull).
    static let buttonSecondaryBg      = adaptiveColor(dark: "#2A2B2F", light: "#EBEBED")
    /// Secondary button text.
    static let buttonSecondaryText    = adaptiveColor(dark: "#D4D4D8", light: "#1D1D1F")
    /// Secondary button hover background.
    static let buttonSecondaryHoverBg = adaptiveColor(dark: "#333438", light: "#E0E0E5")
    /// Danger button background (Discard Changes).
    static let buttonDangerBg      = adaptiveColor(dark: "#3D1214", light: "#FFE4E4")
    /// Danger button text.
    static let buttonDangerText    = adaptiveColor(dark: "#F85149", light: "#C42B2B")
    /// Danger button hover background.
    static let buttonDangerHoverBg = adaptiveColor(dark: "#4D1719", light: "#FFD0D0")

    // MARK: Agent Brand Colors (same in both themes)

    /// Claude (Anthropic copper).
    static let agentClaude    = Color(hex: "#CC7847")
    /// OpenCode (blue-violet).
    static let agentOpenCode  = Color(hex: "#6189F2")
    /// Codex (OpenAI green).
    static let agentCodex     = Color(hex: "#10A37F")
    /// Gemini (Google blue).
    static let agentGemini    = Color(hex: "#4285F4")
    /// Qwen Code (purple).
    static let agentQwen      = Color(hex: "#6B3FA0")

    // MARK: Language Icons

    /// Swift language icon color (official Swift orange).
    static let swiftOrange = Color(hex: "#F05138")
}

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

// MARK: - Typography

/// Font tokens for UI and terminal text.
///
/// UI text uses SF Pro (system font). Terminal uses JetBrains Mono
/// with SF Mono as fallback. Git status literals use SF Mono.
enum DSFont {
    /// Project name on tab: 12pt Medium.
    static let tabTitle = Font.system(size: 12, weight: .medium)
    /// Branch name on tab: 10pt Regular.
    static let tabBranch = Font.system(size: 10)
    /// Section header in sidebar (FILES, GIT): 11pt Semibold.
    static let sidebarSection = Font.system(size: 11, weight: .semibold)
    /// File tree item: 13pt Regular.
    static let sidebarItem = Font.system(size: 13)
    /// Secondary info in sidebar: 11pt Regular.
    static let sidebarItemSmall = Font.system(size: 11)
    /// Git status letter (M/A/D/?): SF Mono 11pt Medium.
    static let gitStatus: Font = {
        if let _ = NSFont(name: "SF Mono", size: 11) {
            return Font.custom("SF Mono", size: 11).weight(.medium)
        }
        return Font.system(size: 11, weight: .medium, design: .monospaced)
    }()
    /// Branch name in git section: 13pt Medium.
    static let gitBranch = Font.system(size: 13, weight: .medium)
    /// Ahead/behind count: 11pt Regular.
    static let gitAheadBehind = Font.system(size: 11)
    /// Button label text: 12pt Medium.
    static let buttonLabel = Font.system(size: 12, weight: .medium)
    /// Commit message input: 13pt Regular.
    static let commitInput = Font.system(size: 13)
    /// Tooltip text: 11pt Regular.
    static let tooltip = Font.system(size: 11)

    /// Terminal font with configurable size.
    ///
    /// Tries JetBrains Mono first, falls back to SF Mono, then Menlo.
    ///
    /// - Parameter size: Font size in points (default 13, range 9-24).
    /// - Returns: Appropriate monospaced font.
    static func terminal(size: CGFloat = 13) -> Font {
        if let _ = NSFont(name: "JetBrains Mono", size: size) {
            return Font.custom("JetBrains Mono", size: size)
        }
        if let _ = NSFont(name: "SF Mono", size: size) {
            return Font.custom("SF Mono", size: size)
        }
        return Font.custom("Menlo", size: size)
    }

    /// NSFont variant of the terminal font for SwiftTerm configuration.
    ///
    /// - Parameter size: Font size in points.
    /// - Returns: NSFont instance.
    static func terminalNSFont(size: CGFloat = 13) -> NSFont {
        if let font = NSFont(name: "JetBrains Mono", size: size) {
            return font
        }
        if let font = NSFont(name: "SF Mono", size: size) {
            return font
        }
        return NSFont(name: "Menlo", size: size) ?? NSFont.monospacedSystemFont(ofSize: size, weight: .regular)
    }
}

// MARK: - Layout Constants

/// Fixed layout dimensions for all components.
enum DSLayout {

    // MARK: Toolbar

    /// Quick-action toolbar height above tab bar.
    static let toolbarHeight: CGFloat = 26

    // MARK: Tab Bar

    /// Total tab bar height.
    static let tabBarHeight: CGFloat = 36
    /// Individual tab height.
    static let tabHeight: CGFloat = 28
    /// Minimum tab width.
    static let tabMinWidth: CGFloat = 120
    /// Maximum tab width.
    static let tabMaxWidth: CGFloat = 200
    /// Horizontal padding inside a tab.
    static let tabHorizontalPadding: CGFloat = 12
    /// Gap between tabs.
    static let tabGap: CGFloat = 2
    /// Close button size on tab.
    static let tabCloseSize: CGFloat = 16
    /// Close button icon size.
    static let tabCloseIconSize: CGFloat = 9
    /// Add button size.
    static let tabAddButtonSize: CGFloat = 28

    // MARK: Sidebar

    /// Default sidebar width.
    static let sidebarDefaultWidth: CGFloat = 240
    /// Minimum sidebar width.
    static let sidebarMinWidth: CGFloat = 180
    /// Maximum sidebar width.
    static let sidebarMaxWidth: CGFloat = 400
    /// Horizontal padding inside sidebar.
    static let sidebarHorizontalPadding: CGFloat = 12

    // MARK: File Tree

    /// Row height in file tree.
    static let treeRowHeight: CGFloat = 28
    /// Indent per nesting level.
    static let treeIndent: CGFloat = 16
    /// Base indent for root level.
    static let treeBaseIndent: CGFloat = 4

    // MARK: Git Section

    /// Section header height (GIT, FILES).
    static let gitSectionHeaderHeight: CGFloat = 28
    /// File row height in git panel.
    static let gitFileRowHeight: CGFloat = 28
    /// Button height in git panel.
    static let gitButtonHeight: CGFloat = 28
    /// Minimum commit message input height.
    static let commitInputMinHeight: CGFloat = 60
    /// Maximum commit message input height.
    static let commitInputMaxHeight: CGFloat = 120

    // MARK: Terminal

    /// Padding around terminal content.
    static let terminalPadding = EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8)
    /// Split divider total hit area (1pt line + 4pt each side).
    static let splitDividerHitArea: CGFloat = 9
    /// Minimum panel size when splitting.
    static let splitMinPanelSize: CGFloat = 120

    // MARK: Indicator

    /// Activity indicator dot diameter.
    static let indicatorSize: CGFloat = 6

    // MARK: Window

    /// Minimum window width.
    static let windowMinWidth: CGFloat = 640
    /// Minimum window height.
    static let windowMinHeight: CGFloat = 400
    /// Default window width.
    static let windowDefaultWidth: CGFloat = 1280
    /// Default window height.
    static let windowDefaultHeight: CGFloat = 800
}

// MARK: - Terminal ANSI Palette

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

// MARK: - NSColor Surface Tokens

extension DSColor {
    /// `surfaceBase` as `NSColor` for AppKit layers and window backgrounds.
    ///
    /// Uses a dynamic provider so it adapts to appearance changes.
    static var surfaceBaseNS: NSColor {
        NSColor(name: nil) { appearance in
            let isDark = appearance
                .bestMatch(from: [.darkAqua, .accessibilityHighContrastDarkAqua]) != nil
            return isDark ? NSColor(hex: "#1A1B1E") : NSColor(hex: "#FFFFFF")
        }
    }

    /// Alias for `surfaceBase` — adaptive base surface color.
    static let surfaceDefault = adaptiveColor(dark: "#1A1B1E", light: "#FFFFFF")
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
