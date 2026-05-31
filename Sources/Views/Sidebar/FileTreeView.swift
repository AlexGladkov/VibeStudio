// MARK: - FileTreeView
// Recursive file tree with git status annotations.
// LazyVStack for performance, 28pt row height, 16pt indent.
// macOS 14+, Swift 5.10

import SwiftUI

/// Displays a hierarchical file tree for a project directory.
///
/// Features:
/// - Recursive tree with disclosure triangles
/// - Git status annotation (M/A/D/?) on the right
/// - Context menu: Copy Path, Reveal in Finder
/// - LazyVStack for performance with large trees
struct FileTreeView: View {

    let projectPath: URL

    /// When `false`, the "FILES" section header is omitted.
    /// Useful when embedding the tree inside a multi-project sidebar.
    var showSectionHeader: Bool = true

    var onFileDoubleTapped: ((FileEntry) -> Void)?

    @Environment(\.fileSystemWatcher) private var fileSystemWatcher

    @State private var vm = FileTreeViewModel()

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if showSectionHeader {
                filesSectionHeader
            }
            fileTreeContent
        }
        .onDisappear {
            vm.cancelRebuild()
        }
        .task(id: projectPath) {
            await vm.observeFileSystemEvents(
                projectPath: projectPath,
                fileSystemWatcher: fileSystemWatcher
            )
        }
    }

    // MARK: - Section Header

    private var filesSectionHeader: some View {
        HStack {
            Text("FILES")
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textSecondary)
            Spacer()
            Button {
                vm.rebuildTree(at: projectPath)
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(DSFont.iconBase)
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
        }
        .frame(height: DSLayout.gitSectionHeaderHeight)
        .padding(.top, DSSpacing.sm)
    }

    // MARK: - Tree Content

    private var fileTreeContent: some View {
        let expandedBinding = Binding<Set<String>>(
            get: { vm.expandedDirs },
            set: { vm.expandedDirs = $0 }
        )
        return LazyVStack(alignment: .leading, spacing: 0) {
            ForEach(vm.tree) { node in
                FileTreeNodeView(
                    node: node,
                    depth: 0,
                    projectPath: projectPath,
                    expandedDirs: expandedBinding,
                    onFileDoubleTapped: onFileDoubleTapped
                )
            }
        }
    }
}

// MARK: - FileTreeNodeView

/// Renders a single node (file or directory) in the file tree.
/// Recursively renders children for expanded directories.
private struct FileTreeNodeView: View {

    let node: FileTreeNode
    let depth: Int
    let projectPath: URL
    @Binding var expandedDirs: Set<String>
    var onFileDoubleTapped: ((FileEntry) -> Void)?

    var body: some View {
        switch node {
        case .directory(let entry):
            DirectoryRowView(
                entry: entry,
                depth: depth,
                projectPath: projectPath,
                expandedDirs: $expandedDirs,
                onFileDoubleTapped: onFileDoubleTapped
            )
        case .file(let entry):
            FileRowView(
                entry: entry,
                depth: depth,
                projectPath: projectPath,
                onDoubleTap: onFileDoubleTapped
            )
        }
    }
}

// MARK: - DirectoryRowView

private struct DirectoryRowView: View {

    let entry: DirectoryEntry
    let depth: Int
    let projectPath: URL
    @Binding var expandedDirs: Set<String>
    var onFileDoubleTapped: ((FileEntry) -> Void)?

    private var isExpanded: Bool {
        expandedDirs.contains(entry.path.path)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                withAnimation(.easeOut(duration: 0.15)) {
                    if isExpanded {
                        expandedDirs.remove(entry.path.path)
                    } else {
                        expandedDirs.insert(entry.path.path)
                    }
                }
            } label: {
                directoryLabel
            }
            .buttonStyle(.plain)
            .contextMenu {
                Button("Copy Path") {
                    ClipboardService.copy(entry.path.path)
                }
                Divider()
                Button("Reveal in Finder") {
                    NSWorkspace.shared.activateFileViewerSelecting([entry.path])
                }
            }
            .onDrag {
                NSItemProvider(object: entry.path as NSURL)
            }

            if isExpanded {
                ForEach(entry.children) { child in
                    FileTreeNodeView(
                        node: child,
                        depth: depth + 1,
                        projectPath: projectPath,
                        expandedDirs: $expandedDirs,
                        onFileDoubleTapped: onFileDoubleTapped
                    )
                }
            }
        }
    }

    private var directoryLabel: some View {
        HStack(spacing: DSSpacing.xs) {
            DisclosureChevron(isExpanded: isExpanded)

            Image(systemName: "folder.fill")
                .font(DSFont.iconLG)
                .foregroundStyle(DSColor.gitModified)

            Text(entry.path.lastPathComponent)
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .lineLimit(1)

            Spacer()
        }
        .padding(.leading, CGFloat(depth) * DSLayout.treeIndent + DSLayout.treeBaseIndent)
        .frame(height: DSLayout.treeRowHeight)
        .contentShape(Rectangle())
    }
}

// MARK: - FileRowView

private struct FileRowView: View {

    let entry: FileEntry
    let depth: Int
    let projectPath: URL
    var onDoubleTap: ((FileEntry) -> Void)?

    var body: some View {
        let icon = FileIconResolver.icon(for: entry.path)
        return HStack(spacing: DSSpacing.xs) {
            // Spacer for disclosure triangle alignment (matches iconSM point size).
            Color.clear
                .frame(width: DSLayout.treeChevronPlaceholderWidth)

            Image(systemName: icon.name)
                .font(DSFont.iconLG)
                .foregroundStyle(icon.color)

            Text(entry.path.lastPathComponent)
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .lineLimit(1)

            Spacer()

            if let status = entry.gitStatus {
                Text(status.rawValue)
                    .font(DSFont.gitStatus)
                    .foregroundStyle(status.color)
            }
        }
        .padding(.leading, CGFloat(depth) * DSLayout.treeIndent + DSLayout.treeBaseIndent)
        .frame(height: DSLayout.treeRowHeight)
        .contentShape(Rectangle())
        .onTapGesture(count: 2) { onDoubleTap?(entry) }
        .onDrag {
            NSItemProvider(object: entry.path as NSURL)
        }
        .contextMenu {
            Button("Copy Path") {
                ClipboardService.copy(entry.path.path)
            }
            Button("Copy Relative Path") {
                let relative = entry.path.path.replacingOccurrences(
                    of: projectPath.path + "/",
                    with: ""
                )
                ClipboardService.copy(relative)
            }
            Divider()
            Button("Reveal in Finder") {
                NSWorkspace.shared.activateFileViewerSelecting([entry.path])
            }
        }
    }

}
