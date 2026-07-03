// MARK: - PlayStopButton
// Unified play/stop button for the Regular and CodeSpeak toolbars.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - PlayStopButton

/// Toolbar play/stop button shared by the Regular and CodeSpeak modes.
///
/// Visual rules (preserved 1:1 from the prior inline implementations — do
/// not change without a design review):
/// - Icon: `stop.fill` while running, `play.fill` otherwise.
/// - Colour: `DSColor.actionStop` while running, `DSColor.actionRun` when
///   `canRun == true`, `DSColor.textMuted` otherwise.
/// - Disabled when not running and `canRun == false`.
/// - Hit area: matches `DSLayout.toolbarButtonHeight`. Width depends on the
///   `size` variant (Regular: `toolbarIconButtonWidth = 26`,
///   Compact/CodeSpeak: `toolbarButtonHeight = 22`).
///
/// `.help` strings differ between callers, so they are accepted as
/// parameters with the previous defaults baked in.
struct PlayStopButton: View {

    // MARK: - Size

    /// Visual variant of the button.
    enum Size {
        /// Regular-mode toolbar: 26×22 with `DSFont.playStopButton` (13pt bold).
        case regular
        /// CodeSpeak run-bar: 22×22 with `DSFont.csPlayStopButton` (11pt bold).
        case compact

        var font: Font {
            switch self {
            case .regular: return DSFont.playStopButton
            case .compact: return DSFont.csPlayStopButton
            }
        }

        var frameWidth: CGFloat {
            switch self {
            case .regular: return DSLayout.toolbarIconButtonWidth
            case .compact: return DSLayout.toolbarButtonHeight
            }
        }
    }

    // MARK: Properties

    /// `true` while the underlying process is running.
    let isRunning: Bool
    /// `true` when launching is currently permitted (drives the disabled
    /// state and muted colour for the play glyph).
    let canRun: Bool
    /// Size variant — controls font and frame width.
    let size: Size
    /// Tooltip text shown while running.
    let runningHelp: String
    /// Tooltip text shown while idle.
    let idleHelp: String
    /// Tap action.
    let action: () -> Void

    // MARK: Init

    init(
        isRunning: Bool,
        canRun: Bool,
        size: Size = .regular,
        runningHelp: String = "Stop",
        idleHelp: String = "Run",
        action: @escaping () -> Void
    ) {
        self.isRunning = isRunning
        self.canRun = canRun
        self.size = size
        self.runningHelp = runningHelp
        self.idleHelp = idleHelp
        self.action = action
    }

    // MARK: - Body

    var body: some View {
        Button(action: action) {
            Image(systemName: isRunning ? "stop.fill" : "play.fill")
                .font(size.font)
                .foregroundStyle(
                    isRunning
                        ? DSColor.actionStop
                        : (canRun ? DSColor.actionRun : DSColor.textMuted)
                )
                .frame(width: size.frameWidth, height: DSLayout.toolbarButtonHeight)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isRunning && !canRun)
        .accessibilityLabel(isRunning ? "Stop" : "Run")
        .help(isRunning ? runningHelp : idleHelp)
    }
}
