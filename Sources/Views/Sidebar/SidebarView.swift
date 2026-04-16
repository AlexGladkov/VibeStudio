// MARK: - SidebarView
// Left sidebar with icon strip and switchable Files/Git sections.
// Background: ultraThinMaterial, right border 1pt.
// macOS 14+, Swift 5.10

import AppKit
import OSLog
import SwiftUI
import UniformTypeIdentifiers


// MARK: - BranchCreationContext

/// Payload for "Create branch here" context menu — identifies the source branch.
struct BranchCreationContext: Identifiable {
    let id = UUID()
    let project: Project
    let fromBranch: String
}

// MARK: - ProjectFileHeaderView

/// Header row for a project in the Files section.
/// Extracted as a standalone View so `.contextMenu` gets proper SwiftUI identity.
private struct ProjectFileHeaderView: View {
    let project: Project
    let isActive: Bool
    let isExpanded: Bool
    let isCodeSpeakProject: Bool
    let remoteURL: String?
    let onTap: () -> Void
    let onSettings: () -> Void
    let onRevealInFinder: () -> Void
    let onOpenInBrowser: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button {
                onTap()
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "chevron.right")
                        .font(DSFont.iconSM)
                        .foregroundStyle(DSColor.textMuted)
                        .rotationEffect(isExpanded ? .degrees(90) : .zero)
                        .animation(.easeOut(duration: 0.15), value: isExpanded)

                    Image(systemName: isCodeSpeakProject ? "doc.text.magnifyingglass" : "folder.fill")
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(
                            isCodeSpeakProject
                                ? DSColor.agentCodeSpeak
                                : (isActive ? DSColor.accentPrimary : DSColor.gitModified)
                        )

                    Text(project.name)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(isActive ? DSColor.textPrimary : DSColor.textSecondary)
                        .fontWeight(isActive ? .medium : .regular)
                        .lineLimit(1)

                    Spacer()
                }
                .padding(.vertical, DSSpacing.xs)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button {
                onSettings()
            } label: {
                Image(systemName: "gearshape")
                    .font(DSFont.iconBase)
                    .foregroundStyle(DSColor.textMuted)
                    .frame(width: DSLayout.sidebarActionButtonSize, height: DSLayout.sidebarActionButtonSize)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.trailing, DSSpacing.xs)
            .help("Project settings")
        }
        .contentShape(Rectangle())
        .contextMenu {
            Button {
                onRevealInFinder()
            } label: {
                Label("Reveal in Finder", systemImage: "folder")
            }

            if remoteURL != nil {
                Button {
                    onOpenInBrowser()
                } label: {
                    Label("Open in GitHub/GitLab", systemImage: "safari")
                }
            }

            Divider()

            Button(role: .destructive) {
                onRemove()
            } label: {
                Label("Remove from VibeStudio", systemImage: "xmark.circle")
            }
        }
        .sidebarHover()
    }
}

// MARK: - ProjectGitHeaderView

/// Header row for a project in the Git section.
/// Extracted as a standalone View so `.contextMenu` gets proper SwiftUI identity.
struct ProjectGitHeaderView: View {
    let project: Project
    let isActive: Bool
    let isExpanded: Bool
    let remoteURL: String?
    let branch: String?
    let aheadCount: Int
    let behindCount: Int
    let onTap: () -> Void
    let onSettings: () -> Void
    let onRevealInFinder: () -> Void
    let onOpenInBrowser: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button {
                onTap()
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "chevron.right")
                        .font(DSFont.iconSM)
                        .foregroundStyle(DSColor.textMuted)
                        .rotationEffect(isExpanded ? .degrees(90) : .zero)
                        .animation(.easeOut(duration: 0.15), value: isExpanded)

                    Image(systemName: "arrow.triangle.branch")
                        .font(DSFont.iconBase)
                        .foregroundStyle(isActive ? DSColor.accentPrimary : DSColor.textSecondary)

                    Text(project.name)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(isActive ? DSColor.textPrimary : DSColor.textSecondary)
                        .fontWeight(isActive ? .medium : .regular)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    Spacer()

                    if let b = branch, !b.isEmpty {
                        HStack(spacing: DSSpacing.xxs) {
                            Text(b)
                                .font(DSFont.sidebarItemSmall)
                                .foregroundStyle(DSColor.textMuted)
                                .lineLimit(1)
                                .truncationMode(.middle)

                            if aheadCount > 0 {
                                HStack(spacing: DSSpacing.xxs) {
                                    Image(systemName: "arrow.up").font(DSFont.iconXS)
                                    Text("\(aheadCount)").font(DSFont.iconMD)
                                }
                                .foregroundStyle(DSColor.gitAdded)
                            }

                            if behindCount > 0 {
                                HStack(spacing: DSSpacing.xxs) {
                                    Image(systemName: "arrow.down").font(DSFont.iconXS)
                                    Text("\(behindCount)").font(DSFont.iconMD)
                                }
                                .foregroundStyle(DSColor.gitDeleted)
                            }
                        }
                    }
                }
                .padding(.vertical, DSSpacing.xs)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button {
                onSettings()
            } label: {
                Image(systemName: "gearshape")
                    .font(DSFont.iconBase)
                    .foregroundStyle(DSColor.textMuted)
                    .frame(width: DSLayout.sidebarActionButtonSize, height: DSLayout.sidebarActionButtonSize)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.leading, DSSpacing.xs)
        }
        .contentShape(Rectangle())
        .contextMenu {
            Button {
                onRevealInFinder()
            } label: {
                Label("Reveal in Finder", systemImage: "folder")
            }

            if remoteURL != nil {
                Button {
                    onOpenInBrowser()
                } label: {
                    Label("Open in GitHub/GitLab", systemImage: "safari")
                }
            }

            Divider()

            Button(role: .destructive) {
                onRemove()
            } label: {
                Label("Remove from VibeStudio", systemImage: "xmark.circle")
            }
        }
        .sidebarHover()
    }
}

// MARK: - SidebarView

struct SidebarView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.gitService) private var gitService
    @Environment(\.aiCommitService) private var aiCommitService
    @Environment(\.codeSpeak) private var codeSpeak

    @State private var activeSection: SidebarSection = .files
    @State private var expandedProjects: Set<UUID> = []
    @State private var showFileImporter = false
    @State private var showAddProjectPopover = false
    @State private var showCreateNewSheet = false

    // Remote modal
    @State private var projectForRemoteModal: Project?

    // Create branch sheet (no start point -- from current HEAD)
    @State private var projectForCreateBranch: Project?

    // Create branch from a specific branch (context menu "Create branch here")
    @State private var branchCreationContext: BranchCreationContext?

    // Project settings sheet
    @State private var projectForSettings: Project?

    // File viewer sheet
    @State private var fileToPreview: ViewedFile?

    // Context menu: project pending removal confirmation
    @State private var projectToRemove: Project?

    // Git ViewModel -- lazily initialized with environment services
    @State private var gitVM: GitSidebarViewModel?

    /// Projects that are NOT CodeSpeak projects — shown in the regular sidebar.
    private var regularProjects: [Project] {
        projectManager.projects.filter { !codeSpeak.isCodeSpeakProject($0.id) }
    }

    /// Resolve or create the git view model, ensuring environment services are injected.
    private var vm: GitSidebarViewModel {
        if let existing = gitVM { return existing }
        let created = GitSidebarViewModel(gitService: gitService, aiCommitService: aiCommitService)
        // Deferred assignment: Task { @MainActor } schedules the state mutation
        // after the current body evaluation cycle, satisfying SwiftUI's invariant.
        Task { @MainActor in gitVM = created }
        return created
    }

    var body: some View {
        HStack(spacing: 0) {
            iconStrip

            Rectangle()
                .fill(DSColor.borderDefault)
                .frame(width: 1)

            contentPanel
        }
        .frame(maxHeight: .infinity)
        .background(DSColor.surfaceRaised)
        .overlay(alignment: .trailing) {
            Rectangle()
                .fill(DSColor.borderDefault)
                .frame(width: 1)
        }
        .onAppear {
            if gitVM == nil {
                gitVM = GitSidebarViewModel(gitService: gitService, aiCommitService: aiCommitService)
            }
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                do {
                    let project = try projectManager.addProject(at: url)
                    projectManager.activeProjectId = project.id
                } catch {
                    Logger.ui.error("Failed to add project: \(error.localizedDescription, privacy: .public)")
                }
            }
        }
        .sheet(item: $projectForSettings) { project in
            ProjectSettingsSheet(project: project)
        }
        .sheet(item: $projectForRemoteModal) { project in
            GitRemoteSetupSheet(project: project)
        }
        .sheet(item: $projectForCreateBranch) { project in
            CreateBranchSheet(project: project) {
                Task { await vm.loadGitInfo(for: project) }
            }
        }
        .sheet(item: $branchCreationContext) { ctx in
            CreateBranchSheet(project: ctx.project, fromBranch: ctx.fromBranch) {
                Task { await vm.loadGitInfo(for: ctx.project) }
            }
        }
        .sheet(item: $fileToPreview) { viewedFile in
            if let project = projectManager.projects.first(where: { $0.id == projectManager.activeProjectId }) {
                FileViewerSheet(initialFile: viewedFile, projectPath: project.path)
            } else {
                FileViewerSheet(initialFile: viewedFile, projectPath: URL(fileURLWithPath: NSHomeDirectory()))
            }
        }
        .alert("Git Operation Failed", isPresented: Binding(
            get: { vm.checkoutErrorMessage != nil },
            set: { if !$0 { vm.checkoutErrorMessage = nil } }
        )) {
            Button("OK") { vm.checkoutErrorMessage = nil }
        } message: {
            Text(vm.checkoutErrorMessage ?? "")
        }
        .alert("Send diff to Claude?", isPresented: Binding(
            get: { vm.showAIDiffWarning },
            set: { vm.showAIDiffWarning = $0 }
        )) {
            Button("Cancel", role: .cancel) {
                vm.pendingAIDiffText = nil
                vm.pendingAIDiffProject = nil
            }
            Button("Proceed") {
                guard let diff = vm.pendingAIDiffText, let project = vm.pendingAIDiffProject else { return }
                vm.pendingAIDiffText = nil
                vm.pendingAIDiffProject = nil
                Task { await vm.sendAIDiff(diff, for: project) }
            }
        } message: {
            Text("This will send your diff to the Anthropic API. Review it to ensure no secrets or sensitive data are included.")
        }
        .alert("Remove Project", isPresented: Binding(
            get: { projectToRemove != nil },
            set: { if !$0 { projectToRemove = nil } }
        )) {
            Button("Cancel", role: .cancel) {
                projectToRemove = nil
            }
            Button("Remove", role: .destructive) {
                if let project = projectToRemove {
                    let id = project.id
                    try? projectManager.removeProject(id)
                    vm.cleanupProject(id)
                    projectToRemove = nil
                }
            }
        } message: {
            Text("Remove \"\(projectToRemove?.name ?? "")\" from VibeStudio? Project files on disk will not be deleted.")
        }
        .sheet(isPresented: $showCreateNewSheet) {
            CreateNewProjectSheet()
        }
    }

    // MARK: - Icon Strip

    private var iconStrip: some View {
        VStack(spacing: DSSpacing.sm) {
            iconButton(section: .files, symbol: "folder.fill")
            iconButton(section: .git, symbol: "arrow.triangle.branch")
            iconButton(section: .specs, symbol: "doc.text.magnifyingglass")
            Spacer()

            Button {
                showAddProjectPopover = true
            } label: {
                Image(systemName: "plus")
                    .font(DSFont.iconLG)
                    .foregroundStyle(DSColor.textMuted)
                    .frame(width: DSLayout.iconStripButtonSize, height: DSLayout.iconStripButtonSize)
                    .cornerRadius(DSRadius.sm)
            }
            .buttonStyle(.plain)
            .padding(.bottom, DSSpacing.sm)
            .keyboardShortcut("t", modifiers: .command)
            .popover(isPresented: $showAddProjectPopover, arrowEdge: .trailing) {
                AddProjectPopover(
                    onOpenFolder: {
                        showAddProjectPopover = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            showFileImporter = true
                        }
                    },
                    onCreateNew: {
                        showAddProjectPopover = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            showCreateNewSheet = true
                        }
                    }
                )
            }
        }
        .padding(.top, DSSpacing.sm)
        .frame(width: DSLayout.iconStripWidth)
    }

    private func iconButton(section: SidebarSection, symbol: String) -> some View {
        let isActive = activeSection == section
        return Button {
            activeSection = section
        } label: {
            Image(systemName: symbol)
                .font(DSFont.iconLG)
                .foregroundStyle(isActive ? DSColor.accentPrimary : DSColor.textMuted)
                .frame(width: DSLayout.iconStripButtonSize, height: DSLayout.iconStripButtonSize)
                .background(isActive ? DSColor.surfaceOverlay : Color.clear)
                .cornerRadius(DSRadius.sm)
        }
        .buttonStyle(.plain)
        .sidebarHover(cornerRadius: DSRadius.sm)
    }

    // MARK: - Content Panel

    private var contentPanel: some View {
        Group {
            if activeSection == .files {
                multiProjectFileTree()
            } else if activeSection == .specs {
                SpecsPanelView()
            } else {
                if regularProjects.isEmpty {
                    noProjectView()
                } else {
                    multiProjectGitView()
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Multi-Project File Tree

    private func multiProjectFileTree() -> some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(regularProjects) { project in
                    projectSection(project: project)
                }
            }
            .padding(.horizontal, DSLayout.sidebarHorizontalPadding)
        }
        .onAppear {
            if let id = projectManager.activeProjectId {
                expandedProjects.insert(id)
            }
        }
        .task {
            for project in regularProjects {
                await vm.loadRemoteURL(for: project)
            }
        }
    }

    private func projectSection(project: Project) -> some View {
        let isActive = project.id == projectManager.activeProjectId
        let isExpanded = expandedProjects.contains(project.id)

        return VStack(alignment: .leading, spacing: 0) {
            ProjectFileHeaderView(
                project: project,
                isActive: isActive,
                isExpanded: isExpanded,
                isCodeSpeakProject: codeSpeak.isCodeSpeakProject(project.id),
                remoteURL: vm.projectRemoteURLs[project.id],
                onTap: {
                    if isExpanded {
                        expandedProjects.remove(project.id)
                    } else {
                        expandedProjects.insert(project.id)
                    }
                    projectManager.activeProjectId = project.id
                },
                onSettings: { projectForSettings = project },
                onRevealInFinder: {
                    NSWorkspace.shared.selectFile(nil, inFileViewerRootedAtPath: project.path.path)
                },
                onOpenInBrowser: { Task { await vm.openInRemote(project: project) } },
                onRemove: { projectToRemove = project }
            )

            if isExpanded {
                FileTreeView(
                    projectPath: project.path,
                    showSectionHeader: false,
                    onFileDoubleTapped: { entry in
                        fileToPreview = ViewedFile(entry: entry)
                    }
                )
            }
        }
    }

    // MARK: - Multi-Project Git View

    private func multiProjectGitView() -> some View {
        let activeProject = regularProjects
            .first { $0.id == projectManager.activeProjectId }
        let showCommitPanel = activeProject.map { !vm.nonGitProjects.contains($0.id) } ?? false

        return VStack(spacing: 0) {
            ScrollView(.vertical, showsIndicators: true) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text("GIT")
                            .font(DSFont.sidebarSection)
                            .foregroundStyle(DSColor.textSecondary)
                        Spacer()
                        Button {
                            Task { await vm.refreshAllGitInfo(projects: regularProjects) }
                        } label: {
                            Image(systemName: "arrow.clockwise")
                                .font(DSFont.iconBase)
                                .foregroundStyle(DSColor.textMuted)
                        }
                        .buttonStyle(.plain)
                    }
                    .frame(height: DSLayout.gitSectionHeaderHeight)

                    ForEach(regularProjects) { project in
                        GitProjectSectionView(
                            project: project,
                            isActiveProject: project.id == projectManager.activeProjectId,
                            gitSidebarVM: vm,
                            projectForRemoteModal: $projectForRemoteModal,
                            projectToRemove: $projectToRemove,
                            projectForCreateBranch: $projectForCreateBranch,
                            branchCreationContext: $branchCreationContext,
                            onSetActiveProject: { projectManager.activeProjectId = $0 }
                        )
                    }
                }
                .padding(.horizontal, DSLayout.sidebarHorizontalPadding)
            }

            // Commit panel pinned to bottom, applies to the active project
            if showCommitPanel, let project = activeProject {
                Rectangle()
                    .fill(DSColor.borderDefault)
                    .frame(height: 1)
                CommitPanelView(project: project, gitSidebarVM: vm)
            }
        }
        .task {
            await vm.refreshAllGitInfo(projects: regularProjects)
        }
    }


    // MARK: - No Project View

    private func noProjectView() -> some View {
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
