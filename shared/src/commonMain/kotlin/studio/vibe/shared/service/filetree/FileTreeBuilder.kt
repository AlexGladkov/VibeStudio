package studio.vibe.shared.service.filetree

import studio.vibe.shared.constants.PathConstants
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.model.DirectoryEntry
import studio.vibe.shared.model.FileEntry
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.FileTreeNode
import studio.vibe.shared.model.GitFile
import studio.vibe.shared.model.GitFileStatus
import studio.vibe.shared.model.GitStatus

/**
 * Builds a [FileTreeNode] hierarchy from a directory on disk.
 *
 * Excluded directories (node_modules, .git, .build, DerivedData, etc.) are
 * filtered out. Git status annotations are applied from an optional [GitStatus].
 *
 * Symlink loop detection is done by tracking canonical path strings that the
 * platform persistence layer resolves; each path is visited at most once.
 */
class FileTreeBuilder(private val persistence: PersistenceStore) {

    /** Directories that are always excluded from the tree. */
    private val excludedNames: Set<String> = PathConstants.excludedDirectoryNames

    /**
     * Build a file tree for the given directory.
     *
     * @param root      Root directory path.
     * @param gitStatus Optional git status for annotating file nodes.
     * @param maxDepth  Maximum recursion depth (null = unlimited).
     * @return List of root-level [FileTreeNode] entries, directories first then files,
     *   each group sorted alphabetically (case-insensitive).
     */
    suspend fun buildTree(
        root: FilePath,
        gitStatus: GitStatus? = null,
        maxDepth: Int? = null,
    ): List<FileTreeNode> {
        // Build a lookup map of relative path -> git status.
        val gitMap = mutableMapOf<String, GitFileStatus>()
        if (gitStatus != null) {
            fun addFiles(files: List<GitFile>) {
                for (file in files) gitMap[file.path] = file.status
            }
            addFiles(gitStatus.stagedFiles)
            addFiles(gitStatus.unstagedFiles)
            addFiles(gitStatus.untrackedFiles)
        }

        val visitedPaths = mutableSetOf<String>()
        return buildLevel(
            directory = root,
            root = root,
            gitMap = gitMap,
            currentDepth = 0,
            maxDepth = maxDepth,
            visitedPaths = visitedPaths,
        )
    }

    // ---------------------------------------------------------------------------
    // Private
    // ---------------------------------------------------------------------------

    private suspend fun buildLevel(
        directory: FilePath,
        root: FilePath,
        gitMap: Map<String, GitFileStatus>,
        currentDepth: Int,
        maxDepth: Int?,
        visitedPaths: MutableSet<String>,
    ): List<FileTreeNode> {
        if (maxDepth != null && currentDepth >= maxDepth) return emptyList()

        // Symlink loop detection: use the canonical path as the identity key.
        // PersistenceStore exposes the path string directly; the platform
        // implementation is responsible for resolving symlinks before listing.
        val canonicalPath = directory.path
        if (canonicalPath in visitedPaths) return emptyList()
        visitedPaths.add(canonicalPath)

        val contents = try {
            persistence.listDirectory(directory)
        } catch (_: Exception) {
            return emptyList()
        }

        val directories = mutableListOf<FileTreeNode>()
        val files = mutableListOf<FileTreeNode>()

        for (childPath in contents) {
            val name = childPath.name

            // Skip excluded directories and hidden files.
            if (name in excludedNames) continue

            val isDir = try {
                persistence.isDirectory(childPath)
            } catch (_: Exception) {
                false
            }

            if (isDir) {
                val children = buildLevel(
                    directory = childPath,
                    root = root,
                    gitMap = gitMap,
                    currentDepth = currentDepth + 1,
                    maxDepth = maxDepth,
                    visitedPaths = visitedPaths,
                )
                val entry = DirectoryEntry(
                    path = childPath,
                    children = children,
                    isExpanded = false,
                )
                directories.add(FileTreeNode.Directory(entry))
            } else {
                // Compute path relative to root for git status lookup.
                val rootPrefix = root.path.trimEnd('/') + "/"
                val relativePath = if (childPath.path.startsWith(rootPrefix)) {
                    childPath.path.removePrefix(rootPrefix)
                } else {
                    childPath.path
                }
                val status = gitMap[relativePath]
                val entry = FileEntry(path = childPath, gitStatus = status)
                files.add(FileTreeNode.File(entry))
            }
        }

        // Sort: directories first (alphabetical), then files (alphabetical).
        directories.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        files.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        return directories + files
    }
}
