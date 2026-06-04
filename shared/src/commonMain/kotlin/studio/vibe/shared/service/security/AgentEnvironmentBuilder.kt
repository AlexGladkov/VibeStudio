package studio.vibe.shared.service.security

import studio.vibe.shared.constants.SecurityConstants
import studio.vibe.shared.contract.AIAgent

/**
 * Constructs a minimal, safe environment map for launching AI CLI agents.
 *
 * Only variables from an explicit allowlist are inherited from the parent process
 * environment. Sensitive variables (AWS_*, DATABASE_URL, *_SECRET, *_TOKEN,
 * *_PASSWORD, GITHUB_*) are never forwarded because they are not in the allowlist.
 *
 * Returns Map<String, String>. Callers that need "KEY=VALUE" strings for PTY/process
 * launch must format the map themselves (platform-specific concern).
 */
object AgentEnvironmentBuilder {

    /** Variables safe to forward to agent subprocesses. */
    private val allowedVariables: Set<String> = setOf(
        "HOME", "USER", "LOGNAME",
        "LANG", "LC_ALL", "LC_CTYPE",
        "TERM", "COLORTERM",
        "PATH", "SSH_AUTH_SOCK",
        "SHELL", "TMPDIR",
        "XDG_CONFIG_HOME", "XDG_DATA_HOME",
        "OPENAI_API_KEY", "ANTHROPIC_API_KEY",
    )

    /**
     * Build an environment map for the given agent.
     *
     * @param assistant   The AI assistant to launch.
     * @param apiKeyValue The API key value to inject (from Keychain or env).
     *   Pass null if the agent does not require an API key.
     * @param currentEnv  The current process environment (pass ProcessEnvironment on each platform).
     * @return Map of environment variable names to values.
     */
    fun build(
        agent: AIAgent,
        apiKeyValue: String?,
        currentEnv: Map<String, String>,
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // Copy allowlisted variables from the current environment.
        for (key in allowedVariables) {
            val value = currentEnv[key]
            if (value != null) {
                result[key] = value
            }
        }

        // Ensure terminal capabilities are always set for proper rendering.
        result["TERM"] = "xterm-256color"
        result["COLORTERM"] = "truecolor"
        if (!result.containsKey("LANG")) {
            result["LANG"] = "en_US.UTF-8"
        }

        // Agent subprocesses run without a login shell and don't benefit from
        // .zprofile PATH augmentation. Prepend trusted directories so the agent
        // binary can locate itself and other tools (git, node) without relying
        // on the parent process PATH. Sourced from SecurityConstants — single
        // source of truth.
        val homeDir = result["HOME"] ?: ""
        val trustedBins = SecurityConstants.trustedBinDirectories(homeDir)
        val currentPath = result["PATH"] ?: "/usr/bin:/bin:/usr/sbin:/sbin"
        val existingParts = currentPath.split(":").filter { it.isNotEmpty() }
        val missingBins = trustedBins.filter { it !in existingParts }
        if (missingBins.isNotEmpty()) {
            result["PATH"] = (missingBins + existingParts).joinToString(":")
        }

        // Inject the agent-specific API key if provided.
        val envVar = agent.apiKeyEnvironmentVariable
        if (envVar != null && !apiKeyValue.isNullOrEmpty()) {
            result[envVar] = apiKeyValue
        }

        return result
    }
}
