// MARK: - SettingsAuthInfoRow
// Reusable "Authorization" info card used by AI-assistant settings panes that
// authenticate via an environment variable (Qwen / Gemini / ...).
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SettingsAuthInfoRow

/// A titled info card explaining how to authenticate a CLI tool.
///
/// Renders the section header, an `info.circle` glyph, a muted hint line, and a
/// monospaced example inside a `.settingsCard()`. Two layouts are supported:
///
/// - `.envVar` (two-line): the hint sits above a monospaced environment-variable
///   example. Visuals match the previous inline `authInfoRow` blocks in
///   `QwenSettingsPane` and `GeminiSettingsPane`.
/// - `.command` (single-line): the hint and a monospaced CLI command sit on one
///   line. Visuals match the previous `providersInfoRow` in `OpencodeSettingsPane`.
struct SettingsAuthInfoRow: View {

    // MARK: Detail

    /// The monospaced example rendered next to the hint, and its layout.
    enum Detail {
        /// Two-line layout: hint above a monospaced env-var example.
        case envVar(String)
        /// Single-line layout: hint followed inline by a monospaced CLI command.
        case command(String)
    }

    // MARK: Properties

    /// Section header text (default: "Authorization").
    let title: String

    /// Muted explanatory line shown next to / above the monospaced example.
    let hint: String

    /// The monospaced example and its layout.
    let detail: Detail

    // MARK: Init

    /// Two-line variant showing a monospaced environment-variable example.
    init(
        title: String = "Authorization",
        hint: String,
        envVar: String
    ) {
        self.title = title
        self.hint = hint
        self.detail = .envVar(envVar)
    }

    /// Single-line variant showing a monospaced CLI command inline with the hint.
    init(
        title: String = "Authorization",
        hint: String,
        command: String
    ) {
        self.title = title
        self.hint = hint
        self.detail = .command(command)
    }

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text(title)
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Image(systemName: "info.circle")
                    .font(DSFont.bodySmall)
                    .foregroundStyle(DSColor.textMuted)

                switch detail {
                case .envVar(let envVar):
                    VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                        Text(hint)
                            .font(DSFont.bodySmall)
                            .foregroundStyle(DSColor.textMuted)

                        Text(envVar)
                            .font(DSFont.monoSmall)
                            .foregroundStyle(DSColor.textSecondary)
                    }

                case .command(let command):
                    Text(hint)
                        .font(DSFont.bodySmall)
                        .foregroundStyle(DSColor.textMuted)

                    Text(command)
                        .font(DSFont.monoSmall)
                        .foregroundStyle(DSColor.textSecondary)
                }

                Spacer()
            }
            .padding(DSSpacing.md)
            .settingsCard()
        }
    }
}
