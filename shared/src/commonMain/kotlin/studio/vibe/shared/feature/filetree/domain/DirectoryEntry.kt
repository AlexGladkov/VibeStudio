package studio.vibe.shared.feature.filetree.domain

import studio.vibe.shared.core.common.FilePath

data class DirectoryEntry(
    val path: FilePath,
    val children: List<FileTreeNode>,
    val isExpanded: Boolean,
)
