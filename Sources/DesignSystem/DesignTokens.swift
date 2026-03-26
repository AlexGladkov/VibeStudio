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

// MARK: - Color Tokens

/// All color tokens for the VibeStudio dark theme.
///
/// Naming follows the pattern `category` + `variant`:
/// surfaces, text, borders, accent, git statuses, indicators, buttons.
enum DSColor {

    // MARK: Surfaces

    /// Terminal area background.
    static let surfaceBase = Color(hex: "#1A1B1E")
    /// Sidebar background.
    static let surfaceRaised = Color(hex: "#212225")
    /// Dropdown, popover, context menu background.
    static let surfaceOverlay = Color(hex: "#2A2B2F")
    /// Tab bar background (darker than base).
    static let surfaceTabBar = Color(hex: "#17181B")
    /// Active tab background (matches terminal area).
    static let surfaceTabActive = Color(hex: "#1A1B1E")
    /// Inactive tab background (matches tab bar).
    static let surfaceTabInactive = Color(hex: "#17181B")
    /// Hover state for inactive tab.
    static let surfaceTabHover = Color(hex: "#1F2023")
    /// Text field background (commit message, search).
    static let surfaceInput = Color(hex: "#16171A")
    /// Text selection highlight in terminal.
    static let surfaceSelection = Color(hex: "#264F78")
    /// Quick-action toolbar background (slightly darker than tab bar).
    static let surfaceToolbar = Color(hex: "#1C1C1E")

    // MARK: Text

    /// Primary text: filenames, terminal content.
    static let textPrimary = Color(hex: "#D4D4D8")
    /// Secondary text: labels, paths, timestamps.
    static let textSecondary = Color(hex: "#8B8B93")
    /// Muted text: placeholders, disabled elements.
    static let textMuted = Color(hex: "#55565C")
    /// Inverse text: on bright backgrounds (badges).
    static let textInverse = Color(hex: "#1A1B1E")

    // MARK: Borders

    /// Default border: sidebar/terminal divider, split divider.
    static let borderDefault = Color(hex: "#2E2F33")
    /// Subtle border: section separators inside sidebar.
    static let borderSubtle = Color(hex: "#252629")
    /// Focus ring for keyboard navigation.
    static let borderFocus = Color(hex: "#4A9EFF")

    // MARK: Accent

    /// Primary accent: active tab indicator, selected items.
    static let accentPrimary = Color(hex: "#4A9EFF")
    /// Hover state for primary accent.
    static let accentPrimaryHover = Color(hex: "#5BABFF")
    /// Secondary accent (reserved for future use).
    static let accentSecondary = Color(hex: "#7C3AED")

    // MARK: Git Statuses

    /// Modified files (M).
    static let gitModified = Color(hex: "#E2B93D")
    /// Added files (A).
    static let gitAdded = Color(hex: "#3FB950")
    /// Deleted files (D).
    static let gitDeleted = Color(hex: "#F85149")
    /// Untracked files (?).
    static let gitUntracked = Color(hex: "#8B8B93")
    /// Conflicted files (U).
    static let gitConflicted = Color(hex: "#F09000")
    /// Renamed files (R).
    static let gitRenamed = Color(hex: "#58A6FF")

    // MARK: Activity Indicators

    /// Tab is open but nothing has happened (or user already checked it).
    static let indicatorIdle = Color(hex: "#6E7681")
    /// Output is actively flowing right now.
    static let indicatorRunning = Color(hex: "#3FB950")
    /// Output appeared since user last looked — waiting for reaction.
    static let indicatorWaiting = Color(hex: "#E2B93D")
    /// Process exited with non-zero code.
    static let indicatorError = Color(hex: "#F85149")

    // MARK: Toolbar Actions

    /// Toolbar picker/control background.
    static let toolbarControlBackground = Color(hex: "#252629")
    /// Toolbar picker/control border.
    static let toolbarControlBorder = Color(hex: "#3C3F41")
    /// Stop action (terminate process).
    static let actionStop = Color(hex: "#F85149")
    /// Run/play action (start process).
    static let actionRun = Color(hex: "#3FB950")

    // MARK: Buttons

    /// Primary button background (Commit, Push).
    static let buttonPrimaryBg = Color(hex: "#4A9EFF")
    /// Primary button text.
    static let buttonPrimaryText = Color.white
    /// Primary button hover background.
    static let buttonPrimaryHoverBg = Color(hex: "#5BABFF")
    /// Secondary button background (Stage All, Pull).
    static let buttonSecondaryBg = Color(hex: "#2A2B2F")
    /// Secondary button text.
    static let buttonSecondaryText = Color(hex: "#D4D4D8")
    /// Secondary button hover background.
    static let buttonSecondaryHoverBg = Color(hex: "#333438")
    /// Danger button background (Discard Changes).
    static let buttonDangerBg = Color(hex: "#3D1214")
    /// Danger button text.
    static let buttonDangerText = Color(hex: "#F85149")
    /// Danger button hover background.
    static let buttonDangerHoverBg = Color(hex: "#4D1719")

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
/// 16 colors: Normal (0-7) + Bright (8-15),
/// plus foreground, background, cursor, and selection colors.
/// Matches the dark theme of VibeStudio design system.
enum DSTerminalColors {
    /// ANSI palette as `NSColor` array for SwiftTerm `installColors`.
    ///
    /// Index 0-7: Normal colors (Black, Red, Green, Yellow, Blue, Magenta, Cyan, White).
    /// Index 8-15: Bright variants.
    static let palette: [NSColor] = [
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

    /// Default terminal foreground color.
    static let foreground = NSColor(hex: "#D4D4D8")
    /// Default terminal background color.
    static let background = NSColor(hex: "#1A1B1E")
    /// Cursor color.
    static let cursor = NSColor(hex: "#D4D4D8")
    /// Selection highlight color.
    static let selection = NSColor(hex: "#264F78")
}

// MARK: - NSColor Surface Tokens

extension DSColor {
    /// `surfaceBase` as `NSColor` for AppKit layers and window backgrounds.
    static let surfaceBaseNS = NSColor(hex: "#1A1B1E")
}

// MARK: - GitFileStatus Color Mapping

extension GitFileStatus {
    /// Design-system color for this git file status.
    var color: Color {
        switch self {
        case .modified: return DSColor.gitModified
        case .added:    return DSColor.gitAdded
        case .deleted:  return DSColor.gitDeleted
        case .renamed:  return DSColor.gitRenamed
        case .copied:   return DSColor.gitRenamed
        case .untracked: return DSColor.gitUntracked
        }
    }
}
