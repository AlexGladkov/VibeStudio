// MARK: - GitProjectSectionView
// A single project row in the git sidebar panel.
// Shows header (name + branch + ahead/behind + gear) and expanded content
// (branch list or "Initialize Git").
// Extracted from SidebarView to reduce complexity.
// macOS 14+, Swift 5.10

import AppKit
import SwiftUI

/// Project row in the git panel. Header shows name, current branch,
/// ahead/behind counts, and settings gear. When expanded, shows local
/// branches list or "Initialize Git" if not a git repository.
struct GitProjectSectionView: View {
    let project: Project
    let isActiveProject: Bool
    let gitSidebarVM: GitSidebarViewModel

    /// Binding to trigger the remote setup modal sheet.
    @Binding var projectForRemoteModal: Project?

    /// Binding to trigger the remove-project confirmation alert.
    @Binding var projectToRemove: Project?

    /// Binding to trigger "Create branch from HEAD" sheet.
    @Binding var projectForCreateBranch: Project?

    /// Binding to trigger "Create branch from specific branch" sheet.
    @Binding var branchCreationContext: BranchCreationContext?

    /// Callback to set the active project in the parent's project manager.
    let onSetActiveProject: (UUID) -> Void

    var body: some View {
        let isExpanded = gitSidebarVM.gitExpandedProjects.contains(project.id)
        let gitStatus = gitSidebarVM.projectGitStatuses[project.id]
        let isNonGit = gitSidebarVM.nonGitProjects.contains(project.id)

        VStack(alignment: .leading, spacing: 0) {
            ProjectGitHeaderView(
                project: project,
                isActive: isActiveProject,
                isExpanded: isExpanded,
                remoteURL: gitSidebarVM.projectRemoteURLs[project.id],
                branch: gitStatus?.branch,
                aheadCount: gitStatus?.aheadCount ?? 0,
                behindCount: gitStatus?.behindCount ?? 0,
                onTap: {
                    if isExpanded {
                        gitSidebarVM.gitExpandedProjects.remove(project.id)
                    } else {
                        gitSidebarVM.gitExpandedProjects.insert(project.id)
                        Task { await gitSidebarVM.loadGitInfo(for: project) }
                    }
                    onSetActiveProject(project.id)
                },
                onSettings: { projectForRemoteModal = project },
                onRevealInFinder: {
                    NSWorkspace.shared.selectFile(nil, inFileViewerRootedAtPath: project.path.path)
                },
                onOpenInBrowser: { Task { await gitSidebarVM.openInRemote(project: project) } },
                onRemove: { projectToRemove = project }
            )

            // Expanded content
            if isExpanded {
                if isNonGit {
                    // Not a git repository -- show init button
                    notARepositoryContent(project: project)
                } else if let branches = gitSidebarVM.projectBranches[project.id] {
                    // Branch list
                    BranchListView(
                        branches: branches,
                        currentBranch: gitStatus?.branch ?? "",
                        aheadCount: gitStatus?.aheadCount ?? 0,
                        behindCount: gitStatus?.behindCount ?? 0,
                        project: project,
                        branchError: gitSidebarVM.projectBranchErrors[project.id],
                        gitSidebarVM: gitSidebarVM,
                        projectForCreateBranch: $projectForCreateBranch,
                        branchCreationContext: $branchCreationContext
                    )
                } else {
                    // Loading
                    HStack {
                        ProgressView().scaleEffect(0.6)
                        Text("Loading...")
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

    // MARK: - Not a Repository Content

    private func notARepositoryContent(project: Project) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            Text("Not a git repository")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)

            Button {
                Task { await gitSidebarVM.initRepository(for: project) }
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
}
