// MARK: - SecurityConstants
// Single source of truth for trusted binary directories.
// macOS 14+, Swift 5.10

import Foundation

/// Security-related constants shared across the application.
enum SecurityConstants {

    /// Trusted binary directories used for agent path resolution and PATH construction.
    ///
    /// This is the single source of truth for the allowlist.
    /// Both ``CLIAgentPathResolver`` and ``AgentEnvironmentBuilder`` reference this list
    /// to guarantee consistency between binary lookup and the PATH injected into
    /// agent subprocesses.
    ///
    /// Directories are ordered by lookup priority (most specific first):
    /// 1. Apple Silicon Homebrew (`/opt/homebrew/bin`, `/opt/homebrew/sbin`)
    /// 2. Intel Homebrew / system-local (`/usr/local/bin`)
    /// 3. User-local directories (`~/.local/bin`, `~/.npm-global/bin`, `~/.cargo/bin`, `~/.opencode/bin`)
    /// 4. System binaries (`/usr/bin`)
    static let trustedBinDirectories: [String] = [
        "/opt/homebrew/bin",
        "/opt/homebrew/sbin",
        "/usr/local/bin",
        "\(NSHomeDirectory())/.local/bin",
        "\(NSHomeDirectory())/.npm-global/bin",
        "\(NSHomeDirectory())/.cargo/bin",
        "\(NSHomeDirectory())/.opencode/bin",
        "/usr/bin",
    ]
}
