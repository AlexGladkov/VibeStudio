// MARK: - DSColor
// Color design tokens for VibeStudio — adaptive for light/dark appearance.
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
func adaptiveColor(dark: String, light: String) -> Color {
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
    /// Disabled text: dimmer than muted, for truly inactive elements.
    /// Use instead of `textMuted.opacity(0.6)`.
    static let textDisabled = adaptiveColor(dark: "#3B3C42", light: "#C7C7CC")
    /// Ghost text: nearly invisible, for breadcrumb separators and micro-dividers.
    /// Use instead of `textMuted.opacity(0.5)`.
    static let textGhost    = adaptiveColor(dark: "#3F4046", light: "#D1D1D6")

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
    /// Subtle accent background: for selected/active state backgrounds.
    /// Use instead of `accentPrimary.opacity(0.15)`.
    static let accentPrimarySubtle = adaptiveColor(dark: "#152740", light: "#EBF2FF")

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

    // MARK: Diff

    /// Background for added lines in diff view.
    static let diffAddedBg   = adaptiveColor(dark: "#143D1F", light: "#DAFBE1")
    /// Background for deleted lines in diff view.
    static let diffDeletedBg = adaptiveColor(dark: "#3D1214", light: "#FFE4E4")
    /// Line number / gutter text in diff view.
    static let diffGutter    = adaptiveColor(dark: "#55565C", light: "#AEAEB2")

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
    /// CodeSpeak (Kotlin orange).
    static let agentCodeSpeak = Color(hex: "#E85D29")

    // MARK: Language Icons

    /// Swift language icon color (official Swift orange).
    static let swiftOrange = Color(hex: "#F05138")

    // MARK: Syntax Highlighting

    /// Heading text (# through ######).
    static let syntaxHeading              = adaptiveColor(dark: "#79C0FF", light: "#0550AE")
    /// Bold text (**bold**).
    static let syntaxBold                 = adaptiveColor(dark: "#E3C04B", light: "#9A6700")
    /// Italic text (*italic*).
    static let syntaxItalic               = adaptiveColor(dark: "#D2A8FF", light: "#8250DF")
    /// Inline code (`code`).
    static let syntaxInlineCode           = adaptiveColor(dark: "#FF7B72", light: "#CF222E")
    /// Code fence delimiter (``` or ~~~).
    static let syntaxCodeFence            = adaptiveColor(dark: "#8B949E", light: "#6E7781")
    /// Code block body text.
    static let syntaxCodeBody             = adaptiveColor(dark: "#C9D1D9", light: "#24292F")
    /// Link text [text].
    static let syntaxLink                 = adaptiveColor(dark: "#58A6FF", light: "#0969DA")
    /// Link URL (url).
    static let syntaxLinkURL              = adaptiveColor(dark: "#8B949E", light: "#6E7781")
    /// Blockquote prefix (>).
    static let syntaxBlockquote           = adaptiveColor(dark: "#8B949E", light: "#6E7781")
    /// List marker (-, *, +, 1.).
    static let syntaxListMarker           = adaptiveColor(dark: "#FF7B72", light: "#CF222E")
    /// YAML frontmatter delimiter (---).
    static let syntaxFrontmatterDelimiter = adaptiveColor(dark: "#BC8CFF", light: "#8250DF")
    /// YAML frontmatter key.
    static let syntaxFrontmatterKey       = adaptiveColor(dark: "#79C0FF", light: "#0550AE")
    /// YAML frontmatter value.
    static let syntaxFrontmatterValue     = adaptiveColor(dark: "#A5D6FF", light: "#0A3069")
    /// CodeSpeak directive (@spec, @assert, etc.).
    static let syntaxCSDirective          = adaptiveColor(dark: "#FFA657", light: "#953800")
    /// CodeSpeak file reference (@file:).
    static let syntaxCSFileRef            = adaptiveColor(dark: "#7EE787", light: "#116329")

    // MARK: Overlay / Interaction

    /// Hover overlay: for generic hover highlight on any surface.
    /// Use instead of `textPrimary.opacity(0.07)`.
    static let hoverOverlay = adaptiveColor(dark: "#FFFFFF12", light: "#00000012")
    /// Drop target highlight background.
    /// Use instead of `accentPrimary.opacity(0.08)`.
    static let dropTargetBg = adaptiveColor(dark: "#4A9EFF14", light: "#0066FF14")
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

// MARK: - Syntax Color Map

extension DSColor {

    /// Maps ``SyntaxTokenKind`` to an adaptive `NSColor` for `NSTextStorage` highlighting.
    ///
    /// Returns `nil` for unknown/plain kinds so the caller can fall back to the
    /// default text color.
    @MainActor
    static func syntaxNSColor(for kind: SyntaxTokenKind) -> NSColor? {
        switch kind {
        case .heading:              return nsAdaptiveColor(dark: "#79C0FF", light: "#0550AE")
        case .bold:                 return nsAdaptiveColor(dark: "#E3C04B", light: "#9A6700")
        case .italic:               return nsAdaptiveColor(dark: "#D2A8FF", light: "#8250DF")
        case .inlineCode:           return nsAdaptiveColor(dark: "#FF7B72", light: "#CF222E")
        case .codeBlockFence:       return nsAdaptiveColor(dark: "#8B949E", light: "#6E7781")
        case .codeBlockBody:        return nsAdaptiveColor(dark: "#C9D1D9", light: "#24292F")
        case .link:                 return nsAdaptiveColor(dark: "#58A6FF", light: "#0969DA")
        case .linkURL:              return nsAdaptiveColor(dark: "#8B949E", light: "#6E7781")
        case .blockquote:           return nsAdaptiveColor(dark: "#8B949E", light: "#6E7781")
        case .listMarker:           return nsAdaptiveColor(dark: "#FF7B72", light: "#CF222E")
        case .frontmatterDelimiter: return nsAdaptiveColor(dark: "#BC8CFF", light: "#8250DF")
        case .frontmatterKey:       return nsAdaptiveColor(dark: "#79C0FF", light: "#0550AE")
        case .frontmatterValue:     return nsAdaptiveColor(dark: "#A5D6FF", light: "#0A3069")
        case .comment:              return nsAdaptiveColor(dark: "#6E7681", light: "#6E7781")
        default:                    return nil
        }
    }

    /// Creates an `NSColor` with a dynamic provider for dark/light appearance.
    private static func nsAdaptiveColor(dark: String, light: String) -> NSColor {
        NSColor(name: nil) { appearance in
            let isDark = appearance.bestMatch(
                from: [.darkAqua, .accessibilityHighContrastDarkAqua]
            ) != nil
            return isDark ? NSColor(hex: dark) : NSColor(hex: light)
        }
    }
}
