package studio.vibe.shared.feature.filetree.domain

import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.git.GitFileStatus

data class FileEntry(
    val path: FilePath,
    val gitStatus: GitFileStatus? = null,
)
