// MARK: - SpecBuildPanelView
// Right-side panel showing CodeSpeak build output.
// macOS 14+, Swift 5.10

import SwiftUI

/// Right-side panel that runs `codespeak build` and streams its output.
///
/// Pattern mirrors `GitChangesPanelView`.
struct SpecBuildPanelView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.codeSpeak) private var codeSpeak
    @Environment(\.navigationCoordinator) private var navigationCoordinator

    @State private var vm: SpecBuildPanelViewModel?
    @State private var scrollProxy: ScrollViewProxy?

    private var viewModel: SpecBuildPanelViewModel {
        if let existing = vm { return existing }
        let created = SpecBuildPanelViewModel(
            codeSpeak: codeSpeak,
            projectManager: projectManager
        )
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel
        let activeProject = projectManager.projects.first {
            $0.id == projectManager.activeProjectId
        }

        VStack(spacing: 0) {
            headerView(model: model, project: activeProject)
            Divider()
            outputView(model: model)
        }
        .frame(
            minWidth: DSLayout.specPanelMinWidth,
            idealWidth: DSLayout.specPanelDefaultWidth,
            maxWidth: DSLayout.specPanelMaxWidth
        )
        .background(DSColor.surfaceRaised)
        .onAppear {
            if vm == nil {
                vm = SpecBuildPanelViewModel(
                    codeSpeak: codeSpeak,
                    projectManager: projectManager
                )
            }
        }
    }

    // MARK: - Header

    private func headerView(model: SpecBuildPanelViewModel, project: Project?) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: DSSpacing.xs) {
                Image(systemName: "doc.text.magnifyingglass")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(DSColor.agentCodeSpeak)

                CommandSelectorView(
                    selectedCommand: Binding(
                        get: { model.selectedCommand },
                        set: { model.selectedCommand = $0 }
                    ),
                    taskName: Binding(
                        get: { model.taskName },
                        set: { model.taskName = $0 }
                    ),
                    changeMessage: Binding(
                        get: { model.changeMessage },
                        set: { model.changeMessage = $0 }
                    ),
                    isRunning: model.isRunning
                )

                Spacer()

                // Exit code badge (only for stats-capable commands)
                if model.selectedCommand.supportsStatsParsing {
                    if let code = model.exitCode {
                        Text(code == 0 ? "PASS" : "FAIL")
                            .font(.system(size: 9, weight: .semibold))
                            .foregroundStyle(code == 0 ? DSColor.gitAdded : DSColor.gitDeleted)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(
                                code == 0 ? DSColor.diffAddedBg : DSColor.diffDeletedBg,
                                in: RoundedRectangle(cornerRadius: 3)
                            )
                    }

                    // Stats summary
                    if let stats = model.stats {
                        Text("\(stats.passing)/\(stats.total)")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(stats.allPassing ? DSColor.gitAdded : DSColor.gitModified)
                    }
                }

                // Play/Stop button
                Button {
                    guard let project else { return }
                    if model.isRunning {
                        model.stop()
                    } else {
                        Task { await model.run(at: project.path) }
                    }
                } label: {
                    Image(systemName: model.isRunning ? "stop.fill" : "play.fill")
                        .font(.system(size: 11))
                        .foregroundStyle(model.isRunning ? DSColor.actionStop : DSColor.actionRun)
                        .frame(width: 16, height: 16)
                }
                .buttonStyle(.plain)
                .disabled(!model.canRun && !model.isRunning)
                .help(model.isRunning ? "Stop codespeak" : "Run codespeak \(model.selectedCommand.displayName.lowercased())")

                // Close button
                Button {
                    withAnimation(.easeOut(duration: 0.2)) {
                        navigationCoordinator.showingSpecPanel = false
                    }
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 10))
                        .foregroundStyle(DSColor.textMuted)
                        .frame(width: 16, height: 16)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, DSSpacing.md)
            .frame(height: DSLayout.gitSectionHeaderHeight)

            if model.selectedCommand.requiresInput {
                HStack(spacing: DSSpacing.xs) {
                    Text(model.selectedCommand.inputLabel)
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textSecondary)
                        .frame(width: 50, alignment: .trailing)

                    TextField(
                        model.selectedCommand.inputPlaceholder,
                        text: model.selectedCommand == .task
                            ? Binding(
                                get: { model.taskName },
                                set: { model.taskName = $0 }
                            )
                            : Binding(
                                get: { model.changeMessage },
                                set: { model.changeMessage = $0 }
                            )
                    )
                    .textFieldStyle(.plain)
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .padding(.horizontal, DSSpacing.xs)
                    .padding(.vertical, DSSpacing.xxs)
                    .background(
                        DSColor.surfaceInput,
                        in: RoundedRectangle(cornerRadius: DSRadius.sm)
                    )
                    .disabled(model.isRunning)
                }
                .padding(.horizontal, DSSpacing.md)
                .frame(height: 28)
            }
        }
    }

    // MARK: - Output

    private func outputView(model: SpecBuildPanelViewModel) -> some View {
        Group {
            if model.outputLines.isEmpty && !model.isRunning {
                emptyStateView
            } else {
                ScrollViewReader { proxy in
                    ScrollView(.vertical, showsIndicators: true) {
                        LazyVStack(alignment: .leading, spacing: 0) {
                            ForEach(Array(model.outputLines.enumerated()), id: \.offset) { idx, line in
                                Text(line)
                                    .font(DSFont.terminal(size: 11))
                                    .foregroundStyle(lineColor(for: line))
                                    .textSelection(.enabled)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, DSSpacing.md)
                                    .padding(.vertical, 1)
                                    .id(idx)
                            }
                        }
                        .padding(.vertical, DSSpacing.xs)
                    }
                    .onChange(of: model.outputLines.count) { _, count in
                        if count > 0 {
                            withAnimation(.none) {
                                proxy.scrollTo(count - 1, anchor: .bottom)
                            }
                        }
                    }
                }
            }
        }
    }

    private var emptyStateView: some View {
        VStack(spacing: DSSpacing.sm) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 24))
                .foregroundStyle(DSColor.textMuted)
            Text("Run codespeak command")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Text("Press \u{25B6} to \(viewModel.selectedCommand.displayName.lowercased())")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted.opacity(0.6))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Line Coloring

    private func lineColor(for line: String) -> Color {
        let lower = line.lowercased()
        if lower.contains("error") || lower.contains("fail") || line.hasPrefix("⚠") {
            return DSColor.gitDeleted
        }
        if lower.contains("pass") || lower.contains("✓") || lower.contains("✔") {
            return DSColor.gitAdded
        }
        if lower.contains("warn") {
            return DSColor.gitModified
        }
        return DSColor.textPrimary
    }
}
