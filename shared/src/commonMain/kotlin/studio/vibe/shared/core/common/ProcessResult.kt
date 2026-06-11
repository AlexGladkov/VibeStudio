package studio.vibe.shared.core.common

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
