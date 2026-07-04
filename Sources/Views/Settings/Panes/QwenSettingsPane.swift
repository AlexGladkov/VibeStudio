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

    @State private var vmBox = LazyStateObject<QwenSettingsPaneViewModel>()
    private var viewModel: QwenSettingsPaneViewModel {
        vmBox.resolve { QwenSettingsPaneViewModel() }
    }

    // MARK: State — Config

    @State private var showEditor = false

    // MARK: State — Agents

    @State private var editingAgent: AgentEntry?
    @State private var showNewAgent = false
    @State private var agentToDelete: AgentEntry?
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

                versionSection

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
            onDismiss: { viewModel.checkConfigExists() },
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
            ) { viewModel.loadAgents() }
        }
        .sheet(isPresented: $showNewAgent) {
            TextFileEditorSheet(
                fileURL: QwenSettingsPaneViewModel.agentsDirectoryURL.appendingPathComponent("new-agent.md"),
                displayTitle: "New agent",
                defaultContent: newAgentTemplate
            ) { viewModel.loadAgents() }
        }
        .alert("Delete agent?", isPresented: $showDeleteAlert, presenting: agentToDelete) { agent in
            Button("Delete", role: .destructive) { viewModel.deleteAgent(agent) }
            Button("Cancel", role: .cancel) {}
        } message: { agent in
            Text("File \u{00AB}\(agent.fileURL.lastPathComponent)\u{00BB} will be permanently deleted.")
        }
    }

    // MARK: - Version

    private var versionSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "Version")
            AgentVersionRow(assistant: .qwenCode)
                .settingsCard()
        }
    }

    // MARK: - Global Config Row

    private func fileRow(model: QwenSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Global config")
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
                SettingsListCard(items: model.agents) { agent in
                    agentRow(agent)
                }
            }
        }
    }

    private var agentsSectionHeader: some View {
        SettingsSectionHeader(title: "Subagents", showAddButton: true) {
            showNewAgent = true
        }
    }

    private var emptyAgentsState: some View {
        SettingsEmptyState(text: "No subagents")
    }

    private func agentRow(_ agent: AgentEntry) -> some View {
        AgentRowView(
            agent: agent,
            onEdit: { editingAgent = agent },
            onDelete: {
                agentToDelete = agent
                showDeleteAlert = true
            }
        )
    }

    // MARK: - Auth Info Row

    private var authInfoRow: some View {
        SettingsAuthInfoRow(
            hint: "Set the DashScope API key via an environment variable:",
            envVar: "export DASHSCOPE_API_KEY=your-key"
        )
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
