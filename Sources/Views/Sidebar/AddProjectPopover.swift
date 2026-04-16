// MARK: - AddProjectPopover
// Popover for the "+" button in the sidebar icon strip.
// Shows recent projects, "Open Folder...", and "Create New..." actions.
// macOS 14+, Swift 5.10

import OSLog
import SwiftUI

// MARK: - AddProjectPopover

struct AddProjectPopover: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.dismiss) private var dismiss

    /// Called when the user wants to open a folder via NSOpenPanel.
    let onOpenFolder: () -> Void

    /// Called when the user wants to create a new project. `nil` hides the button.
    let onCreateNew: (() -> Void)?

    @State private var vm: AddProjectViewModel?

    private var viewModel: AddProjectViewModel {
        if let existing = vm { return existing }
        let created = AddProjectViewModel(projectManager: projectManager)
        Task { @MainActor in vm = created }
        return created
    }

    var body: some View {
        let model = viewModel
        VStack(alignment: .leading, spacing: 0) {
            if projectManager.recentHistory.isEmpty {
                emptyState
            } else {
                recentsList(model: model)
            }

            if !projectManager.recentHistory.isEmpty {
                Divider()
                    .overlay(DSColor.borderSubtle)
                    .padding(.vertical, DSSpacing.xs)
            }

            actionButtons
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
        .frame(minWidth: 280, idealWidth: DSLayout.addProjectPopoverWidth, maxWidth: 360)
        .background(DSColor.surfaceOverlay)
        .onAppear {
            if vm == nil {
                vm = AddProjectViewModel(projectManager: projectManager)
            }
        }
    }

    // MARK: - Empty State

    @ViewBuilder
    private var emptyState: some View {
        VStack(spacing: DSSpacing.sm) {
            Image(systemName: "folder.badge.questionmark")
                .font(DSFont.emptyStateIcon)
                .foregroundStyle(DSColor.textMuted)

            Text("No recent projects")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textSecondary)

            Text("Open a folder or create a new project")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, DSSpacing.md)

        Divider()
            .overlay(DSColor.borderSubtle)
            .padding(.vertical, DSSpacing.xs)
    }

    // MARK: - Recents List

    private func recentsList(model: AddProjectViewModel) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("RECENT")
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textSecondary)
                .padding(.bottom, DSSpacing.xs)

            ForEach(projectManager.recentHistory) { project in
                RecentRow(project: project) {
                    if model.openRecentProject(project) {
                        dismiss()
                    }
                }
            }

            if let err = model.openError {
                Text(err)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
                    .lineLimit(2)
                    .padding(.top, DSSpacing.xs)
            }
        }
    }

    // MARK: - Action Buttons

    private var actionButtons: some View {
        VStack(alignment: .leading, spacing: 0) {
            ActionRow(
                title: "Open Folder...",
                systemImage: "folder.badge.plus"
            ) {
                onOpenFolder()
                dismiss()
            }

            if let onCreateNew {
                ActionRow(
                    title: "Create New...",
                    systemImage: "plus.square"
                ) {
                    onCreateNew()
                    dismiss()
                }
            }
        }
    }
}

// MARK: - RecentRow

/// A single row displaying a recent project: folder icon, name, path, and relative date.
private struct RecentRow: View {

    let project: Project
    let onTap: () -> Void

    @State private var isHovering = false

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: DSSpacing.sm) {
                Image(systemName: "folder.fill")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.gitModified)

                VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                    Text(project.name)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(DSColor.textPrimary)
                        .lineLimit(1)

                    HStack(spacing: DSSpacing.xs) {
                        Text(abbreviatedPath)
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textMuted)
                            .lineLimit(1)
                            .truncationMode(.middle)

                        Spacer()

                        Text(project.lastOpened, format: .relative(presentation: .named))
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textSecondary)
                            .lineLimit(1)
                    }
                }

                Spacer(minLength: 0)
            }
            .padding(.horizontal, DSSpacing.sm)
            .padding(.vertical, DSSpacing.xs)
            .background(
                RoundedRectangle(cornerRadius: DSRadius.sm)
                    .fill(isHovering ? DSColor.hoverOverlay : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { isHovering = $0 }
    }

    /// Path with ~ substituted for the home directory.
    private var abbreviatedPath: String {
        NSString(string: project.path.path).abbreviatingWithTildeInPath
    }
}

// MARK: - ActionRow

/// A styled action button row (e.g. "Open Folder...", "Create New...").
private struct ActionRow: View {

    let title: String
    let systemImage: String
    let action: () -> Void

    @State private var isHovering = false

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, DSSpacing.md)
                .padding(.vertical, DSSpacing.sm)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .background(
            RoundedRectangle(cornerRadius: DSRadius.sm)
                .fill(isHovering ? DSColor.hoverOverlay : Color.clear)
        )
        .onHover { isHovering = $0 }
    }
}
