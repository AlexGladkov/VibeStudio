// MARK: - ClaudeSettingsPane
// Settings pane for the global Claude configuration file, subagents, commands, and skills.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - ClaudeSettingsPane

/// Settings pane showing the global `~/.claude/CLAUDE.md` config,
/// the list of subagents from `~/.claude/agents/`,
/// commands from `~/.claude/commands/`,
/// and skills from `~/.claude/skills/`.
struct ClaudeSettingsPane: View {

    // MARK: ViewModel (lazy init)

    @State private var vm: ClaudeSettingsPaneViewModel?
    private var viewModel: ClaudeSettingsPaneViewModel {
        if let existing = vm { return existing }
        let created = ClaudeSettingsPaneViewModel()
        DispatchQueue.main.async { vm = created }
        return created
    }

    // MARK: State -- Agents

    @State private var showEditor = false
    @State private var editingAgent: AgentEntry?
    @State private var showNewAgent = false
    @State private var agentToDelete: AgentEntry?
    @State private var showDeleteAlert = false

    // MARK: State -- Commands

    @State private var editingCommand: CommandEntry?
    @State private var showNewCommand = false
    @State private var commandToDelete: CommandEntry?
    @State private var showDeleteCommandAlert = false

    // MARK: State -- Skills

    @State private var viewingSkill: SkillInfo?

    // MARK: - Body

    var body: some View {
        let model = viewModel
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("Claude")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                fileRow(model: model)

                agentsSection(model: model)

                commandsSection(model: model)

                skillsSection(model: model)
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            if vm == nil { vm = ClaudeSettingsPaneViewModel() }
            let m = vm!
            m.loadAgents()
            m.loadCommands()
            m.loadSkills()
        }
        // MARK: Sheets -- Agents
        .sheet(isPresented: $showEditor) {
            ClaudeEditorSheet()
        }
        .sheet(item: $editingAgent) { agent in
            AgentEditorSheet(fileURL: agent.fileURL) {
                vm?.loadAgents()
            }
        }
        .sheet(isPresented: $showNewAgent) {
            AgentEditorSheet(fileURL: nil) {
                vm?.loadAgents()
            }
        }
        // MARK: Sheets -- Commands
        .sheet(item: $editingCommand) { cmd in
            CommandEditorSheet(fileURL: cmd.fileURL) {
                vm?.loadCommands()
            }
        }
        .sheet(isPresented: $showNewCommand) {
            CommandEditorSheet(fileURL: nil) {
                vm?.loadCommands()
            }
        }
        // MARK: Sheets -- Skills
        .sheet(item: $viewingSkill) { skill in
            SkillViewerSheet(skill: skill) {
                vm?.loadSkills()
            }
        }
        // MARK: Alerts
        .alert("Удалить агента?", isPresented: $showDeleteAlert, presenting: agentToDelete) { agent in
            Button("Удалить", role: .destructive) {
                vm?.deleteAgent(agent)
            }
            Button("Отмена", role: .cancel) {}
        } message: { agent in
            Text("Файл \u{00AB}\(agent.fileURL.lastPathComponent)\u{00BB} будет удалён без возможности восстановления.")
        }
        .alert("Удалить команду?", isPresented: $showDeleteCommandAlert, presenting: commandToDelete) { cmd in
            Button("Удалить", role: .destructive) {
                vm?.deleteCommand(cmd)
            }
            Button("Отмена", role: .cancel) {}
        } message: { cmd in
            Text("Файл \u{00AB}\(cmd.filename)\u{00BB} будет удалён без возможности восстановления.")
        }
    }

    // MARK: - Global Config Row

    private func fileRow(model: ClaudeSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Глобальный конфиг")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(DSColor.textSecondary)

            HStack(spacing: DSSpacing.sm) {
                Text(model.displayPath)
                    .font(.system(size: 12, weight: .regular, design: .monospaced))
                    .foregroundStyle(DSColor.textPrimary)
                    .lineLimit(1)
                    .truncationMode(.middle)

                Spacer()

                Button {
                    NSWorkspace.shared.activateFileViewerSelecting([ClaudeSettingsPaneViewModel.claudeURL])
                } label: {
                    Label("Finder", systemImage: "folder")
                        .font(.system(size: 11, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)

                Button {
                    showEditor = true
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

    // MARK: - Agents Section

    private func agentsSection(model: ClaudeSettingsPaneViewModel) -> some View {
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
                .frame(maxHeight: 320)
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

    private func agentRow(_ agent: AgentEntry) -> some View {
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

    // MARK: - Commands Section

    private func commandsSection(model: ClaudeSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            commandsSectionHeader

            if model.commands.isEmpty {
                emptyCommandsState
            } else {
                ScrollView(.vertical, showsIndicators: true) {
                    VStack(spacing: 0) {
                        ForEach(model.commands) { cmd in
                            commandRow(cmd)
                            if cmd.id != model.commands.last?.id {
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

    private var commandsSectionHeader: some View {
        SettingsSectionHeader(title: "Команды", showAddButton: true) {
            showNewCommand = true
        }
    }

    private var emptyCommandsState: some View {
        SettingsEmptyState(text: "Нет команд")
    }

    private func commandRow(_ cmd: CommandEntry) -> some View {
        SettingsItemRow(
            name: cmd.name,
            subtitle: cmd.filename,
            showDelete: true,
            onEdit: { editingCommand = cmd },
            onDelete: {
                commandToDelete = cmd
                showDeleteCommandAlert = true
            }
        )
    }

    // MARK: - Skills Section

    private func skillsSection(model: ClaudeSettingsPaneViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            skillsSectionHeader

            if model.skills.isEmpty {
                emptySkillsState
            } else {
                ScrollView(.vertical, showsIndicators: true) {
                    VStack(spacing: 0) {
                        ForEach(model.skills) { skill in
                            skillRow(skill)
                            if skill.id != model.skills.last?.id {
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

    private var skillsSectionHeader: some View {
        SettingsSectionHeader(title: "Скиллы")
    }

    private var emptySkillsState: some View {
        SettingsEmptyState(text: "Нет скиллов")
    }

    private func skillRow(_ skill: SkillInfo) -> some View {
        HStack(spacing: DSSpacing.sm) {
            VStack(alignment: .leading, spacing: 2) {
                Text(skill.name.isEmpty ? skill.directoryURL.lastPathComponent : skill.name)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(DSColor.textPrimary)
                    .lineLimit(1)

                if !skill.description.isEmpty {
                    Text(skill.description)
                        .font(.system(size: 11))
                        .foregroundStyle(DSColor.textMuted)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
            }

            Spacer()

            if !skill.isWritable {
                Image(systemName: "lock.fill")
                    .font(.system(size: 10))
                    .foregroundStyle(DSColor.textMuted)
            }

            Button {
                viewingSkill = skill
            } label: {
                Image(systemName: skill.isWritable ? "pencil" : "eye")
                    .font(.system(size: 11, weight: .medium))
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
    }
}
