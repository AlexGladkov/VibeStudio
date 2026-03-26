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

    var onFileDoubleTapped: ((FileEntry) -> Void)? = nil

    @Environment(\.fileSystemWatcher) private var fileSystemWatcher

    @State private var tree: [FileTreeNode] = []
    @State private var expandedDirs: Set<String> = []
    @State private var rebuildTask: Task<Void, Never>?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if showSectionHeader {
                filesSectionHeader
            }
            fileTreeContent
        }
        .onAppear {
            rebuildTree()
        }
        .onDisappear {
            rebuildTask?.cancel()
        }
        .task(id: projectPath) {
            // Rebuild immediately when projectPath changes.
            rebuildTree()
            // Then watch for file system events inside this project directory.
            let projectPrefix = projectPath.path
            var debounceTask: Task<Void, Never>?
            for await event in fileSystemWatcher.events {
                guard event.path.path.hasPrefix(projectPrefix) else { continue }
                debounceTask?.cancel()
                debounceTask = Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(500))
                    guard !Task.isCancelled else { return }
                    rebuildTree()
                }
            }
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
                rebuildTree()
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: 11))
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
        }
        .frame(height: DSLayout.gitSectionHeaderHeight)
        .padding(.top, DSSpacing.sm)
    }

    // MARK: - Tree Content

    private var fileTreeContent: some View {
        LazyVStack(alignment: .leading, spacing: 0) {
            ForEach(tree) { node in
                FileTreeNodeView(
                    node: node,
                    depth: 0,
                    projectPath: projectPath,
                    expandedDirs: $expandedDirs,
                    onFileDoubleTapped: onFileDoubleTapped
                )
            }
        }
    }

    // MARK: - Tree Building

    private func rebuildTree() {
        rebuildTask?.cancel()
        let path = projectPath
        rebuildTask = Task {
            let nodes = await Task.detached(priority: .utility) {
                guard !Task.isCancelled else { return [FileTreeNode]() }
                return FileTreeBuilder.buildTree(at: path)
            }.value
            guard !Task.isCancelled else { return }
            tree = nodes
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
    var onFileDoubleTapped: ((FileEntry) -> Void)? = nil

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
    var onFileDoubleTapped: ((FileEntry) -> Void)? = nil

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
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(entry.path.path, forType: .string)
                }
                Divider()
                Button("Reveal in Finder") {
                    NSWorkspace.shared.activateFileViewerSelecting([entry.path])
                }
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
            Image(systemName: "chevron.right")
                .font(.system(size: 9))
                .foregroundStyle(DSColor.textMuted)
                .rotationEffect(isExpanded ? .degrees(90) : .zero)

            Image(systemName: "folder.fill")
                .font(.system(size: 14))
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
    var onDoubleTap: ((FileEntry) -> Void)? = nil

    var body: some View {
        HStack(spacing: DSSpacing.xs) {
            // Spacer for disclosure triangle alignment.
            Color.clear
                .frame(width: 9, height: 9)

            Image(systemName: fileIcon)
                .font(.system(size: 14))
                .foregroundStyle(fileIconColor)

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
        .contextMenu {
            Button("Copy Path") {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(entry.path.path, forType: .string)
            }
            Button("Copy Relative Path") {
                let relative = entry.path.path.replacingOccurrences(
                    of: projectPath.path + "/",
                    with: ""
                )
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(relative, forType: .string)
            }
            Divider()
            Button("Reveal in Finder") {
                NSWorkspace.shared.activateFileViewerSelecting([entry.path])
            }
        }
    }

    // MARK: - Helpers

    private var fileIcon: String {
        let ext = entry.path.pathExtension.lowercased()
        switch ext {
        case "swift": return "swift"
        case "json", "yaml", "yml", "toml": return "gearshape.fill"
        default: return "doc.text.fill"
        }
    }

    private var fileIconColor: Color {
        let ext = entry.path.pathExtension.lowercased()
        switch ext {
        case "swift": return Color(hex: "#F05138")
        default: return DSColor.textSecondary
        }
    }

}
