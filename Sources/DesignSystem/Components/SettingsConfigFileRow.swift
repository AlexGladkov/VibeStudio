// MARK: - SettingsConfigFileRow
// Path + "Finder" + "Edit/Create" action row used by every AI-assistant
// settings pane (Claude / Codex / Qwen / Gemini).
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SettingsConfigFileRow

/// A row that shows a monospaced path plus the two standard config-file
/// actions: reveal in Finder and edit (or create when the file does not yet
/// exist).
///
/// Visuals are identical to the previous inline `fileRow` blocks in
/// `ClaudeSettingsPane`, `CodexSettingsPane`, `QwenSettingsPane` and
/// `GeminiSettingsPane`:
/// - path: `DSFont.monoPath`, `DSColor.textPrimary` (or `.textMuted` when
///   the file is missing).
/// - "not found" label appears when `configExists == false` (matches the
///   Qwen / Gemini variant).
/// - Finder button: `Label("Finder", systemImage: "folder")`, `.bordered`,
///   `controlSize(.small)`. Hidden when the file does not exist (matches
///   Qwen / Gemini behaviour); pass `alwaysShowReveal = true` to always
///   render it (Claude / Codex always show it because their config dir
///   always exists).
/// - Edit/Create button: `Label("Edit"|"Create config", …)`,
///   `.borderedProminent`, `controlSize(.small)`.
///
/// Caller wraps the row in its own outer section header (e.g.
/// `Text("Global config")`) — this component only renders the action
/// row inside a `.settingsCard()`.
struct SettingsConfigFileRow: View {

    // MARK: Properties

    /// Path shown in the row (typically `URL.tildeAbbreviatedPath`).
    let displayPath: String

    /// Whether the underlying file currently exists on disk.
    /// Controls the muted style, "not found" label, Finder visibility and
    /// the Edit-vs-Create button text/icon.
    let configExists: Bool

    /// Localised label for the edit action (default: "Edit").
    let editLabel: String

    /// Localised label for the create action (default: "Create config").
    let createLabel: String

    /// When `true`, the Finder button is rendered regardless of
    /// `configExists` (used by Claude / Codex which always have the
    /// directory in place). Default `false` — matches Qwen / Gemini.
    let alwaysShowReveal: Bool

    /// Action invoked when the Finder button is tapped.
    let onReveal: () -> Void

    /// Action invoked when the Edit/Create button is tapped.
    let onEdit: () -> Void

    // MARK: Init

    init(
        displayPath: String,
        configExists: Bool,
        editLabel: String = "Edit",
        createLabel: String = "Create config",
        alwaysShowReveal: Bool = false,
        onReveal: @escaping () -> Void,
        onEdit: @escaping () -> Void
    ) {
        self.displayPath = displayPath
        self.configExists = configExists
        self.editLabel = editLabel
        self.createLabel = createLabel
        self.alwaysShowReveal = alwaysShowReveal
        self.onReveal = onReveal
        self.onEdit = onEdit
    }

    // MARK: - Body

    var body: some View {
        HStack(spacing: DSSpacing.sm) {
            Text(displayPath)
                .font(DSFont.monoPath)
                .foregroundStyle(configExists ? DSColor.textPrimary : DSColor.textMuted)
                .lineLimit(1)
                .truncationMode(.middle)

            if !configExists {
                Text("not found")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
            }

            Spacer()

            if alwaysShowReveal || configExists {
                Button(action: onReveal) {
                    Label("Finder", systemImage: "folder")
                        .font(DSFont.smallButtonLabel)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }

            Button(action: onEdit) {
                Label(
                    configExists ? editLabel : createLabel,
                    systemImage: configExists ? "pencil" : "plus"
                )
                .font(DSFont.smallButtonLabel)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)
        }
        .padding(DSSpacing.md)
        .settingsCard()
    }
}
