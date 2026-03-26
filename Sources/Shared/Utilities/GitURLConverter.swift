// MARK: - GitURLConverter
// Converts git remote URLs (SCP, SSH, HTTPS) to browser-accessible URLs.
// macOS 14+, Swift 5.10

import Foundation

/// Converts git remote URLs to browser-accessible HTTPS URLs.
///
/// Handles three URL formats:
/// - SCP: `git@github.com:user/repo.git` → `https://github.com/user/repo`
/// - SSH: `ssh://git@github.com/user/repo.git` → `https://github.com/user/repo`
/// - HTTPS: `https://github.com/user/repo.git` → `https://github.com/user/repo`
enum GitURLConverter {

    /// Convert a git remote URL to a browser-accessible URL.
    ///
    /// - Parameter remoteURL: Raw remote URL string (SCP, SSH, or HTTPS format).
    /// - Returns: A browser URL, or nil if the format is unrecognised.
    static func browserURL(from remoteURL: String) -> URL? {
        let trimmed = remoteURL.trimmingCharacters(in: .whitespacesAndNewlines)

        // SCP format: git@github.com:user/repo.git
        if trimmed.hasPrefix("git@") {
            let withoutPrefix = String(trimmed.dropFirst(4)) // drop "git@"
            let parts = withoutPrefix.components(separatedBy: ":")
            guard parts.count == 2 else { return nil }
            let host = parts[0]
            let path = parts[1].hasSuffix(".git") ? String(parts[1].dropLast(4)) : parts[1]
            return URL(string: "https://\(host)/\(path)")
        }

        // SSH format: ssh://git@github.com/user/repo.git
        if trimmed.hasPrefix("ssh://") {
            var cleaned = String(trimmed.dropFirst(6)) // drop "ssh://"
            // Remove user@ prefix if present
            if let atIdx = cleaned.range(of: "@") {
                cleaned = String(cleaned[atIdx.upperBound...])
            }
            if cleaned.hasSuffix(".git") {
                cleaned = String(cleaned.dropLast(4))
            }
            return URL(string: "https://\(cleaned)")
        }

        // HTTPS format: https://github.com/user/repo.git
        if trimmed.hasPrefix("https://") || trimmed.hasPrefix("http://") {
            var cleaned = trimmed
            if cleaned.hasSuffix(".git") {
                cleaned = String(cleaned.dropLast(4))
            }
            return URL(string: cleaned)
        }

        return nil
    }
}
