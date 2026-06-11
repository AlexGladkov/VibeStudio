package studio.vibe.shared.feature.filetree.domain

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
