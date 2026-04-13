// MARK: - SpecsPanelView
// Sidebar SPECS section: lists spec files with pass/fail status.
// macOS 14+, Swift 5.10

import SwiftUI

/// SPECS sidebar section showing all `spec/*.cs.md` files for the active project.
///
/// Header shows aggregate stats + build/new-spec action buttons.
/// Each row shows spec name + status indicator.
/// Tapping a row opens the spec in `SpecEditorSheet`.
private enum SpecsSheet: Identifiable {
    case editor(SpecFile)
    case wizard
    var id: String {
        switch self {
        case .editor(let s): return "editor-\(s.id)"
        case .wizard:        return "wizard"
        }
    }
}

struct SpecsPanelView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.codeSpeak) private var codeSpeak
    @Environment(\.navigationCoordinator) private var navigationCoordinator

    @State private var vm: SpecsViewModel?
    @State private var activeSheet: SpecsSheet?

    private var viewModel: SpecsViewModel {
        if let existing = vm { return existing }
        let created = SpecsViewModel()
        DispatchQueue.main.async { vm = created }
        return created
    }

    private var activeProject: Project? {
        projectManager.projects.first { $0.id == projectManager.activeProjectId }
    }

    var body: some View {
        let model = viewModel

        VStack(spacing: 0) {
            headerView(model: model)
            Divider()

            if let project = activeProject {
                if model.isLoading {
                    loadingView
                } else if model.specFiles.isEmpty {
                    emptyStateView
                } else {
                    specListView(model: model)
                }
                EmptyView()
                    .task(id: project.id) {
                        await model.loadSpecs(at: project.path)
                    }
            } else {
                noProjectView
            }
        }
        .onChange(of: projectManager.activeProjectId) { _, _ in
            if let project = activeProject {
                Task { await model.loadSpecs(at: project.path) }
            }
        }
        .onAppear {
            if vm == nil {
                vm = SpecsViewModel()
                if let project = activeProject {
                    Task { await viewModel.loadSpecs(at: project.path) }
                }
            }
        }
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .editor(let spec):
                SpecEditorSheet(specFile: spec)
            case .wizard:
                if let project = activeProject {
                    SpecWizardSheet(projectPath: project.path) {
                        if let project = activeProject {
                            Task { await model.refresh(at: project.path) }
                        }
                    }
                }
            }
        }
    }

    // MARK: - Header

    private func headerView(model: SpecsViewModel) -> some View {
        HStack(spacing: DSSpacing.xs) {
            Text("SPECS")
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textSecondary)

            if let stats = model.stats {
                Text("\(stats.passing)/\(stats.total) passing")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(stats.allPassing ? DSColor.gitAdded : DSColor.gitModified)
            }

            Spacer()

            // Build button — triggers SpecBuildPanel
            Button {
                withAnimation(.easeOut(duration: 0.2)) {
                    navigationCoordinator.showingSpecPanel = true
                }
            } label: {
                Image(systemName: "play.circle")
                    .font(.system(size: 13))
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
            .help("Run codespeak build")

            // New spec button
            Button {
                activeSheet = .wizard
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 13))
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
            .help("New spec")
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: DSLayout.gitSectionHeaderHeight)
    }

    // MARK: - Spec List

    private func specListView(model: SpecsViewModel) -> some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(model.specFiles) { spec in
                    specRow(spec: spec)
                }
            }
            .padding(.horizontal, DSSpacing.sm)
            .padding(.vertical, DSSpacing.xs)
        }
    }

    private func specRow(spec: SpecFile) -> some View {
        Button {
            activeSheet = .editor(spec)
        } label: {
            HStack(spacing: DSSpacing.xs) {
                // Status dot
                Circle()
                    .fill(statusColor(spec.status))
                    .frame(width: DSLayout.indicatorSize, height: DSLayout.indicatorSize)

                // Name
                Text(spec.name)
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .lineLimit(1)
                    .truncationMode(.tail)

                Spacer()

                // Status badge
                statusBadge(for: spec)
            }
            .padding(.horizontal, DSSpacing.xs)
            .frame(height: DSLayout.gitFileRowHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .sidebarHover(cornerRadius: DSRadius.sm)
    }

    @ViewBuilder
    private func statusBadge(for spec: SpecFile) -> some View {
        switch spec.status {
        case .passing:
            Image(systemName: "checkmark")
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(DSColor.gitAdded)
        case .failing:
            Image(systemName: "xmark")
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(DSColor.gitDeleted)
        case .unknown:
            Image(systemName: "questionmark")
                .font(.system(size: 10))
                .foregroundStyle(DSColor.textMuted)
        }
    }

    private func statusColor(_ status: SpecStatus) -> Color {
        switch status {
        case .passing: return DSColor.gitAdded
        case .failing:  return DSColor.gitDeleted
        case .unknown:  return DSColor.indicatorIdle
        }
    }

    // MARK: - Empty States

    private var loadingView: some View {
        VStack {
            Spacer()
            ProgressView()
                .scaleEffect(0.7)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var emptyStateView: some View {
        VStack(spacing: DSSpacing.sm) {
            Spacer()
            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 24))
                .foregroundStyle(DSColor.textMuted)
            Text("No specs found")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Text("spec/*.cs.md")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted.opacity(0.6))
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var noProjectView: some View {
        VStack {
            Spacer()
            Text("No project selected")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
