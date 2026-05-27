package studio.vibe.shared.model

import kotlin.time.Instant

data class FileChangeEvent(
    val path: FilePath,
    val kind: FileChangeKind,
    val timestamp: Instant,
)

enum class FileChangeKind {
    CREATED, MODIFIED, DELETED, RENAMED
}

sealed class FileTreeNode {
    abstract val id: String
    abstract val name: String

    data class File(val entry: FileEntry) : FileTreeNode() {
        override val id: String get() = entry.path.path
        override val name: String get() = entry.path.name
    }

    data class Directory(val entry: DirectoryEntry) : FileTreeNode() {
        override val id: String get() = entry.path.path
        override val name: String get() = entry.path.name
    }
}

data class FileEntry(
    val path: FilePath,
    val gitStatus: GitFileStatus? = null,
)

data class DirectoryEntry(
    val path: FilePath,
    val children: List<FileTreeNode>,
    val isExpanded: Boolean,
)
