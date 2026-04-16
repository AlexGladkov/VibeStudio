// MARK: - GeminiSettingsPane
// Settings pane for the Google Gemini CLI AI coding assistant.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - GeminiSettingsPane

/// Settings pane for Google Gemini CLI.
///
/// Shows `~/.gemini/settings.json` with Finder + editor actions.
/// When the file does not exist yet a "Создать конфиг" button is offered.
struct GeminiSettingsPane: View {

    // MARK: State

    @State private var showEditor = false
    @State private var configExists = false

    // MARK: Constants

    private static let settingsURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".gemini/settings.json")

    private var displayPath: String {
        Self.settingsURL.tildeAbbreviatedPath
    }

    // MARK: - Body

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("Gemini")
                    .font(DSFont.settingsTitle)
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                settingsFileSection

                authInfoRow
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear(perform: checkConfigExists)
        .sheet(isPresented: $showEditor, onDismiss: checkConfigExists) {
            TextFileEditorSheet(
                fileURL: Self.settingsURL,
                displayTitle: "settings.json",
                defaultContent: defaultSettings
            )
        }
    }

    // MARK: - Settings File Section

    private var settingsFileSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Конфиг")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Text(displayPath)
                    .font(DSFont.monoPath)
                    .foregroundStyle(configExists ? DSColor.textPrimary : DSColor.textMuted)
                    .lineLimit(1)
                    .truncationMode(.middle)

                if !configExists {
                    Text("не найден")
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)
                }

                Spacer()

                if configExists {
                    Button {
                        NSWorkspace.shared.activateFileViewerSelecting([Self.settingsURL])
                    } label: {
                        Label("Finder", systemImage: "folder")
                            .font(DSFont.smallButtonLabel)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                }

                Button {
                    showEditor = true
                } label: {
                    Label(
                        configExists ? "Редактировать" : "Создать конфиг",
                        systemImage: configExists ? "pencil" : "plus"
                    )
                    .font(DSFont.smallButtonLabel)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
            }
            .padding(DSSpacing.md)
            .settingsCard()

            if configExists {
                settingsReferenceRow
            }
        }
    }

    // MARK: - Settings Reference

    private var settingsReferenceRow: some View {
        VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Text("Доступные настройки: theme, sandbox, checkpointing, preferredEditor,")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
            Text("contextWindowCompression, telemetry, coreTools, mcpServers, extensions.")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
        }
    }

    // MARK: - Auth Info Row

    private var authInfoRow: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Авторизация")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Image(systemName: "info.circle")
                    .font(DSFont.bodySmall)
                    .foregroundStyle(DSColor.textMuted)

                VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                    Text("Установите API-ключ через переменную окружения:")
                        .font(DSFont.bodySmall)
                        .foregroundStyle(DSColor.textMuted)

                    Text("export GEMINI_API_KEY=your-key")
                        .font(DSFont.monoSmall)
                        .foregroundStyle(DSColor.textSecondary)
                }

                Spacer()
            }
            .padding(DSSpacing.md)
            .settingsCard()
        }
    }

    // MARK: - Helpers

    private func checkConfigExists() {
        configExists = FileManager.default.fileExists(atPath: Self.settingsURL.path)
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
