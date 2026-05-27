package studio.vibe.shared.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

data class GitStatus(
    val branch: String,
    val aheadCount: Int,
    val behindCount: Int,
    val stagedFiles: List<GitFile>,
    val unstagedFiles: List<GitFile>,
    val untrackedFiles: List<GitFile>,
) {
    val isClean: Boolean
        get() = stagedFiles.isEmpty() && unstagedFiles.isEmpty() && untrackedFiles.isEmpty()

    companion object {
        val EMPTY = GitStatus(
            branch = "",
            aheadCount = 0,
            behindCount = 0,
            stagedFiles = emptyList(),
            unstagedFiles = emptyList(),
            untrackedFiles = emptyList(),
        )
    }
}

data class GitFile(
    val path: String,
    val status: GitFileStatus,
)

enum class GitFileStatus(val code: String) {
    MODIFIED("M"),
    ADDED("A"),
    DELETED("D"),
    RENAMED("R"),
    COPIED("C"),
    UNTRACKED("?");

    companion object {
        fun fromCode(code: String): GitFileStatus? = entries.find { it.code == code }
    }
}

data class GitBranch(
    val name: String,
    val isRemote: Boolean,
    val isCurrent: Boolean,
)

data class GitDiffHunk(
    val header: String,
    val lines: List<GitDiffLine>,
)

data class GitDiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null,
)

enum class DiffLineType {
    CONTEXT, ADDITION, DELETION
}

data class GitDiffStat(
    val added: Int,
    val deleted: Int,
)

data class GitCommitInfo(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val date: Instant,
)
