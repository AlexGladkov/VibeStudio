// MARK: - CodexSettingsPane
// Settings pane for the Codex CLI AI coding assistant.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - CodexMemoryEntry

/// Represents a single Markdown memory file in ~/.codex/memories/.
private struct CodexMemoryEntry: Identifiable {
    let id: String
    let fileURL: URL
    let filename: String

    var displayName: String {
        fileURL.deletingPathExtension().lastPathComponent
    }
}

// MARK: - CodexSkillEntry

/// Represents a skill directory in ~/.codex/skills/.
private struct CodexSkillEntry: Identifiable {
    let id: String
    let directoryURL: URL
    var displayName: String { directoryURL.lastPathComponent }
}

// MARK: - CodexSettingsPane

/// Settings pane for Codex CLI.
///
/// Shows three sections:
/// 1. **Config** — `~/.codex/config.toml` with Finder + editor actions.
/// 2. **Memories** — Markdown files in `~/.codex/memories/` (create / edit / delete).
/// 3. **Skills** — Read-only list of directories in `~/.codex/skills/`.
struct CodexSettingsPane: View {

    // MARK: State — Config

    @State private var showConfigEditor = false

    // MARK: State — Memories

    @State private var memories: [CodexMemoryEntry] = []
    @State private var editingMemory: CodexMemoryEntry?
    @State private var showNewMemory = false
    @State private var memoryToDelete: CodexMemoryEntry?
    @State private var showDeleteAlert = false

    // MARK: State — Skills

    @State private var skills: [CodexSkillEntry] = []

    // MARK: Constants

    private static let configURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".codex/config.toml")

    private static let memoriesURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".codex/memories")

    private static let skillsURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".codex/skills")

    private var displayConfigPath: String {
        Self.configURL.tildeAbbreviatedPath
    }

    // MARK: - Body

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("Codex")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                configSection

                memoriesSection

                skillsSection
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            loadMemories()
            loadSkills()
        }
        .sheet(isPresented: $showConfigEditor) {
            TextFileEditorSheet(
                fileURL: Self.configURL,
                displayTitle: "config.toml",
                defaultContent: defaultConfigToml
            )
        }
        .sheet(item: $editingMemory) { memory in
            TextFileEditorSheet(
                fileURL: memory.fileURL,
                displayTitle: memory.filename
            ) { loadMemories() }
        }
        .sheet(isPresented: $showNewMemory) {
            TextFileEditorSheet(
                fileURL: Self.memoriesURL.appendingPathComponent("memory.md"),
                displayTitle: "Новая память",
                defaultContent: "# Память\n\n"
            ) { loadMemories() }
        }
        .alert("Удалить память?", isPresented: $showDeleteAlert, presenting: memoryToDelete) { mem in
            Button("Удалить", role: .destructive) { deleteMemory(mem) }
            Button("Отмена", role: .cancel) {}
        } message: { mem in
            Text("Файл «\(mem.filename)» будет удалён без возможности восстановления.")
        }
    }

    // MARK: - Config Section

    private var configSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Конфиг")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Text(displayConfigPath)
                    .font(.system(size: 12, weight: .regular, design: .monospaced))
                    .foregroundStyle(DSColor.textPrimary)
                    .lineLimit(1)
                    .truncationMode(.middle)

                Spacer()

                Button {
                    NSWorkspace.shared.activateFileViewerSelecting([Self.configURL])
                } label: {
                    Label("Finder", systemImage: "folder")
                        .font(.system(size: 11, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)

                Button {
                    showConfigEditor = true
                } label: {
                    Label("Редактировать", systemImage: "pencil")
                        .font(.system(size: 11, weight: .medium))
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
            }
            .padding(DSSpacing.md)
            .settingsCard()
        }
    }

    // MARK: - Memories Section

    private var memoriesSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            memoriesSectionHeader

            if memories.isEmpty {
                emptyMemoriesState
            } else {
                ScrollView(.vertical, showsIndicators: true) {
                    VStack(spacing: 0) {
                        ForEach(memories) { mem in
                            memoryRow(mem)
                            if mem.id != memories.last?.id {
                                Divider()
                                    .background(DSColor.borderSubtle)
                                    .padding(.horizontal, DSSpacing.md)
                            }
                        }
                    }
                }
                .frame(maxHeight: 280)
                .settingsCard()
            }
        }
    }

    private var memoriesSectionHeader: some View {
        SettingsSectionHeader(title: "Память", showAddButton: true) {
            showNewMemory = true
        }
    }

    private var emptyMemoriesState: some View {
        SettingsEmptyState(text: "Нет файлов памяти")
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

    private var skillsSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "Скиллы")

            if skills.isEmpty {
                SettingsEmptyState(text: "Нет скиллов")
            } else {
                ScrollView(.vertical, showsIndicators: true) {
                    VStack(spacing: 0) {
                        ForEach(skills) { skill in
                            skillRow(skill)
                            if skill.id != skills.last?.id {
                                Divider()
                                    .background(DSColor.borderSubtle)
                                    .padding(.horizontal, DSSpacing.md)
                            }
                        }
                    }
                }
                .frame(maxHeight: 200)
                .settingsCard()
            }
        }
    }

    private func skillRow(_ skill: CodexSkillEntry) -> some View {
        HStack(spacing: DSSpacing.sm) {
            Text(skill.displayName)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(DSColor.textPrimary)
                .lineLimit(1)

            Spacer()

            Button {
                NSWorkspace.shared.open(skill.directoryURL)
            } label: {
                Image(systemName: "folder")
                    .font(.system(size: 11, weight: .medium))
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
    }

    // MARK: - Data Loading

    private func loadMemories() {
        let fm = FileManager.default
        let dir = Self.memoriesURL
        guard let contents = try? fm.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.nameKey],
            options: [.skipsHiddenFiles]
        ) else {
            memories = []
            return
        }

        memories = contents
            .filter { $0.pathExtension == "md" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { url in
                CodexMemoryEntry(
                    id: url.path,
                    fileURL: url,
                    filename: url.lastPathComponent
                )
            }
    }

    private func loadSkills() {
        let fm = FileManager.default
        let dir = Self.skillsURL
        guard let contents = try? fm.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else {
            skills = []
            return
        }

        skills = contents
            .filter { url in
                (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true
            }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { url in
                CodexSkillEntry(id: url.path, directoryURL: url)
            }
    }

    // MARK: - Delete

    private func deleteMemory(_ mem: CodexMemoryEntry) {
        try? FileManager.default.removeItem(at: mem.fileURL)
        loadMemories()
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
