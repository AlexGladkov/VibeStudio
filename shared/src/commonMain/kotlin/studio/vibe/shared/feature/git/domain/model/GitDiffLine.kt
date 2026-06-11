package studio.vibe.shared.feature.git.domain.model

data class GitDiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null,
)
