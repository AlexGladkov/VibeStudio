// MARK: - GitURLConverter
// Converts git remote URLs (SCP, SSH, HTTPS) to browser-accessible URLs.
// macOS 14+, Swift 5.10

import Foundation

/// Converts git remote URLs to browser-accessible HTTPS URLs.
///
/// Handles all common git remote URL formats:
/// - SCP:   `git@github.com:user/repo.git`             → `https://github.com/user/repo`
/// - SSH:   `ssh://git@github.com/user/repo.git`       → `https://github.com/user/repo`
/// - git:// `git://github.com/user/repo.git`           → `https://github.com/user/repo`
/// - HTTPS: `https://github.com/user/repo.git`         → `https://github.com/user/repo`
/// - Token: `https://token@github.com/user/repo.git`   → `https://github.com/user/repo`
enum GitURLConverter {

    /// Convert a git remote URL to a browser-accessible URL.
    ///
    /// - Parameter remoteURL: Raw remote URL string in any common git format.
    /// - Returns: A browser URL, or nil if the format is unrecognised or invalid.
    static func browserURL(from remoteURL: String) -> URL? {
        var s = remoteURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !s.isEmpty else { return nil }

        // SCP syntax: user@host:path/repo.git (no :// present)
        // Detect by: contains "@" and ":" but no "://"
        if s.contains("@"), s.contains(":"), !s.contains("://") {
            if let atIdx = s.firstIndex(of: "@") {
                s = String(s[s.index(after: atIdx)...])
            }
            if let colonIdx = s.firstIndex(of: ":") {
                s.replaceSubrange(colonIdx...colonIdx, with: "/")
            }
            s = "https://\(s)"
        }

        // ssh:// → https://, strip user@
        if s.lowercased().hasPrefix("ssh://") {
            s = "https://" + s.dropFirst("ssh://".count)
            stripCredentials(from: &s)
        }

        // git:// → https://
        if s.lowercased().hasPrefix("git://") {
            s = "https://" + s.dropFirst("git://".count)
        }

        // https:// or http:// with embedded token/user: https://token@host/... → https://host/...
        if s.lowercased().hasPrefix("https://") || s.lowercased().hasPrefix("http://") {
            stripCredentials(from: &s)
        }

        // Strip .git suffix
        if s.hasSuffix(".git") {
            s = String(s.dropLast(4))
        }

        // Validate: must be a proper http(s) URL with a host
        guard let url = URL(string: s),
              let scheme = url.scheme?.lowercased(),
              scheme == "https" || scheme == "http",
              url.host != nil else { return nil }

        return url
    }

    // MARK: - Private

    /// Strip `user@` or `token@` from the authority component of an already-schemed URL.
    ///
    /// Only strips if the part before `@` contains no dots — dots indicate it is
    /// part of a hostname, not a credential.
    private static func stripCredentials(from urlString: inout String) {
        guard let schemeRange = urlString.range(of: "://") else { return }
        let afterScheme = urlString[schemeRange.upperBound...]
        guard let atRange = afterScheme.range(of: "@") else { return }
        let credential = afterScheme[afterScheme.startIndex..<atRange.lowerBound]
        if !credential.contains(".") {
            urlString.removeSubrange(schemeRange.upperBound...atRange.lowerBound)
        }
    }
}
