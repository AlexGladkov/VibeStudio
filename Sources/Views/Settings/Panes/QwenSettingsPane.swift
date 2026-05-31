// MARK: - QwenSettingsPane
// Settings pane for the Qwen Code AI coding assistant (Alibaba / Claude fork).
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - QwenSettingsPane

/// Settings pane for Qwen Code CLI.
///
/// Qwen Code is a Claude Code fork so it follows an identical config structure:
/// - `~/.qwen/QWEN.md` — global instructions injected into every session.
/// - `~/.qwen/agents/` — optional subagent markdown files.
struct QwenSettingsPane: View {

    // MARK: ViewModel (lazy init)

    @State private var vm: QwenSettingsPaneViewModel?
    private var viewModel: QwenSettingsPaneViewModel {
        if let existing = vm { return existing }
        let created = QwenSettingsPaneViewModel()
        Task { @MainActor in vm = created }
        return created
    }

    // MARK: State — Config

    @State private var showEditor = false

    // MARK: State — Agents

    @State private var editingAgent: QwenAgentEntry?
    @State private var showNewAgent = false
    @State private var agentToDelete: QwenAgentEntry?
    @State private var showDeleteAlert = false

    // MARK: - Body

    var body: some View {
        let model = viewModel
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("Qwen")
                    .font(DSFont.settingsTitle)
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                fileRow(model: model)

                agentsSection(model: model)

                authInfoRow
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            let initialModel = viewModel
            initialModel.checkConfigExists()
            initialModel.loadAgents()
        }
        .sheet(
            isPresented: $showEditor,
            onDismiss: { vm?.checkConfigExists() },
            content: {
                TextFileEditorSheet(
                    fileURL: QwenSettingsPaneViewModel.qwenURL,
                    displayTitle: "QWEN.md",
                    defaultContent: defaultQwenMd
                )
            }
        )
        .sheet(item: $editingAgent) { agent in
            TextFileEditorSheet(
                fileURL: agent.fileURL,
                displayTitle: agent.name
            ) { vm?.loadAgents() }
        }
        .sheet(isPresented: $showNewAgent) {
            TextFileEditorSheet(
                fileURL: QwenSettingsPaneViewModel.agentsDirectoryURL.appendingPathComponent("new-agent.md"),
                displayTitle: "Новый агент",
                defaultContent: newAgentTemplate
            ) { vm?.loadAgents() }
        }
        .alert("Удалить агента?", isPresented: $showDeleteAlert, presenting: agentToDelete) { agent in
            Button("Удалить", role: .destructive) { vm?.deleteAgent(agent) }
            Button("Отмена", role: .cancel) {}
        } message: { agent in
            Text("Файл «\(agent.fileURL.lastPathComponent)» будет удалён без возможности восстановления.")
        }
    }

    // MARK: - Global Config Row

    private func fileRow(model: QwenSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Глобальный конфиг")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            SettingsConfigFileRow(
                displayPath: model.displayPath,
                configExists: model.configExists,
                onReveal: {
                    NSWorkspace.shared.activateFileViewerSelecting([QwenSettingsPaneViewModel.qwenURL])
                },
                onEdit: { showEditor = true }
            )
        }
    }

    // MARK: - Agents Section

    private func agentsSection(model: QwenSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            agentsSectionHeader

            if model.agents.isEmpty {
                emptyAgentsState
            } else {
                ScrollView(.vertical, showsIndicators: true) {
                    VStack(spacing: 0) {
                        ForEach(model.agents) { agent in
                            agentRow(agent)
                            if agent.id != model.agents.last?.id {
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
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Image(systemName: "info.circle")
                    .font(DSFont.bodySmall)
                    .foregroundStyle(DSColor.textMuted)

                VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                    Text("Установите API-ключ DashScope через переменную окружения:")
                        .font(DSFont.bodySmall)
                        .foregroundStyle(DSColor.textMuted)

                    Text("export DASHSCOPE_API_KEY=your-key")
                        .font(DSFont.monoSmall)
                        .foregroundStyle(DSColor.textSecondary)
                }

                Spacer()
            }
            .padding(DSSpacing.md)
            .settingsCard()
        }
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
