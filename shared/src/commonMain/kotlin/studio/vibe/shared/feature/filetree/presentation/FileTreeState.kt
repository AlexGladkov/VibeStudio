package studio.vibe.shared.feature.filetree.presentation

import studio.vibe.shared.feature.filetree.domain.FileTreeNode
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class FileTreeState(
    val nodes: List<FileTreeNode> = emptyList(),
    val expandedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
