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
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(DSColor.agentCodeSpeak)

            Text("CodeSpeak Build")
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textPrimary)

            Spacer()

            // Exit code badge
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

            // Run button
            Button {
                guard let project else { return }
                Task { await model.runBuild(at: project.path) }
            } label: {
                if model.isRunning {
                    ProgressView()
                        .scaleEffect(0.5)
                        .frame(width: 16, height: 16)
                } else {
                    Image(systemName: "play.fill")
                        .font(.system(size: 11))
                        .foregroundStyle(DSColor.actionRun)
                        .frame(width: 16, height: 16)
                }
            }
            .buttonStyle(.plain)
            .disabled(model.isRunning || project == nil)
            .help("Run codespeak build")

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
            Text("Run codespeak build")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Text("Press ▶ to build specs")
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
