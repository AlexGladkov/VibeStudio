package studio.vibe.shared.core.common.git

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
