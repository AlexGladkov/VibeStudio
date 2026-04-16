// MARK: - BranchListView
// Expanded branch list for a project in the git sidebar section.
// Shows local branches (double-click to checkout), new branch row,
// and remote branches section.
// Extracted from SidebarView to reduce complexity.
// macOS 14+, Swift 5.10

import SwiftUI

/// Displays a project's local and remote branches with context menus
/// for pull, push, and branch creation.
struct BranchListView: View {
    let branches: [GitBranch]
    let currentBranch: String
    let aheadCount: Int
    let behindCount: Int
    let project: Project
    let branchError: String?
    let gitSidebarVM: GitSidebarViewModel

    /// Binding to trigger "Create branch from HEAD" sheet in the parent.
    @Binding var projectForCreateBranch: Project?

    /// Binding to trigger "Create branch from specific branch" sheet in the parent.
    @Binding var branchCreationContext: BranchCreationContext?

    var body: some View {
        let localBranches = branches.filter { !$0.isRemote }
        let remoteBranches = branches.filter { $0.isRemote && !$0.name.hasSuffix("/HEAD") }
        let remoteUnavailable = gitSidebarVM.remoteUnavailableProjects.contains(project.id)

        VStack(alignment: .leading, spacing: 0) {
            if localBranches.isEmpty && remoteBranches.isEmpty {
                emptyState(branchError: branchError)
            }

            // Local branches -- double-click to checkout
            ForEach(localBranches) { branch in
                localBranchRow(branch: branch)
            }

            // New branch row (always visible below local branches)
            newBranchRow()

            // Remote section separator + content
            if remoteUnavailable || !remoteBranches.isEmpty {
                remoteSectionSeparator()

                if remoteUnavailable {
                    remoteUnavailableRow()
                } else {
                    ForEach(remoteBranches) { branch in
                        remoteBranchRow(branch: branch)
                    }
                }
            }
        }
        .padding(.leading, DSSpacing.md)
        .padding(.trailing, DSSpacing.xs)
        .padding(.bottom, DSSpacing.xs)
    }

    // MARK: - Empty State

    @ViewBuilder
    private func emptyState(branchError: String?) -> some View {
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

    // MARK: - Local Branch Row

    private func localBranchRow(branch: GitBranch) -> some View {
        let isCurrent = branch.name == currentBranch
        let isOperating = gitSidebarVM.branchOperationsInProgress.contains("\(project.id):\(branch.name)")

        return HStack(spacing: DSSpacing.xs) {
            // Current / loading indicator
            if isOperating {
                ProgressView()
                    .scaleEffect(0.5)
                    .frame(width: DSSpacing.md, height: DSSpacing.md)
            } else {
                Image(systemName: isCurrent ? "checkmark" : "circle")
                    // TODO: sub-grid size — 6pt has no DS token yet
                    .font(.system(size: isCurrent ? 9 : 6, weight: isCurrent ? .semibold : .regular))
                    .foregroundStyle(isCurrent ? DSColor.gitAdded : DSColor.textDisabled)
                    .frame(width: DSSpacing.md)
            }

            Image(systemName: "arrow.triangle.branch")
                .font(DSFont.iconMD)
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
                HStack(spacing: DSSpacing.xxs) {
                    if aheadCount > 0 {
                        HStack(spacing: DSSpacing.xxs) {
                            Image(systemName: "arrow.up").font(DSFont.iconXS)
                            Text("\(aheadCount)").font(DSFont.iconMD)
                        }.foregroundStyle(DSColor.gitAdded)
                    }
                    if behindCount > 0 {
                        HStack(spacing: DSSpacing.xxs) {
                            Image(systemName: "arrow.down").font(DSFont.iconXS)
                            Text("\(behindCount)").font(DSFont.iconMD)
                        }.foregroundStyle(DSColor.gitDeleted)
                    }
                }
            }
        }
        .padding(.vertical, DSSpacing.xxs)
        .contentShape(Rectangle())
        .frame(height: DSLayout.gitFileRowHeight)
        .sidebarHover()
        .onTapGesture(count: 2) {
            guard !isCurrent else { return }
            Task { await gitSidebarVM.checkout(branch: branch.name, project: project) }
        }
        .contextMenu {
            Button {
                Task { await gitSidebarVM.gitBranchPull(branch.name, isCurrent: isCurrent, project: project) }
            } label: {
                Label("Pull", systemImage: "arrow.down.circle")
            }

            Button {
                Task { await gitSidebarVM.gitBranchPush(branch.name, project: project) }
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

    // MARK: - New Branch Row

    private func newBranchRow() -> some View {
        Button {
            projectForCreateBranch = project
        } label: {
            HStack(spacing: DSSpacing.xs) {
                Image(systemName: "plus")
                    .font(DSFont.badgeSmall)
                    .foregroundStyle(DSColor.accentPrimary)
                    .frame(width: DSSpacing.md)
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
    }

    // MARK: - Remote Section

    private func remoteSectionSeparator() -> some View {
        HStack(spacing: DSSpacing.xs) {
            Rectangle().fill(DSColor.borderSubtle).frame(height: 1)
            Text("origin")
                .font(DSFont.iconMD)
                .foregroundStyle(DSColor.textMuted)
                .fixedSize()
            Rectangle().fill(DSColor.borderSubtle).frame(height: 1)
        }
        .padding(.vertical, DSSpacing.xs)
    }

    private func remoteUnavailableRow() -> some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "cloud.slash")
                .font(DSFont.iconSM)
                .foregroundStyle(DSColor.textMuted)
                .frame(width: DSSpacing.md)
            Text("unavailable")
                .font(DSFont.iconMD)
                .foregroundStyle(DSColor.textMuted)
            Spacer()
        }
        .frame(height: DSLayout.gitFileRowHeight - 8)
    }

    private func remoteBranchRow(branch: GitBranch) -> some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "cloud")
                .font(DSFont.iconSM)
                .foregroundStyle(DSColor.textMuted)
                .frame(width: DSSpacing.md)

            Text(branch.name.replacingOccurrences(of: "origin/", with: ""))
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
                .lineLimit(1)
                .truncationMode(.tail)

            Spacer()
        }
        .frame(height: DSLayout.gitFileRowHeight - 6)
    }
}
