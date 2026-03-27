// MARK: - CLIAgentPathResolver
// Resolves AI CLI agent executables from trusted directories only.
// macOS 14+, Swift 5.10

import Foundation

/// Resolves the absolute path of an AI CLI agent binary by searching
/// a curated allowlist of trusted directories.
///
/// This prevents arbitrary path injection -- only binaries installed in
/// well-known locations (Homebrew, npm global, cargo, /usr/local/bin)
/// are eligible for launch.
enum CLIAgentPathResolver {

    /// Directories trusted for CLI agent binaries, searched in order.
    private static let trustedDirectories: [String] = [
        "/opt/homebrew/bin",
        "/usr/local/bin",
        "\(NSHomeDirectory())/.local/bin",
        "\(NSHomeDirectory())/.npm-global/bin",
        "/usr/bin",
    ]

    /// Resolve an executable name to its absolute path within trusted directories.
    ///
    /// - Parameter executableName: The bare binary name (e.g. "claude", "codex").
    ///   Must not contain path separators or traversal sequences.
    /// - Returns: Absolute path to the executable, or `nil` if not found
    ///   or the name is invalid.
    static func resolve(_ executableName: String) -> String? {
        // Reject path traversal and absolute/relative paths.
        guard !executableName.contains("/"),
              !executableName.contains(".."),
              !executableName.isEmpty else {
            return nil
        }

        for directory in trustedDirectories {
            let path = "\(directory)/\(executableName)"
            if FileManager.default.isExecutableFile(atPath: path) {
                return path
            }
        }
        return nil
    }
}
