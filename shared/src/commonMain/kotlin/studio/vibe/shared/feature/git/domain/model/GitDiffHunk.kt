package studio.vibe.shared.feature.git.domain.model

data class GitDiffHunk(
    val header: String,
    val lines: List<GitDiffLine>,
)
