// MARK: - OpencodeSettingsPane
// Settings pane for the opencode AI coding assistant.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - OpencodeSettingsPane

/// Settings pane for opencode.
///
/// Shows the config directory, lists TypeScript plugin files from
/// `~/.config/opencode/plugins/`, and provides create / edit / delete actions.
struct OpencodeSettingsPane: View {

    // MARK: ViewModel (lazy init)

    @State private var vmBox = LazyStateObject<OpencodeSettingsPaneViewModel>()
    private var viewModel: OpencodeSettingsPaneViewModel {
        vmBox.resolve { OpencodeSettingsPaneViewModel() }
    }

    // MARK: State

    @State private var editingPlugin: OpencodePluginEntry?
    @State private var showNewPlugin = false
    @State private var pluginToDelete: OpencodePluginEntry?
    @State private var showDeleteAlert = false
    @State private var newPluginName: String = ""

    // MARK: - Body

    var body: some View {
        let model = viewModel
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("OpenCode")
                    .font(DSFont.settingsTitle)
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                configDirectoryRow(model: model)

                pluginsSection(model: model)

                providersInfoRow
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            viewModel.loadPlugins()
        }
        .sheet(item: $editingPlugin) { plugin in
            TextFileEditorSheet(
                fileURL: plugin.fileURL,
                displayTitle: plugin.filename
            ) { viewModel.loadPlugins() }
        }
        .sheet(isPresented: $showNewPlugin) {
            newPluginSheet
        }
        .alert("Удалить плагин?", isPresented: $showDeleteAlert, presenting: pluginToDelete) { plugin in
            Button("Удалить", role: .destructive) { viewModel.deletePlugin(plugin) }
            Button("Отмена", role: .cancel) {}
        } message: { plugin in
            Text("Файл «\(plugin.filename)» будет удалён без возможности восстановления.")
        }
    }

    // MARK: - Config Directory Row

    private func configDirectoryRow(model: OpencodeSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Директория конфига")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Text(model.displayConfigPath)
                    .font(DSFont.monoPath)
                    .foregroundStyle(DSColor.textPrimary)
                    .lineLimit(1)
                    .truncationMode(.middle)

                Spacer()

                Button {
                    NSWorkspace.shared.open(OpencodeSettingsPaneViewModel.configDirectoryURL)
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

    private func pluginsSection(model: OpencodeSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            pluginsSectionHeader

            if model.plugins.isEmpty {
                emptyPluginsState
            } else {
                SettingsListCard(items: model.plugins) { plugin in
                    pluginRow(plugin)
                }
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

    private var newPluginSheet: some View {
        let newFileURL: URL = {
            let name = newPluginName.trimmingCharacters(in: .whitespaces)
            let filename = name.isEmpty ? "my-plugin" : name
            return OpencodeSettingsPaneViewModel.pluginsDirectoryURL.appendingPathComponent("\(filename).ts")
        }()

        return TextFileEditorSheet(
            fileURL: newFileURL,
            displayTitle: newFileURL.lastPathComponent,
            defaultContent: newPluginTemplate
        ) {
            newPluginName = ""
            viewModel.loadPlugins()
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
        SettingsAuthInfoRow(
            hint: "Провайдеры и API-ключи управляются через CLI: ",
            command: "opencode providers"
        )
    }
}
