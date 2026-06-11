package studio.vibe.shared.feature.git.domain.model

data class GitBranch(
    val name: String,
    val isRemote: Boolean,
    val isCurrent: Boolean,
)
