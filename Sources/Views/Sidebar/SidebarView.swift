// MARK: - SidebarView
// Left sidebar with icon strip and switchable Files/Git sections.
// Background: ultraThinMaterial, right border 1pt.
// macOS 14+, Swift 5.10

import AppKit
import OSLog
import SwiftUI
import UniformTypeIdentifiers

// MARK: - SidebarHoverModifier

/// Adds a subtle background highlight on mouse hover for sidebar interactive rows.
private struct SidebarHoverModifier: ViewModifier {
    let cornerRadius: CGFloat

    @State private var isHovering = false

    init(cornerRadius: CGFloat = 4) {
        self.cornerRadius = cornerRadius
    }

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(isHovering ? DSColor.textPrimary.opacity(0.07) : Color.clear)
            )
            .onHover { hovering in
                isHovering = hovering
            }
    }
}

extension View {
    /// Applies a sidebar hover highlight effect.
    fileprivate func sidebarHover(cornerRadius: CGFloat = 4) -> some View {
        modifier(SidebarHoverModifier(cornerRadius: cornerRadius))
    }
}

// MARK: - SidebarSection

/// Sidebar content sections selectable via the icon strip.
enum SidebarSection {
    case files
    case git
}

// MARK: - BranchCreationContext

/// Payload for "Create branch here" context menu — identifies the source branch.
private struct BranchCreationContext: Identifiable {
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
                        .font(.system(size: 9))
                        .foregroundStyle(DSColor.textMuted)
                        .rotationEffect(isExpanded ? .degrees(90) : .zero)
                        .animation(.easeOut(duration: 0.15), value: isExpanded)

                    Image(systemName: "folder.fill")
                        .font(.system(size: 13))
                        .foregroundStyle(isActive ? DSColor.accentPrimary : DSColor.gitModified)

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
                    .font(.system(size: 11))
                    .foregroundStyle(DSColor.textMuted)
                    .frame(width: 20, height: 20)
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
private struct ProjectGitHeaderView: View {
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
                        .font(.system(size: 9))
                        .foregroundStyle(DSColor.textMuted)
                        .rotationEffect(isExpanded ? .degrees(90) : .zero)
                        .animation(.easeOut(duration: 0.15), value: isExpanded)

                    Image(systemName: "arrow.triangle.branch")
                        .font(.system(size: 11))
                        .foregroundStyle(isActive ? DSColor.accentPrimary : DSColor.textSecondary)

                    Text(project.name)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(isActive ? DSColor.textPrimary : DSColor.textSecondary)
                        .fontWeight(isActive ? .medium : .regular)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    Spacer()

                    if let b = branch, !b.isEmpty {
                        HStack(spacing: 3) {
                            Text(b)
                                .font(DSFont.sidebarItemSmall)
                                .foregroundStyle(DSColor.textMuted)
                                .lineLimit(1)
                                .truncationMode(.middle)

                            if aheadCount > 0 {
                                HStack(spacing: 1) {
                                    Image(systemName: "arrow.up").font(.system(size: 8))
                                    Text("\(aheadCount)").font(.system(size: 10))
                                }
                                .foregroundStyle(DSColor.gitAdded)
                            }

                            if behindCount > 0 {
                                HStack(spacing: 1) {
                                    Image(systemName: "arrow.down").font(.system(size: 8))
                                    Text("\(behindCount)").font(.system(size: 10))
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
                    .font(.system(size: 11))
                    .foregroundStyle(DSColor.textMuted)
                    .frame(width: 20, height: 20)
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
        .background(.ultraThinMaterial)
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
                    try? projectManager.removeProject(project.id)
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
            Spacer()

            Button {
                showAddProjectPopover = true
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 14))
                    .foregroundStyle(DSColor.textMuted)
                    .frame(width: 24, height: 24)
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
        .frame(width: 32)
    }

    private func iconButton(section: SidebarSection, symbol: String) -> some View {
        let isActive = activeSection == section
        return Button {
            activeSection = section
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 14))
                .foregroundStyle(isActive ? DSColor.accentPrimary : DSColor.textMuted)
                .frame(width: 24, height: 24)
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
            } else {
                if projectManager.projects.isEmpty {
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
                ForEach(projectManager.projects) { project in
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
            for project in projectManager.projects {
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
        let activeProject = projectManager.projects
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
                            Task { await vm.refreshAllGitInfo(projects: projectManager.projects) }
                        } label: {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 11))
                                .foregroundStyle(DSColor.textMuted)
                        }
                        .buttonStyle(.plain)
                    }
                    .frame(height: DSLayout.gitSectionHeaderHeight)

                    ForEach(projectManager.projects) { project in
                        gitProjectSection(project: project)
                    }
                }
                .padding(.horizontal, DSLayout.sidebarHorizontalPadding)
            }

            // Commit panel pinned to bottom, applies to the active project
            if showCommitPanel, let project = activeProject {
                Rectangle()
                    .fill(DSColor.borderDefault)
                    .frame(height: 1)
                commitPanel(project: project)
            }
        }
        .task {
            await vm.refreshAllGitInfo(projects: projectManager.projects)
        }
    }

    /// Project row in git panel. Header shows name + current branch + ahead/behind + gear.
    /// Expanded: local branches list, or "Initialize Git" if not a repo.
    private func gitProjectSection(project: Project) -> some View {
        let isActive = project.id == projectManager.activeProjectId
        let isExpanded = vm.gitExpandedProjects.contains(project.id)
        let gitStatus = vm.projectGitStatuses[project.id]
        let isNonGit = vm.nonGitProjects.contains(project.id)

        return VStack(alignment: .leading, spacing: 0) {
            ProjectGitHeaderView(
                project: project,
                isActive: isActive,
                isExpanded: isExpanded,
                remoteURL: vm.projectRemoteURLs[project.id],
                branch: gitStatus?.branch,
                aheadCount: gitStatus?.aheadCount ?? 0,
                behindCount: gitStatus?.behindCount ?? 0,
                onTap: {
                    if isExpanded {
                        vm.gitExpandedProjects.remove(project.id)
                    } else {
                        vm.gitExpandedProjects.insert(project.id)
                        Task { await vm.loadGitInfo(for: project) }
                    }
                    projectManager.activeProjectId = project.id
                },
                onSettings: { projectForRemoteModal = project },
                onRevealInFinder: {
                    NSWorkspace.shared.selectFile(nil, inFileViewerRootedAtPath: project.path.path)
                },
                onOpenInBrowser: { Task { await vm.openInRemote(project: project) } },
                onRemove: { projectToRemove = project }
            )

            // Expanded content
            if isExpanded {
                if isNonGit {
                    // Not a git repository — show init button
                    notARepositoryContent(project: project)
                } else if let branches = vm.projectBranches[project.id] {
                    // Branch list
                    branchListContent(
                        branches: branches,
                        currentBranch: gitStatus?.branch ?? "",
                        aheadCount: gitStatus?.aheadCount ?? 0,
                        behindCount: gitStatus?.behindCount ?? 0,
                        project: project,
                        branchError: vm.projectBranchErrors[project.id]
                    )
                } else {
                    // Loading
                    HStack {
                        ProgressView().scaleEffect(0.6)
                        Text("Loading…")
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textMuted)
                    }
                    .padding(.leading, DSSpacing.md)
                    .padding(.vertical, DSSpacing.xs)
                }
            }
        }
        .padding(.vertical, 2)
    }

    // MARK: - Expanded: Not a Repository

    private func notARepositoryContent(project: Project) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Not a git repository")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)

            Button {
                Task { await vm.initRepository(for: project) }
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "arrow.triangle.branch")
                        .font(.system(size: 11))
                    Text("Initialize Git")
                        .font(DSFont.buttonLabel)
                }
                .foregroundStyle(DSColor.buttonPrimaryText)
                .frame(maxWidth: .infinity)
                .frame(height: DSLayout.gitButtonHeight)
                .background(DSColor.buttonPrimaryBg, in: RoundedRectangle(cornerRadius: DSRadius.md))
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, DSSpacing.md)
        .padding(.vertical, DSSpacing.xs)
        .padding(.trailing, DSSpacing.xs)
    }

    // MARK: - Expanded: Branch List

    private func branchListContent(
        branches: [GitBranch],
        currentBranch: String,
        aheadCount: Int,
        behindCount: Int,
        project: Project,
        branchError: String? = nil
    ) -> some View {
        let localBranches = branches.filter { !$0.isRemote }
        let remoteBranches = branches.filter { $0.isRemote && !$0.name.hasSuffix("/HEAD") }
        let remoteUnavailable = vm.remoteUnavailableProjects.contains(project.id)

        return VStack(alignment: .leading, spacing: 0) {
            if localBranches.isEmpty && remoteBranches.isEmpty {
                // Empty state: error or genuinely no branches
                if let error = branchError {
                    VStack(alignment: .leading, spacing: DSSpacing.xs) {
                        Text("Could not load branches")
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textMuted)
                        Text(error)
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.gitDeleted)
                            .lineLimit(2)
                    }
                    .padding(.leading, DSSpacing.md)
                    .padding(.vertical, DSSpacing.xs)
                } else {
                    HStack(spacing: DSSpacing.sm) {
                        Text("No local branches")
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textMuted)
                        Button("New branch") {
                            projectForCreateBranch = project
                        }
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.accentPrimary)
                        .buttonStyle(.plain)
                    }
                    .padding(.leading, DSSpacing.md)
                    .padding(.vertical, DSSpacing.xs)
                }
            }

            // Local branches — double-click to checkout
            ForEach(localBranches) { branch in
                let isCurrent = branch.name == currentBranch
                let isOperating = vm.branchOperationsInProgress.contains("\(project.id):\(branch.name)")

                HStack(spacing: DSSpacing.xs) {
                    // Current / loading indicator
                    if isOperating {
                        ProgressView()
                            .scaleEffect(0.5)
                            .frame(width: 12, height: 12)
                    } else {
                        Image(systemName: isCurrent ? "checkmark" : "circle")
                            .font(.system(size: isCurrent ? 9 : 6, weight: isCurrent ? .semibold : .regular))
                            .foregroundStyle(isCurrent ? DSColor.gitAdded : DSColor.textMuted.opacity(0.5))
                            .frame(width: 12)
                    }

                    Image(systemName: "arrow.triangle.branch")
                        .font(.system(size: 10))
                        .foregroundStyle(isCurrent ? DSColor.accentPrimary : DSColor.textSecondary)

                    Text(branch.name)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(isCurrent ? DSColor.textPrimary : DSColor.textSecondary)
                        .fontWeight(isCurrent ? .medium : .regular)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    Spacer()

                    // Ahead/behind only for current branch
                    if isCurrent && (aheadCount > 0 || behindCount > 0) {
                        HStack(spacing: 3) {
                            if aheadCount > 0 {
                                HStack(spacing: 1) {
                                    Image(systemName: "arrow.up").font(.system(size: 8))
                                    Text("\(aheadCount)").font(.system(size: 10))
                                }.foregroundStyle(DSColor.gitAdded)
                            }
                            if behindCount > 0 {
                                HStack(spacing: 1) {
                                    Image(systemName: "arrow.down").font(.system(size: 8))
                                    Text("\(behindCount)").font(.system(size: 10))
                                }.foregroundStyle(DSColor.gitDeleted)
                            }
                        }
                    }
                }
                .padding(.vertical, 3)
                .contentShape(Rectangle())
                .frame(height: DSLayout.gitFileRowHeight)
                .sidebarHover()
                .onTapGesture(count: 2) {
                    guard !isCurrent else { return }
                    Task { await vm.checkout(branch: branch.name, project: project) }
                }
                .contextMenu {
                    Button {
                        Task { await vm.gitBranchPull(branch.name, isCurrent: isCurrent, project: project) }
                    } label: {
                        Label("Pull", systemImage: "arrow.down.circle")
                    }

                    Button {
                        Task { await vm.gitBranchPush(branch.name, project: project) }
                    } label: {
                        Label("Push", systemImage: "arrow.up.circle")
                    }

                    Divider()

                    Button {
                        branchCreationContext = BranchCreationContext(project: project, fromBranch: branch.name)
                    } label: {
                        Label("Create branch here", systemImage: "plus.circle")
                    }
                }
            }

            // New branch row (always visible below local branches)
            Button {
                projectForCreateBranch = project
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "plus")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(DSColor.accentPrimary)
                        .frame(width: 12)
                    Text("New branch")
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.accentPrimary)
                    Spacer()
                }
                .frame(height: DSLayout.gitFileRowHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .sidebarHover()

            // Remote section separator + content
            if remoteUnavailable || !remoteBranches.isEmpty {
                // Shared origin separator — rendered once
                HStack(spacing: DSSpacing.xs) {
                    Rectangle().fill(DSColor.borderSubtle).frame(height: 1)
                    Text("origin")
                        .font(.system(size: 10))
                        .foregroundStyle(DSColor.textMuted)
                        .fixedSize()
                    Rectangle().fill(DSColor.borderSubtle).frame(height: 1)
                }
                .padding(.vertical, DSSpacing.xs)

                if remoteUnavailable {
                    HStack(spacing: DSSpacing.xs) {
                        Image(systemName: "cloud.slash")
                            .font(.system(size: 9))
                            .foregroundStyle(DSColor.textMuted)
                            .frame(width: 12)
                        Text("unavailable")
                            .font(.system(size: 10))
                            .foregroundStyle(DSColor.textMuted)
                        Spacer()
                    }
                    .frame(height: 20)
                } else {
                    ForEach(remoteBranches) { branch in
                        HStack(spacing: DSSpacing.xs) {
                            Image(systemName: "cloud")
                                .font(.system(size: 9))
                                .foregroundStyle(DSColor.textMuted)
                                .frame(width: 12)

                            Text(branch.name.replacingOccurrences(of: "origin/", with: ""))
                                .font(DSFont.sidebarItemSmall)
                                .foregroundStyle(DSColor.textMuted)
                                .lineLimit(1)
                                .truncationMode(.tail)

                            Spacer()
                        }
                        .frame(height: 22)
                    }
                }
            }
        }
        .padding(.leading, DSSpacing.md)
        .padding(.trailing, DSSpacing.xs)
        .padding(.bottom, DSSpacing.xs)
    }

    // MARK: - Commit Panel

    @ViewBuilder
    private func commitPanel(project: Project) -> some View {
        let gitStatus = vm.projectGitStatuses[project.id]
        let hasChanges = !(gitStatus?.stagedFiles.isEmpty ?? true)
            || !(gitStatus?.unstagedFiles.isEmpty ?? true)
            || !(gitStatus?.untrackedFiles.isEmpty ?? true)
        let isGenerating = vm.generatingAIProjects.contains(project.id)
        let isCommitting = vm.committingProjects.contains(project.id)
        let summaryText = vm.commitSummaries[project.id] ?? ""
        let charCount = summaryText.count
        let isOverLimit = charCount > 72

        VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Rectangle()
                .fill(DSColor.borderSubtle)
                .frame(height: 1)
                .padding(.vertical, 2)

            // Input area
            VStack(alignment: .leading, spacing: 0) {
                // Summary + AI button
                HStack(alignment: .top, spacing: DSSpacing.xs) {
                    TextField(
                        "Commit summary",
                        text: Binding(
                            get: { vm.commitSummaries[project.id] ?? "" },
                            set: { vm.commitSummaries[project.id] = $0 }
                        ),
                        axis: .vertical
                    )
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .textFieldStyle(.plain)
                    .lineLimit(1...3)
                    .disabled(!hasChanges || isCommitting)
                    .opacity(hasChanges ? 1 : 0.35)

                    Button {
                        Task { await vm.generateAICommitMessage(for: project) }
                    } label: {
                        if isGenerating {
                            ProgressView().scaleEffect(0.5).frame(width: 20, height: 20)
                        } else {
                            Image(systemName: "sparkles")
                                .font(.system(size: 12))
                                .foregroundStyle(hasChanges ? DSColor.accentPrimary : DSColor.textMuted)
                                .frame(width: 20, height: 20)
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(!hasChanges || isGenerating || isCommitting)
                    .help("Generate commit message with AI")
                }

                // Char count (only when typing)
                if !summaryText.isEmpty {
                    Text("\(charCount)/72")
                        .font(.system(size: 9))
                        .foregroundStyle(isOverLimit ? DSColor.gitDeleted : DSColor.textMuted)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .padding(.top, 2)
                }

                Rectangle()
                    .fill(DSColor.borderSubtle)
                    .frame(height: 1)
                    .padding(.vertical, DSSpacing.xs)

                // Description
                TextField(
                    "Description (optional)",
                    text: Binding(
                        get: { vm.commitDescriptions[project.id] ?? "" },
                        set: { vm.commitDescriptions[project.id] = $0 }
                    ),
                    axis: .vertical
                )
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textSecondary)
                .textFieldStyle(.plain)
                .lineLimit(1...5)
                .disabled(!hasChanges || isCommitting)
                .opacity(hasChanges ? 1 : 0.35)
            }
            .padding(DSSpacing.sm)
            .background(DSColor.surfaceInput)
            .clipShape(RoundedRectangle(cornerRadius: DSRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: DSRadius.md)
                    .stroke(DSColor.borderDefault, lineWidth: 1)
            )

            // Inline error
            if let error = vm.commitPanelErrors[project.id] {
                Text(error)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
                    .lineLimit(2)
            }

            // Commit button
            Button {
                Task { await vm.performCommit(for: project) }
            } label: {
                ZStack {
                    if isCommitting {
                        ProgressView().scaleEffect(0.65)
                    } else {
                        HStack(spacing: DSSpacing.xs) {
                            Image(systemName: "arrow.up.circle.fill")
                                .font(.system(size: 11))
                            Text("Commit All Changes")
                                .font(DSFont.buttonLabel)
                        }
                    }
                }
                .foregroundStyle(hasChanges ? DSColor.buttonPrimaryText : DSColor.textMuted.opacity(0.5))
                .frame(maxWidth: .infinity)
                .frame(height: DSLayout.gitButtonHeight)
                .background(
                    hasChanges
                        ? DSColor.buttonPrimaryBg.opacity(isCommitting ? 0.6 : 1.0)
                        : DSColor.surfaceOverlay,
                    in: RoundedRectangle(cornerRadius: DSRadius.md)
                )
            }
            .buttonStyle(.plain)
            .disabled(!hasChanges || isCommitting)
        }
        .padding(.leading, DSSpacing.md)
        .padding(.trailing, DSSpacing.xs)
        .padding(.bottom, DSSpacing.sm)
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
