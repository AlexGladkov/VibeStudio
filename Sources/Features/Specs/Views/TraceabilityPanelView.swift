// MARK: - TraceabilityPanelView
// Right-side panel showing spec ↔ source file cross-references.
// macOS 14+, Swift 5.10

import SwiftUI

/// Right-side panel showing bidirectional traceability between specs and source files.
///
/// Scans `spec/*.cs.md` for `@file:` markers and shows:
/// - Which source files each spec references.
/// - Which specs reference each source file.
struct TraceabilityPanelView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.navigationCoordinator) private var navigationCoordinator

    @State private var vm: TraceabilityPanelViewModel?
    @State private var selectedFile: String?

    private var viewModel: TraceabilityPanelViewModel {
        if let existing = vm { return existing }
        let created = TraceabilityPanelViewModel()
        Task { @MainActor in vm = created }
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

            if model.isLoading {
                loadingView
            } else if model.referencedFiles.isEmpty && model.specToFiles.isEmpty {
                emptyStateView
            } else {
                contentView(model: model)
            }
        }
        .frame(
            minWidth: DSLayout.traceabilityPanelMinWidth,
            idealWidth: DSLayout.traceabilityPanelDefaultWidth,
            maxWidth: DSLayout.traceabilityPanelMaxWidth
        )
        .background(DSColor.surfaceRaised)
        .task(id: projectManager.activeProjectId) {
            if let project = activeProject {
                await model.scan(at: project.path)
            }
        }
        .onAppear {
            if vm == nil {
                vm = TraceabilityPanelViewModel()
                if let project = activeProject {
                    Task { await viewModel.scan(at: project.path) }
                }
            }
        }
    }

    // MARK: - Header

    private func headerView(model: TraceabilityPanelViewModel) -> some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "link")
                .font(DSFont.smallButtonLabel)
                .foregroundStyle(DSColor.agentCodeSpeak)

            Text("Traceability")
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textPrimary)

            Spacer()

            // Refresh
            Button {
                if let project = activeProject {
                    Task { await model.scan(at: project.path) }
                }
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
            .help("Refresh traceability map")

            // Close
            Button {
                withAnimation(.easeOut(duration: 0.2)) {
                    navigationCoordinator.showingTraceabilityPanel = false
                }
            } label: {
                Image(systemName: "xmark")
                    .font(DSFont.iconMD)
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: DSLayout.gitSectionHeaderHeight)
    }

    // MARK: - Content

    private func contentView(model: TraceabilityPanelViewModel) -> some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 0) {

                // Spec → Files section
                if !model.specToFiles.isEmpty {
                    sectionHeader("SPECS → FILES")
                    ForEach(model.specToFiles.keys.sorted { $0.lastPathComponent < $1.lastPathComponent }, id: \.self) { specURL in
                        let specName = specURL.deletingPathExtension().deletingPathExtension().lastPathComponent
                        let files = model.specToFiles[specURL] ?? []
                        specToFilesRow(specName: specName, files: files)
                    }
                }

                // File → Specs section
                if !model.fileToSpecs.isEmpty {
                    sectionHeader("FILES → SPECS")
                    ForEach(model.referencedFiles, id: \.self) { filePath in
                        let specs = model.fileToSpecs[filePath] ?? []
                        fileToSpecsRow(filePath: filePath, specs: specs)
                    }
                }
            }
            .padding(.horizontal, DSSpacing.sm)
            .padding(.vertical, DSSpacing.xs)
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(DSFont.sidebarSection)
            .foregroundStyle(DSColor.textMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, DSSpacing.xs)
            .padding(.vertical, DSSpacing.xs)
            .padding(.top, DSSpacing.sm)
    }

    private func specToFilesRow(specName: String, files: [String]) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.xxs) {
            HStack(spacing: DSSpacing.xs) {
                Image(systemName: "doc.text")
                    .font(DSFont.iconMD)
                    .foregroundStyle(DSColor.agentCodeSpeak)
                Text(specName)
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .lineLimit(1)
            }
            .padding(.horizontal, DSSpacing.xs)
            .frame(height: DSLayout.gitFileRowHeight)

            ForEach(files, id: \.self) { file in
                HStack(spacing: DSSpacing.xs) {
                    Text("→")
                        .font(DSFont.iconMD)
                        .foregroundStyle(DSColor.textMuted)
                        .frame(width: DSLayout.smallIconButtonSize)
                    Text(file)
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textSecondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                .padding(.horizontal, DSSpacing.sm)
                .padding(.bottom, DSSpacing.xxs)
            }
        }
        .background(DSColor.surfaceOverlay.opacity(0.4), in: RoundedRectangle(cornerRadius: DSRadius.sm))
        .padding(.bottom, DSSpacing.xs)
    }

    private func fileToSpecsRow(filePath: String, specs: [String]) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.xxs) {
            HStack(spacing: DSSpacing.xs) {
                Image(systemName: "doc.fill")
                    .font(DSFont.iconMD)
                    .foregroundStyle(DSColor.textSecondary)
                Text(URL(fileURLWithPath: filePath).lastPathComponent)
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .lineLimit(1)
            }
            .padding(.horizontal, DSSpacing.xs)
            .frame(height: DSLayout.gitFileRowHeight)

            ForEach(specs, id: \.self) { spec in
                HStack(spacing: DSSpacing.xs) {
                    Text("→")
                        .font(DSFont.iconMD)
                        .foregroundStyle(DSColor.textMuted)
                        .frame(width: DSLayout.smallIconButtonSize)
                    Text(spec + ".cs.md")
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.agentCodeSpeak)
                        .lineLimit(1)
                }
                .padding(.horizontal, DSSpacing.sm)
                .padding(.bottom, DSSpacing.xxs)
            }
        }
        .background(DSColor.surfaceOverlay.opacity(0.4), in: RoundedRectangle(cornerRadius: DSRadius.sm))
        .padding(.bottom, DSSpacing.xs)
    }

    // MARK: - Empty States

    private var loadingView: some View {
        VStack {
            Spacer()
            ProgressView().scaleEffect(0.7)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var emptyStateView: some View {
        VStack(spacing: DSSpacing.sm) {
            Spacer()
            Image(systemName: "link.badge.plus")
                .font(DSFont.emptyStateIcon)
                .foregroundStyle(DSColor.textMuted)
            Text("No traceability links")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Text("Add @file: markers to specs")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textDisabled)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}
