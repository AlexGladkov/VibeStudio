package studio.vibe.shared.core.common.git

data class GitFile(
    val path: String,
    val status: GitFileStatus,
)
