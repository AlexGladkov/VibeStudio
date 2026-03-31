// MARK: - AgentEnvironmentBuilder
// Builds an allowlist-based environment for AI CLI agent subprocesses.
// macOS 14+, Swift 5.10

import Foundation

/// Constructs a minimal, safe environment for launching AI CLI agents
/// in a dedicated PTY.
///
/// Only variables from an explicit allowlist are inherited from the parent
/// process. Sensitive variables (`AWS_*`, `DATABASE_URL`, `*_SECRET`,
/// `*_TOKEN`, `*_PASSWORD`, `GITHUB_*`) are never forwarded.
///
/// The agent's API key is injected explicitly from Keychain or environment.
enum AgentEnvironmentBuilder {

    /// Variables safe to forward to agent subprocesses.
    private static let allowedVariables: Set<String> = [
        "HOME", "USER", "LOGNAME",
        "LANG", "LC_ALL", "LC_CTYPE",
        "TERM", "COLORTERM",
        "PATH", "SSH_AUTH_SOCK",
        "SHELL", "TMPDIR",
        "XDG_CONFIG_HOME", "XDG_DATA_HOME",
        "OPENAI_API_KEY", "ANTHROPIC_API_KEY",
    ]

    /// Build an environment array (`["KEY=VALUE", ...]`) for the given agent.
    ///
    /// - Parameters:
    ///   - assistant: The AI assistant to launch.
    ///   - apiKeyValue: The API key value to inject (from Keychain or env).
    ///     Pass `nil` if the agent does not require an API key.
    /// - Returns: Array of `"KEY=VALUE"` strings suitable for `startProcess(environment:)`.
    static func build(for assistant: AIAssistant, apiKeyValue: String?) -> [String] {
        let processEnv = ProcessInfo.processInfo.environment
        var result: [String: String] = [:]

        for key in allowedVariables {
            if let value = processEnv[key] {
                result[key] = value
            }
        }

        // Ensure terminal capabilities are always set for proper rendering.
        result["TERM"] = "xterm-256color"
        result["COLORTERM"] = "truecolor"
        result["LANG"] = result["LANG"] ?? "en_US.UTF-8"

        // Agent subprocesses run without a login shell and don't benefit from
        // .zprofile PATH augmentation. When VibeStudio is launched from Finder/Dock,
        // launchd provides a minimal PATH (/usr/bin:/bin:/usr/sbin:/sbin) that
        // excludes Homebrew, npm-global, cargo, etc.
        //
        // We prepend the same trusted directories used by CLIAgentPathResolver so
        // the agent binary can locate itself (for self-invocation, updates) and
        // other tools (git, node) without relying on the parent process PATH.
        // Sourced from SecurityConstants.trustedBinDirectories — single source of truth.
        let trustedBins = SecurityConstants.trustedBinDirectories
        let currentPath = result["PATH"] ?? "/usr/bin:/bin:/usr/sbin:/sbin"
        let existingParts = currentPath.split(separator: ":").map(String.init)
        let missingBins = trustedBins.filter { !existingParts.contains($0) }
        if !missingBins.isEmpty {
            result["PATH"] = (missingBins + existingParts).joined(separator: ":")
        }

        // Inject the agent-specific API key if provided.
        if let envVar = assistant.apiKeyEnvironmentVariable,
           let key = apiKeyValue,
           !key.isEmpty {
            result[envVar] = key
        }

        return result.map { "\($0.key)=\($0.value)" }
    }
}
