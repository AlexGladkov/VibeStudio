package studio.vibe.shared.service.git.parser

import studio.vibe.shared.model.GitServiceError

/**
 * Validation logic for git branch and remote names.
 *
 * Extracted from [GitCommandExecutor] to keep that class focused on
 * command orchestration.
 */
internal object GitBranchParser {

    // Regex for valid git branch/remote names — prevents injection via crafted names.
    private val validBranchRegex = Regex("^[a-zA-Z0-9/_\\-.@]+$")

    /**
     * Validate that a branch or remote name does not contain dangerous characters.
     *
     * Prevents command injection via crafted names such as `--upload-pack=evil`
     * or `; rm -rf /`. Mirrors the rules enforced by git itself.
     *
     * @throws [GitServiceError.CommandFailed] if the name is invalid.
     */
    fun validateBranchName(name: String) {
        val valid = name.isNotEmpty() &&
            !name.startsWith("-") &&
            !name.contains("..") &&
            !name.contains(" ") &&
            !name.contains("~") &&
            !name.contains("^") &&
            !name.contains(":") &&
            !name.contains("\\") &&
            validBranchRegex.matches(name)

        if (!valid) {
            throw GitServiceError.CommandFailed(
                command = "validate",
                exitCode = 1,
                stderr = "Invalid branch name: $name",
            )
        }
    }
}
