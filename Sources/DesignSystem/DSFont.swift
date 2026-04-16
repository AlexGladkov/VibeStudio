// MARK: - DSFont
// Typography design tokens for VibeStudio.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

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

    /// Monospaced `NSFont` for the syntax-highlighted code editor.
    ///
    /// Uses the system monospaced font (SF Mono on macOS). Suitable for
    /// `NSTextStorage` attribute application in `SyntaxHighlightTextStorage`.
    ///
    /// - Parameter size: Font size in points (default 13).
    /// - Returns: Monospaced `NSFont` instance.
    static func codeEditorNSFont(size: CGFloat = 13) -> NSFont {
        NSFont.monospacedSystemFont(ofSize: size, weight: .regular)
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

    // MARK: Settings / Sheets

    /// Settings pane title: 15pt Semibold.
    static let settingsTitle = Font.system(size: 15, weight: .semibold)
    /// Sheet header title: 15pt Semibold.
    static let sheetTitle = Font.system(size: 15, weight: .semibold)
    /// Sheet sub-header: 14pt Semibold (e.g. SpecEditorSheet).
    static let sheetSubtitle = Font.system(size: 14, weight: .semibold)
    /// Small action button label: 11pt Medium.
    static let smallButtonLabel = Font.system(size: 11, weight: .medium)
    /// Monospaced path display: 12pt Regular Monospaced.
    static let monoPath = Font.system(size: 12, weight: .regular, design: .monospaced)
    /// Small monospaced: 11pt Regular Monospaced (inline code hints, version labels).
    static let monoSmall = Font.system(size: 11, design: .monospaced)
    /// Body small: 12pt Regular (info text, descriptions without emphasis).
    static let bodySmall = Font.system(size: 12)
    /// Body medium: 14pt Regular (editor section labels, secondary headers).
    static let bodyMedium = Font.system(size: 14)
    /// Icon medium medium-weight: 10pt Medium (action icon labels).
    static let iconMDMedium = Font.system(size: 10, weight: .medium)

    // MARK: Badges

    /// Status badge text: 10pt Semibold (PASS/FAIL/STAGED).
    static let statusBadge = Font.system(size: 10, weight: .semibold)
    /// Small badge text: 9pt Semibold (compact status indicators).
    static let badgeSmall = Font.system(size: 9, weight: .semibold)

    // MARK: Icons

    /// Extra-small icon: 8pt (arrow up/down in ahead/behind badges).
    static let iconXS = Font.system(size: 8)
    /// Small icon: 9pt (chevron, branch indicators, char count).
    static let iconSM = Font.system(size: 9)
    /// Medium icon: 10pt (remote branch icon, tab indicators).
    static let iconMD = Font.system(size: 10)
    /// Base icon: 11pt (gear, refresh, small action icons). Alias for sidebarItemSmall.
    static let iconBase = Font.system(size: 11)
    /// Large icon: 14pt (folder, file tree icons).
    static let iconLG = Font.system(size: 14)

    // MARK: Welcome / Empty States

    /// Welcome screen hero icon: 48pt.
    static let welcomeIcon = Font.system(size: 48)
    /// Welcome screen title: 24pt Semibold.
    static let welcomeTitle = Font.system(size: 24, weight: .semibold)
    /// Empty state placeholder icon: 24pt.
    static let emptyStateIcon = Font.system(size: 24)
    /// Large empty state icon: 32pt.
    static let emptyStateIconLarge = Font.system(size: 32)
}
