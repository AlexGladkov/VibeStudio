// MARK: - OpencodeSettingsPane
// Settings pane for the opencode AI coding assistant.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - OpencodePluginEntry

/// Represents a single TypeScript plugin file in ~/.config/opencode/plugins/.
private struct OpencodePluginEntry: Identifiable {
    let id: String
    let fileURL: URL
    let filename: String

    var displayName: String {
        fileURL.deletingPathExtension().lastPathComponent
    }
}

// MARK: - OpencodeSettingsPane

/// Settings pane for opencode.
///
/// Shows the config directory, lists TypeScript plugin files from
/// `~/.config/opencode/plugins/`, and provides create / edit / delete actions.
struct OpencodeSettingsPane: View {

    // MARK: State

    @State private var plugins: [OpencodePluginEntry] = []
    @State private var editingPlugin: OpencodePluginEntry?
    @State private var showNewPlugin = false
    @State private var pluginToDelete: OpencodePluginEntry?
    @State private var showDeleteAlert = false

    // MARK: Constants

    private static let configDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".config/opencode")

    private static let pluginsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".config/opencode/plugins")

    private var displayConfigPath: String {
        Self.configDirectoryURL.tildeAbbreviatedPath
    }

    // MARK: - Body

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("OpenCode")
                    .font(DSFont.settingsTitle)
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                configDirectoryRow

                pluginsSection

                providersInfoRow
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear(perform: loadPlugins)
        .sheet(item: $editingPlugin) { plugin in
            TextFileEditorSheet(
                fileURL: plugin.fileURL,
                displayTitle: plugin.filename
            ) { loadPlugins() }
        }
        .sheet(isPresented: $showNewPlugin) {
            newPluginSheet
        }
        .alert("Удалить плагин?", isPresented: $showDeleteAlert, presenting: pluginToDelete) { plugin in
            Button("Удалить", role: .destructive) { deletePlugin(plugin) }
            Button("Отмена", role: .cancel) {}
        } message: { plugin in
            Text("Файл «\(plugin.filename)» будет удалён без возможности восстановления.")
        }
    }

    // MARK: - Config Directory Row

    private var configDirectoryRow: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Директория конфига")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Text(displayConfigPath)
                    .font(DSFont.monoPath)
                    .foregroundStyle(DSColor.textPrimary)
                    .lineLimit(1)
                    .truncationMode(.middle)

                Spacer()

                Button {
                    NSWorkspace.shared.open(Self.configDirectoryURL)
                } label: {
                    Label("Finder", systemImage: "folder")
                        .font(DSFont.smallButtonLabel)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
            .padding(DSSpacing.md)
            .settingsCard()
        }
    }

    // MARK: - Plugins Section

    private var pluginsSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            pluginsSectionHeader

            if plugins.isEmpty {
                emptyPluginsState
            } else {
                ScrollView(.vertical, showsIndicators: true) {
                    VStack(spacing: 0) {
                        ForEach(plugins) { plugin in
                            pluginRow(plugin)
                            if plugin.id != plugins.last?.id {
                                Divider()
                                    .background(DSColor.borderSubtle)
                                    .padding(.horizontal, DSSpacing.md)
                            }
                        }
                    }
                }
                .frame(maxHeight: DSLayout.settingsListMaxHeightLarge)
                .settingsCard()
            }
        }
    }

    private var pluginsSectionHeader: some View {
        SettingsSectionHeader(title: "Плагины", showAddButton: true) {
            showNewPlugin = true
        }
    }

    private var emptyPluginsState: some View {
        SettingsEmptyState(text: "Нет плагинов")
    }

    private func pluginRow(_ plugin: OpencodePluginEntry) -> some View {
        SettingsItemRow(
            name: plugin.displayName,
            subtitle: plugin.filename,
            showDelete: true,
            onEdit: { editingPlugin = plugin },
            onDelete: {
                pluginToDelete = plugin
                showDeleteAlert = true
            }
        )
    }

    // MARK: - New Plugin Sheet

    @State private var newPluginName: String = ""

    private var newPluginSheet: some View {
        let newFileURL: URL = {
            let name = newPluginName.trimmingCharacters(in: .whitespaces)
            let filename = name.isEmpty ? "my-plugin" : name
            return Self.pluginsDirectoryURL.appendingPathComponent("\(filename).ts")
        }()

        return TextFileEditorSheet(
            fileURL: newFileURL,
            displayTitle: newFileURL.lastPathComponent,
            defaultContent: newPluginTemplate
        ) {
            newPluginName = ""
            loadPlugins()
        }
    }

    private let newPluginTemplate = """
        import { definePlugin } from "@opencode-ai/plugin"

        export default definePlugin({
          name: "my-plugin",
          init(app) {
            // Plugin initialization
          }
        })
        """

    // MARK: - Providers Info Row

    private var providersInfoRow: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Авторизация")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Image(systemName: "info.circle")
                    .font(DSFont.bodySmall)
                    .foregroundStyle(DSColor.textMuted)

                Text("Провайдеры и API-ключи управляются через CLI: ")
                    .font(DSFont.bodySmall)
                    .foregroundStyle(DSColor.textMuted)

                Text("opencode providers")
                    .font(DSFont.monoSmall)
                    .foregroundStyle(DSColor.textSecondary)

                Spacer()
            }
            .padding(DSSpacing.md)
            .settingsCard()
        }
    }

    // MARK: - Data Loading

    private func loadPlugins() {
        let fm = FileManager.default
        let dir = Self.pluginsDirectoryURL
        guard let contents = try? fm.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.nameKey],
            options: [.skipsHiddenFiles]
        ) else {
            plugins = []
            return
        }

        plugins = contents
            .filter { $0.pathExtension == "ts" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { url in
                OpencodePluginEntry(
                    id: url.path,
                    fileURL: url,
                    filename: url.lastPathComponent
                )
            }
    }

    // MARK: - Delete

    private func deletePlugin(_ plugin: OpencodePluginEntry) {
        try? FileManager.default.removeItem(at: plugin.fileURL)
        loadPlugins()
    }
}
