// MARK: - QwenSettingsPane
// Settings pane for the Qwen Code AI coding assistant (Alibaba / Claude fork).
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - QwenAgentEntry

/// Represents a single agent markdown file in ~/.qwen/agents/.
private struct QwenAgentEntry: Identifiable {
    let id: String
    let fileURL: URL
    let name: String
    let description: String
}

// MARK: - QwenSettingsPane

/// Settings pane for Qwen Code CLI.
///
/// Qwen Code is a Claude Code fork so it follows an identical config structure:
/// - `~/.qwen/QWEN.md` — global instructions injected into every session.
/// - `~/.qwen/agents/` — optional subagent markdown files.
struct QwenSettingsPane: View {

    // MARK: State — Config

    @State private var showEditor = false
    @State private var configExists = false

    // MARK: State — Agents

    @State private var agents: [QwenAgentEntry] = []
    @State private var editingAgent: QwenAgentEntry?
    @State private var showNewAgent = false
    @State private var agentToDelete: QwenAgentEntry?
    @State private var showDeleteAlert = false

    // MARK: Constants

    private static let qwenURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".qwen/QWEN.md")

    private static let agentsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".qwen/agents")

    private var displayPath: String {
        Self.qwenURL.tildeAbbreviatedPath
    }

    // MARK: - Body

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("Qwen")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                fileRow

                agentsSection

                authInfoRow
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            checkConfigExists()
            loadAgents()
        }
        .sheet(isPresented: $showEditor, onDismiss: checkConfigExists) {
            TextFileEditorSheet(
                fileURL: Self.qwenURL,
                displayTitle: "QWEN.md",
                defaultContent: defaultQwenMd
            )
        }
        .sheet(item: $editingAgent) { agent in
            TextFileEditorSheet(
                fileURL: agent.fileURL,
                displayTitle: agent.name
            ) { loadAgents() }
        }
        .sheet(isPresented: $showNewAgent) {
            TextFileEditorSheet(
                fileURL: Self.agentsDirectoryURL.appendingPathComponent("new-agent.md"),
                displayTitle: "Новый агент",
                defaultContent: newAgentTemplate
            ) { loadAgents() }
        }
        .alert("Удалить агента?", isPresented: $showDeleteAlert, presenting: agentToDelete) { agent in
            Button("Удалить", role: .destructive) { deleteAgent(agent) }
            Button("Отмена", role: .cancel) {}
        } message: { agent in
            Text("Файл «\(agent.fileURL.lastPathComponent)» будет удалён без возможности восстановления.")
        }
    }

    // MARK: - Global Config Row

    private var fileRow: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Глобальный конфиг")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Text(displayPath)
                    .font(.system(size: 12, weight: .regular, design: .monospaced))
                    .foregroundStyle(configExists ? DSColor.textPrimary : DSColor.textMuted)
                    .lineLimit(1)
                    .truncationMode(.middle)

                if !configExists {
                    Text("не найден")
                        .font(.system(size: 11))
                        .foregroundStyle(DSColor.textMuted)
                }

                Spacer()

                if configExists {
                    Button {
                        NSWorkspace.shared.activateFileViewerSelecting([Self.qwenURL])
                    } label: {
                        Label("Finder", systemImage: "folder")
                            .font(.system(size: 11, weight: .medium))
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
                    .font(.system(size: 11, weight: .medium))
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
            }
            .padding(DSSpacing.md)
            .settingsCard()
        }
    }

    // MARK: - Agents Section

    private var agentsSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            agentsSectionHeader

            if agents.isEmpty {
                emptyAgentsState
            } else {
                ScrollView(.vertical, showsIndicators: true) {
                    VStack(spacing: 0) {
                        ForEach(agents) { agent in
                            agentRow(agent)
                            if agent.id != agents.last?.id {
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

    private var agentsSectionHeader: some View {
        SettingsSectionHeader(title: "Субагенты", showAddButton: true) {
            showNewAgent = true
        }
    }

    private var emptyAgentsState: some View {
        SettingsEmptyState(text: "Нет субагентов")
    }

    private func agentRow(_ agent: QwenAgentEntry) -> some View {
        SettingsItemRow(
            name: agent.name,
            subtitle: agent.description.isEmpty ? nil : agent.description,
            showDelete: true,
            onEdit: { editingAgent = agent },
            onDelete: {
                agentToDelete = agent
                showDeleteAlert = true
            }
        )
    }

    // MARK: - Auth Info Row

    private var authInfoRow: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Авторизация")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Image(systemName: "info.circle")
                    .font(.system(size: 12))
                    .foregroundStyle(DSColor.textMuted)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Установите API-ключ DashScope через переменную окружения:")
                        .font(.system(size: 12))
                        .foregroundStyle(DSColor.textMuted)

                    Text("export DASHSCOPE_API_KEY=your-key")
                        .font(.system(size: 11, design: .monospaced))
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
        configExists = FileManager.default.fileExists(atPath: Self.qwenURL.path)
    }

    // MARK: - Data Loading

    private func loadAgents() {
        let fm = FileManager.default
        let dir = Self.agentsDirectoryURL
        guard let contents = try? fm.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.nameKey],
            options: [.skipsHiddenFiles]
        ) else {
            agents = []
            return
        }

        agents = contents
            .filter { $0.pathExtension == "md" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .compactMap { url in
                guard let text = try? String(contentsOf: url, encoding: .utf8) else { return nil }
                let fields = parseFrontmatter(text)
                return QwenAgentEntry(
                    id: url.path,
                    fileURL: url,
                    name: fields.name.isEmpty ? url.deletingPathExtension().lastPathComponent : fields.name,
                    description: fields.description
                )
            }
    }

    // MARK: - Delete

    private func deleteAgent(_ agent: QwenAgentEntry) {
        try? FileManager.default.removeItem(at: agent.fileURL)
        loadAgents()
    }

    // MARK: - Templates

    private let defaultQwenMd = """
        # Qwen Global Config

        <!-- Инструкции, которые будут добавлены в каждую сессию. -->
        """

    private let newAgentTemplate = """
        ---
        name: my-agent
        description:
        model: qwen-coder-plus
        ---

        Ты — агент, выполняющий...
        """
}
