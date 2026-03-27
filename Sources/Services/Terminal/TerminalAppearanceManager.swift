// MARK: - TerminalAppearanceManager
// Terminal appearance configuration and theme color management.
// Internal helper for TerminalService -- not exposed outside the Terminal module.
// macOS 14+, Swift 5.10

import AppKit
import OSLog
import SwiftTerm

/// Configures terminal view appearance (fonts, colors, palette) and
/// handles theme-change refresh across all live views.
///
/// Extracted from ``TerminalService`` to isolate the design-tokens /
/// NSColor -> SwiftTerm.Color conversion logic from PTY lifecycle.
@MainActor
struct TerminalAppearanceManager {

    // MARK: - Initial Configuration

    /// Apply VibeStudio design tokens to a terminal view.
    ///
    /// Sets font, foreground/background colors, cursor, selection, palette,
    /// and enables `notifyUpdateChanges` for activity detection.
    func configure(_ view: TaggedTerminalView) {
        view.font = DSFont.terminalNSFont(size: 13)
        view.nativeForegroundColor = DSTerminalColors.foreground
        view.nativeBackgroundColor = DSTerminalColors.background
        view.caretColor = DSTerminalColors.cursor
        view.selectedTextBackgroundColor = DSTerminalColors.selection

        // Enable rangeChanged delegate callbacks for activity detection.
        view.notifyUpdateChanges = true

        view.installColors(convertPalette(DSTerminalColors.palette))
    }

    // MARK: - Theme Refresh

    /// Re-apply theme colors to all provided terminal views.
    ///
    /// - Parameters:
    ///   - views: The views to update.
    ///   - appearance: The new `AppAppearance`. `.system`/`nil` reads
    ///     `NSApp.effectiveAppearance` at call time.
    func refreshColors<C: Collection>(
        for views: C,
        appearance: AppAppearance? = nil
    ) where C.Element == TaggedTerminalView {
        let isDark = resolveIsDark(appearance)

        let fg        = isDark ? DSTerminalColors.darkForeground  : DSTerminalColors.lightForeground
        let bg        = isDark ? DSTerminalColors.darkBackground  : DSTerminalColors.lightBackground
        let cursorClr = isDark ? DSTerminalColors.darkCursor      : DSTerminalColors.lightCursor
        let selection = isDark ? DSTerminalColors.darkSelection   : DSTerminalColors.lightSelection
        let palette   = isDark ? DSTerminalColors.darkPalette     : DSTerminalColors.lightPalette
        let converted = convertPalette(palette)

        for view in views {
            // Skip views not attached to a window -- they may be mid-dismantlement.
            guard view.window != nil else { continue }
            view.nativeForegroundColor = fg
            view.nativeBackgroundColor = bg
            view.caretColor = cursorClr
            view.selectedTextBackgroundColor = selection
            view.installColors(converted)
            view.setNeedsDisplay(view.bounds)
        }
    }

    // MARK: - Environment

    /// Build environment variables for a shell subprocess.
    ///
    /// Sets TERM, COLORTERM, LANG and strips Claude Code session variables.
    func buildShellEnvironment() -> [String] {
        var env = ProcessInfo.processInfo.environment
        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["LANG"] = env["LANG"] ?? "en_US.UTF-8"

        // Strip Claude Code session vars so nested invocations don't fail.
        let keysToStrip = env.keys.filter { key in
            key.hasPrefix("CLAUDE_") || key == "CLAUDECODE" ||
            key == "ANTHROPIC_API_KEY" || key == "ANTHROPIC_API_KEY_HELPER"
        }
        keysToStrip.forEach { env.removeValue(forKey: $0) }

        return env.map { "\($0.key)=\($0.value)" }
    }

    // MARK: - Shell Validation

    /// Validate that the shell path is listed in `/etc/shells`.
    ///
    /// Falls back to allowing only `/bin/zsh` if `/etc/shells` is unreadable.
    static func isValidShell(_ path: String) -> Bool {
        guard let shellsFile = try? String(contentsOfFile: "/etc/shells", encoding: .utf8) else {
            return path == "/bin/zsh"
        }
        return shellsFile.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.hasPrefix("#") && !$0.isEmpty }
            .contains(path)
    }

    // MARK: - Private

    /// Convert an array of NSColors to SwiftTerm.Color palette.
    private func convertPalette(_ nsColors: [NSColor]) -> [SwiftTerm.Color] {
        nsColors.map { nsColor -> SwiftTerm.Color in
            let c = nsColor.usingColorSpace(.sRGB) ?? nsColor
            return SwiftTerm.Color(
                red:   UInt16(c.redComponent   * 65535),
                green: UInt16(c.greenComponent * 65535),
                blue:  UInt16(c.blueComponent  * 65535)
            )
        }
    }

    /// Determine whether dark mode is active for the given appearance.
    private func resolveIsDark(_ appearance: AppAppearance?) -> Bool {
        switch appearance {
        case .dark:
            return true
        case .light:
            return false
        case .system, nil:
            return NSApp.effectiveAppearance
                .bestMatch(from: [.darkAqua, .accessibilityHighContrastDarkAqua]) != nil
        }
    }
}
