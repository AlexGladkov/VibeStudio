// MARK: - CodexSettingsPane
// Settings pane for the Codex CLI AI coding assistant.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - CodexSettingsPane

/// Settings pane for Codex CLI.
///
/// Shows three sections:
/// 1. **Config** — `~/.codex/config.toml` with Finder + editor actions.
/// 2. **Memories** — Markdown files in `~/.codex/memories/` (create / edit / delete).
/// 3. **Skills** — Read-only list of directories in `~/.codex/skills/`.
struct CodexSettingsPane: View {

    // MARK: ViewModel (lazy init)

    @State private var vmBox = LazyStateObject<CodexSettingsPaneViewModel>()
    private var viewModel: CodexSettingsPaneViewModel {
        vmBox.resolve { CodexSettingsPaneViewModel() }
    }

    // MARK: State — Config

    @State private var showConfigEditor = false

    // MARK: State — Memories

    @State private var editingMemory: CodexMemoryEntry?
    @State private var showNewMemory = false
    @State private var memoryToDelete: CodexMemoryEntry?
    @State private var showDeleteAlert = false

    // MARK: - Body

    var body: some View {
        let model = viewModel
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("Codex")
                    .font(DSFont.settingsTitle)
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                versionSection

                configSection(model: model)

                memoriesSection(model: model)

                skillsSection(model: model)
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            let initialModel = viewModel
            initialModel.loadMemories()
            initialModel.loadSkills()
        }
        .sheet(isPresented: $showConfigEditor) {
            TextFileEditorSheet(
                fileURL: CodexSettingsPaneViewModel.configURL,
                displayTitle: "config.toml",
                defaultContent: defaultConfigToml
            )
        }
        .sheet(item: $editingMemory) { memory in
            TextFileEditorSheet(
                fileURL: memory.fileURL,
                displayTitle: memory.filename
            ) { viewModel.loadMemories() }
        }
        .sheet(isPresented: $showNewMemory) {
            TextFileEditorSheet(
                fileURL: CodexSettingsPaneViewModel.memoriesURL.appendingPathComponent("memory.md"),
                displayTitle: "New memory",
                defaultContent: "# Память\n\n"
            ) { viewModel.loadMemories() }
        }
        .alert("Delete memory?", isPresented: $showDeleteAlert, presenting: memoryToDelete) { mem in
            Button("Delete", role: .destructive) { viewModel.deleteMemory(mem) }
            Button("Cancel", role: .cancel) {}
        } message: { mem in
            Text("File \u{00AB}\(mem.filename)\u{00BB} will be permanently deleted.")
        }
    }

    // MARK: - Version

    private var versionSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "Version")
            AgentVersionRow(assistant: .codex)
                .settingsCard()
        }
    }

    // MARK: - Config Section

    private func configSection(model: CodexSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Config")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            SettingsConfigFileRow(
                displayPath: model.displayPath,
                configExists: true,
                alwaysShowReveal: true,
                onReveal: {
                    NSWorkspace.shared.activateFileViewerSelecting([CodexSettingsPaneViewModel.configURL])
                },
                onEdit: { showConfigEditor = true }
            )
        }
    }

    // MARK: - Memories Section

    private func memoriesSection(model: CodexSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            memoriesSectionHeader

            if model.memories.isEmpty {
                emptyMemoriesState
            } else {
                SettingsListCard(items: model.memories) { mem in
                    memoryRow(mem)
                }
            }
        }
    }

    private var memoriesSectionHeader: some View {
        SettingsSectionHeader(title: "Memory", showAddButton: true) {
            showNewMemory = true
        }
    }

    private var emptyMemoriesState: some View {
        SettingsEmptyState(text: "No memory files")
    }

    private func memoryRow(_ mem: CodexMemoryEntry) -> some View {
        SettingsItemRow(
            name: mem.displayName,
            subtitle: mem.filename,
            showDelete: true,
            onEdit: { editingMemory = mem },
            onDelete: {
                memoryToDelete = mem
                showDeleteAlert = true
            }
        )
    }

    // MARK: - Skills Section

    private func skillsSection(model: CodexSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "Skills")

            if model.skills.isEmpty {
                SettingsEmptyState(text: "No skills")
            } else {
                SettingsListCard(items: model.skills, maxHeight: DSLayout.settingsListMaxHeightSmall) { skill in
                    skillRow(skill)
                }
            }
        }
    }

    private func skillRow(_ skill: CodexSkillEntry) -> some View {
        HStack(spacing: DSSpacing.sm) {
            Text(skill.displayName)
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textPrimary)
                .lineLimit(1)

            Spacer()

            Button {
                NSWorkspace.shared.open(skill.directoryURL)
            } label: {
                Image(systemName: "folder")
                    .font(DSFont.smallButtonLabel)
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
    }

    // MARK: - Default Config

    private let defaultConfigToml = """
        # Codex configuration
        # Full reference: https://github.com/openai/codex

        # model = "o4-mini"

        # [sandbox_permissions]
        # disk-full-read-access = true

        # [shell_environment_policy]
        # inherit = "none"
        """
}
