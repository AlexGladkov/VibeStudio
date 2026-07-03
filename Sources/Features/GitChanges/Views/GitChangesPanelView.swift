// MARK: - GitChangesPanelView
// Right-side panel showing changed git files.
// Double-click a file to open a resizable diff window.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

/// Right-side panel showing the list of changed git files.
///
/// File sections (Staged / Modified / Untracked) are driven by
/// `GitStatusPoller.status`. Double-clicking a file opens a standalone
/// resizable ``FileDiffSheetView`` window via ``DiffWindowStore``.
struct GitChangesPanelView: View {

    @Environment(\.gitStatusPoller) private var gitStatusPoller
    @Environment(\.gitService) private var gitService
    @Environment(\.projectManager) private var projectManager

    @State private var vmBox = LazyStateObject<GitChangesPanelViewModel>()
    @State private var hoveredFile: String?

    private var viewModel: GitChangesPanelViewModel {
        vmBox.resolve {
            GitChangesPanelViewModel(
                gitService: gitService,
                projectManager: projectManager
            )
        }
    }

    var body: some View {
        let model = viewModel
        let status = gitStatusPoller.status

        VStack(spacing: 0) {
            headerView(status: status)
            Divider()

            switch model.loadingState {
            case .loading where status.isClean:
                // Initial load — show spinner instead of "clean" until we know.
                loadingStateView
            case .error(let message):
                errorStateView(message: message)
            case .loading, .ready:
                if status.isClean {
                    emptyStateView
                } else {
                    fileListView(status: status, model: model)
                }
            }
        }
        .frame(
            minWidth: DSLayout.changesPanelMinWidth,
            idealWidth: DSLayout.changesPanelDefaultWidth,
            maxWidth: DSLayout.changesPanelMaxWidth
        )
        .background(DSColor.surfaceRaised)
        .task(id: status) {
            await viewModel.loadStats()
        }
    }

    // MARK: - Header

    private func headerView(status: GitStatus) -> some View {
        let total = status.stagedFiles.count + status.unstagedFiles.count + status.untrackedFiles.count
        // The total-count badge originally sat at the trailing edge (after a
        // Spacer), so we route it through the header's `trailing` slot rather
        // than the inline `badge` slot — visually identical, single trailing
        // closure keeps the call site terse.
        return SidebarSectionHeader(title: "CHANGES") {
            if total > 0 {
                Text("\(total)")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textInverse)
                    .padding(.horizontal, DSSpacing.xs)
                    .padding(.vertical, 1) // sub-grid vertical padding for badge
                    .background(DSColor.accentPrimary, in: Capsule())
            }
        }
    }

    // MARK: - Empty State

    private var emptyStateView: some View {
        PanelStateView(
            kind: .empty,
            icon: "checkmark.circle",
            title: "Working tree clean"
        )
    }

    // MARK: - Loading State

    private var loadingStateView: some View {
        PanelStateView(
            kind: .loading,
            icon: "ellipsis",
            title: "Loading changes…"
        )
    }

    // MARK: - Error State

    private func errorStateView(message: String) -> some View {
        PanelStateView(
            kind: .error,
            icon: "exclamationmark.triangle",
            title: "Failed to load changes",
            subtitle: message,
            iconColor: DSColor.gitDeleted
        )
    }

    // MARK: - File List

    private func fileListView(status: GitStatus, model: GitChangesPanelViewModel) -> some View {
        let entries = flatEntries(status: status)
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(entries, id: \.id) { entry in
                    fileRowView(file: entry.file, staged: entry.staged, model: model)
                }
            }
            .padding(.top, DSSpacing.xxs)
        }
        .background(DSColor.surfaceRaised)
    }

    /// Flat ordered list: staged first, then unstaged, then untracked.
    private func flatEntries(status: GitStatus) -> [ChangesFileEntry] {
        // swiftlint:disable opening_brace
        status.stagedFiles.map   { ChangesFileEntry(id: "\($0.path)-s", file: $0, staged: true)  } +
        status.unstagedFiles.map { ChangesFileEntry(id: "\($0.path)-u", file: $0, staged: false) } +
        status.untrackedFiles.map { ChangesFileEntry(id: "\($0.path)-t", file: $0, staged: false) }
        // swiftlint:enable opening_brace
    }

    // MARK: - File Row

    private func fileRowView(
        file: GitFile,
        staged: Bool,
        model: GitChangesPanelViewModel
    ) -> some View {
        let isHovered = hoveredFile == file.path
        let stat = model.fileStats[file.path]
        return HStack(spacing: DSSpacing.xs) {
            Text((file.path as NSString).lastPathComponent)
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .lineLimit(1)

            Spacer()

            fileStatsBadges(stat)

            Text(displayStatus(file.status))
                .font(DSFont.gitStatus)
                .foregroundStyle(file.status.color)
                .frame(width: DSLayout.statusLetterWidth, alignment: .center)
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: DSLayout.changesFileRowHeight)
        .background(isHovered ? DSColor.surfaceOverlay : Color.clear)
        .contentShape(Rectangle())
        .onTapGesture(count: 2) {
            openDiffWindow(file: file, staged: staged)
        }
        .onHover { isHoveringNow in hoveredFile = isHoveringNow ? file.path : nil }
        .contextMenu {
            fileRowContextMenu(file: file, staged: staged, model: model)
        }
        .help(file.path)
    }

    /// Inline `+added` / `-deleted` line-count badges for a changed file.
    @ViewBuilder
    private func fileStatsBadges(_ stat: GitDiffStat?) -> some View {
        if let stat {
            if stat.added > 0 {
                Text("+\(stat.added)")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitAdded)
            }
            if stat.deleted > 0 {
                Text("-\(stat.deleted)")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
            }
        }
    }

    /// Right-click menu for a changed file row: view diff + stage/unstage.
    @ViewBuilder
    private func fileRowContextMenu(
        file: GitFile,
        staged: Bool,
        model: GitChangesPanelViewModel
    ) -> some View {
        Button("View Diff") {
            openDiffWindow(file: file, staged: staged)
        }
        Divider()
        if staged {
            Button("Unstage") {
                model.unstageFile(file)
                gitStatusPoller.refreshNow()
            }
        } else {
            Button("Stage") {
                model.stageFile(file)
                gitStatusPoller.refreshNow()
            }
        }
    }

    /// Human-readable single-letter status. `?` (untracked) is shown as `U`.
    private func displayStatus(_ status: GitFileStatus) -> String {
        status == .untracked ? "U" : status.rawValue
    }

    // MARK: - Open Diff Window

    private func openDiffWindow(file: GitFile, staged: Bool) {
        DiffWindowStore.open(
            file: file,
            staged: staged,
            projectPath: projectManager.activeProject?.path,
            gitService: gitService
        )
    }
}

// MARK: - File Entry

private struct ChangesFileEntry {
    let id: String
    let file: GitFile
    let staged: Bool
}
