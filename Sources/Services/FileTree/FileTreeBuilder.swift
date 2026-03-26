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

        return buildLevel(
            at: root,
            relativeTo: root,
            fileManager: fm,
            gitMap: gitMap,
            currentDepth: 0,
            maxDepth: maxDepth,
            visitedPaths: &visitedPaths
        )
    }

    // MARK: - Private

    private static func buildLevel(
        at directory: URL,
        relativeTo root: URL,
        fileManager: FileManager,
        gitMap: [String: GitFileStatus],
        currentDepth: Int,
        maxDepth: Int?,
        visitedPaths: inout Set<String>
    ) -> [FileTreeNode] {
        if let max = maxDepth, currentDepth >= max {
            return []
        }

        // R-14: Resolve symlinks and check for loops to prevent infinite recursion.
        let canonicalPath = directory.resolvingSymlinksInPath().path
        guard !visitedPaths.contains(canonicalPath) else {
            return []
        }
        visitedPaths.insert(canonicalPath)

        guard let contents = try? fileManager.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.isDirectoryKey, .isHiddenKey],
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

            let resourceValues = try? url.resourceValues(forKeys: [.isDirectoryKey])
            let isDirectory = resourceValues?.isDirectory ?? false

            if isDirectory {
                let children = buildLevel(
                    at: url,
                    relativeTo: root,
                    fileManager: fileManager,
                    gitMap: gitMap,
                    currentDepth: currentDepth + 1,
                    maxDepth: maxDepth,
                    visitedPaths: &visitedPaths
                )
                let entry = DirectoryEntry(
                    path: url,
                    children: children,
                    isExpanded: false
                )
                directories.append(.directory(entry))
            } else {
                let relativePath = url.path.replacingOccurrences(
                    of: root.path + "/",
                    with: ""
                )
                let gitStatus = gitMap[relativePath]
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
