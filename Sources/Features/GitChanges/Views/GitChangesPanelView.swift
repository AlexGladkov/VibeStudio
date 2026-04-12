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

    @State private var vm: GitChangesPanelViewModel?
    @State private var hoveredFile: String?

    private var viewModel: GitChangesPanelViewModel {
        if let existing = vm { return existing }
        let created = GitChangesPanelViewModel(
            gitService: gitService,
            projectManager: projectManager
        )
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel
        let status = gitStatusPoller.status

        VStack(spacing: 0) {
            headerView(status: status)
            Divider()

            if status.isClean {
                emptyStateView
            } else {
                fileListView(status: status, model: model)
            }
        }
        .frame(
            minWidth: DSLayout.changesPanelMinWidth,
            idealWidth: DSLayout.changesPanelDefaultWidth,
            maxWidth: DSLayout.changesPanelMaxWidth
        )
        .background(DSColor.surfaceRaised)
        .onAppear {
            if vm == nil {
                vm = GitChangesPanelViewModel(
                    gitService: gitService,
                    projectManager: projectManager
                )
            }
        }
        .task(id: status) {
            await viewModel.loadStats()
        }
    }

    // MARK: - Header

    private func headerView(status: GitStatus) -> some View {
        let total = status.stagedFiles.count + status.unstagedFiles.count + status.untrackedFiles.count
        return HStack(spacing: DSSpacing.xs) {
            Text("CHANGES")
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textSecondary)
            Spacer()
            if total > 0 {
                Text("\(total)")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textInverse)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1)
                    .background(DSColor.accentPrimary, in: Capsule())
            }
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: 28)
    }

    // MARK: - Empty State

    private var emptyStateView: some View {
        VStack(spacing: DSSpacing.sm) {
            Spacer()
            Image(systemName: "checkmark.circle")
                .font(.system(size: 24))
                .foregroundStyle(DSColor.textMuted)
            Text("Working tree clean")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Spacer()
        }
        .frame(maxWidth: .infinity)
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
        status.stagedFiles.map   { ChangesFileEntry(id: "\($0.path)-s", file: $0, staged: true)  } +
        status.unstagedFiles.map { ChangesFileEntry(id: "\($0.path)-u", file: $0, staged: false) } +
        status.untrackedFiles.map { ChangesFileEntry(id: "\($0.path)-t", file: $0, staged: false) }
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

            Text(displayStatus(file.status))
                .font(DSFont.gitStatus)
                .foregroundStyle(file.status.color)
                .frame(width: 16, alignment: .center)
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
        .help(file.path)
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

// MARK: - Diff Window Store

/// Manages standalone diff `NSWindow` instances.
///
/// Retains windows in a static array so they are not deallocated while open.
/// Each window is removed from the array automatically when it closes.
enum DiffWindowStore {

    nonisolated(unsafe) private static var windows: [NSWindow] = []

    /// Open a new resizable diff window for the given file.
    @MainActor
    static func open(
        file: GitFile,
        staged: Bool,
        projectPath: URL?,
        gitService: any GitServicing
    ) {
        let content = FileDiffSheetView(
            file: file,
            staged: staged,
            projectPath: projectPath,
            gitService: gitService
        )

        let vc = NSHostingController(rootView: content)
        let window = NSWindow(contentViewController: vc)
        window.styleMask = [.titled, .closable, .resizable, .miniaturizable]
        window.title = (file.path as NSString).lastPathComponent

        let screen = NSScreen.main?.visibleFrame ?? NSRect(x: 0, y: 0, width: 1440, height: 900)
        let width  = min(screen.width  * 0.82, 1680)
        let height = min(screen.height * 0.88, 1100)
        window.setContentSize(NSSize(width: width, height: height))
        window.center()

        window.minSize = NSSize(width: 760, height: 480)
        window.isReleasedWhenClosed = false

        windows.append(window)

        NotificationCenter.default.addObserver(
            forName: NSWindow.willCloseNotification,
            object: window,
            queue: .main
        ) { [weak window] _ in
            guard let window else { return }
            DiffWindowStore.windows.removeAll { $0 === window }
        }

        window.makeKeyAndOrderFront(nil)
    }
}
