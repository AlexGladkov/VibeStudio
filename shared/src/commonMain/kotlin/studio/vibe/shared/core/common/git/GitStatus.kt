package studio.vibe.shared.core.common.git

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
