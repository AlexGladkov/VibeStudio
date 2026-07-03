// MARK: - GeminiSettingsPane
// Settings pane for the Google Gemini CLI AI coding assistant.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - GeminiSettingsPane

/// Settings pane for Google Gemini CLI.
///
/// Shows `~/.gemini/settings.json` with Finder + editor actions.
/// When the file does not exist yet a "Create config" button is offered.
struct GeminiSettingsPane: View {

    // MARK: ViewModel (lazy init)

    @State private var vmBox = LazyStateObject<GeminiSettingsPaneViewModel>()
    private var viewModel: GeminiSettingsPaneViewModel {
        vmBox.resolve { GeminiSettingsPaneViewModel() }
    }

    // MARK: State

    @State private var showEditor = false

    // MARK: - Body

    var body: some View {
        let model = viewModel
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("Gemini")
                    .font(DSFont.settingsTitle)
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                settingsFileSection(model: model)

                authInfoRow
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear { viewModel.checkConfigExists() }
        .sheet(isPresented: $showEditor, onDismiss: { viewModel.checkConfigExists() }, content: {
            TextFileEditorSheet(
                fileURL: GeminiSettingsPaneViewModel.settingsURL,
                displayTitle: "settings.json",
                defaultContent: defaultSettings
            )
        })
    }

    // MARK: - Settings File Section

    private func settingsFileSection(model: GeminiSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Config")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            SettingsConfigFileRow(
                displayPath: model.displayPath,
                configExists: model.configExists,
                onReveal: {
                    NSWorkspace.shared.activateFileViewerSelecting([GeminiSettingsPaneViewModel.settingsURL])
                },
                onEdit: { showEditor = true }
            )

            if model.configExists {
                settingsReferenceRow
            }
        }
    }

    // MARK: - Settings Reference

    private var settingsReferenceRow: some View {
        VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Text("Available settings: theme, sandbox, checkpointing, preferredEditor,")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
            Text("contextWindowCompression, telemetry, coreTools, mcpServers, extensions.")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
        }
    }

    // MARK: - Auth Info Row

    private var authInfoRow: some View {
        SettingsAuthInfoRow(
            hint: "Set the API key via an environment variable:",
            envVar: "export GEMINI_API_KEY=your-key"
        )
    }

    // MARK: - Default Settings

    private let defaultSettings = """
        {
          "theme": "Default",
          "checkpointing": false,
          "contextWindowCompression": {
            "enabled": true,
            "threshold": 0.7
          },
          "telemetry": false
        }
        """
}
