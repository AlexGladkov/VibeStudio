// MARK: - FileTreeBuilder
// Builds a FileTreeNode hierarchy from the file system.
// Filters excluded directories and annotates git status.
// macOS 14+, Swift 5.10

import Foundation

/// Builds a hierarchical ``FileTreeNode`` tree from a directory on disk.
///
/// Excluded directories (node_modules, .git, .build, DerivedData, etc.)
/// are filtered out. Git status annotations are applied from a provided
/// ``GitStatus`` instance.
enum FileTreeBuilder {

    /// Directories that are always excluded from the tree.
    private static let excludedNames: Set<String> = PathConstants.excludedDirectoryNames

    /// Build a file tree for the given directory.
    ///
    /// - Parameters:
    ///   - root: Root directory URL.
    ///   - gitStatus: Optional git status for annotating file nodes.
    ///   - maxDepth: Maximum recursion depth (nil = unlimited).
    /// - Returns: Array of root-level ``FileTreeNode`` entries.
    static func buildTree(
        at root: URL,
        gitStatus: GitStatus? = nil,
        maxDepth: Int? = nil
    ) -> [FileTreeNode] {
        let fm = FileManager.default

        // Build a lookup map of relative path -> git status.
        var gitMap: [String: GitFileStatus] = [:]
        if let gs = gitStatus {
            for file in gs.stagedFiles { gitMap[file.path] = file.status }
            for file in gs.unstagedFiles { gitMap[file.path] = file.status }
            for file in gs.untrackedFiles { gitMap[file.path] = file.status }
        }

        // R-14: Track visited canonical paths to detect symlink loops.
        var visitedPaths = Set<String>()

        let context = BuildContext(
            root: root,
            fileManager: fm,
            gitMap: gitMap,
            maxDepth: maxDepth
        )

        return buildLevel(
            at: root,
            context: context,
            currentDepth: 0,
            visitedPaths: &visitedPaths
        )
    }

    // MARK: - Private

    /// Invariant inputs shared across every recursive ``buildLevel`` call.
    private struct BuildContext {
        let root: URL
        let fileManager: FileManager
        let gitMap: [String: GitFileStatus]
        let maxDepth: Int?
    }

    private static func gitRelativePath(for url: URL, root: URL) -> String? {
        let rootPaths = [root.path, root.resolvingSymlinksInPath().path]
        let filePaths = [url.path, url.resolvingSymlinksInPath().path]

        for filePath in filePaths {
            for rootPath in rootPaths {
                let normalizedRootPath = rootPath.hasSuffix("/") ? String(rootPath.dropLast()) : rootPath
                let prefix = normalizedRootPath + "/"
                if filePath.hasPrefix(prefix) {
                    return String(filePath.dropFirst(prefix.count))
                }
            }
        }

        return nil
    }

    private static func buildLevel(
        at directory: URL,
        context: BuildContext,
        currentDepth: Int,
        visitedPaths: inout Set<String>
    ) -> [FileTreeNode] {
        if let max = context.maxDepth, currentDepth >= max {
            return []
        }

        // R-14: Resolve symlinks and check for loops to prevent infinite recursion.
        let canonicalPath = directory.resolvingSymlinksInPath().path
        guard !visitedPaths.contains(canonicalPath) else {
            return []
        }
        visitedPaths.insert(canonicalPath)

        // Prefetch only `.isDirectoryKey`: it is the sole resource value read
        // below, and enumeration caches it on each returned URL. `.isHiddenKey`
        // was prefetched previously but never consumed — dropping it avoids an
        // unused per-entry stat during directory enumeration.
        guard let contents = try? context.fileManager.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsPackageDescendants]
        ) else {
            return []
        }

        var directories: [FileTreeNode] = []
        var files: [FileTreeNode] = []

        for url in contents {
            let name = url.lastPathComponent

            // Skip excluded directories and hidden files.
            if excludedNames.contains(name) { continue }

            // Served from the enumeration prefetch cache — no additional stat.
            let resourceValues = try? url.resourceValues(forKeys: [.isDirectoryKey])
            let isDirectory = resourceValues?.isDirectory ?? false

            if isDirectory {
                let children = buildLevel(
                    at: url,
                    context: context,
                    currentDepth: currentDepth + 1,
                    visitedPaths: &visitedPaths
                )
                let entry = DirectoryEntry(
                    path: url,
                    children: children,
                    isExpanded: false
                )
                directories.append(.directory(entry))
            } else {
                let relativePath = gitRelativePath(for: url, root: context.root)
                let gitStatus = relativePath.flatMap { context.gitMap[$0] }
                let entry = FileEntry(path: url, gitStatus: gitStatus)
                files.append(.file(entry))
            }
        }

        // Sort: directories first (alphabetical), then files (alphabetical).
        directories.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        files.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }

        return directories + files
    }
}
