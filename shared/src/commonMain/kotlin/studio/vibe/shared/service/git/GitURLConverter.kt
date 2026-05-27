package studio.vibe.shared.service.git

/**
 * Converts git remote URLs to browser-accessible HTTPS URL strings.
 *
 * Handles all common git remote URL formats:
 * - SCP:    `git@github.com:user/repo.git`            → `https://github.com/user/repo`
 * - SSH:    `ssh://git@github.com/user/repo.git`      → `https://github.com/user/repo`
 * - git://  `git://github.com/user/repo.git`          → `https://github.com/user/repo`
 * - HTTPS:  `https://github.com/user/repo.git`        → `https://github.com/user/repo`
 * - Token:  `https://token@github.com/user/repo.git`  → `https://github.com/user/repo`
 * - HTTP:   `http://github.com/user/repo.git`         → `https://github.com/user/repo`
 *
 * Returns a plain [String] (not a URL object) because `commonMain` has no
 * platform-agnostic URL class. Callers on Android/Desktop/iOS can parse the
 * returned string into a platform URL type as needed.
 */
object GitURLConverter {

    /**
     * Convert a git remote URL to a browser-accessible HTTPS URL string.
     *
     * @param remoteURL Raw remote URL in any common git format.
     * @return A normalized `https://` URL string, or `null` if the input
     *   format is unrecognised or produces an invalid URL.
     */
    fun browserURL(remoteURL: String): String? {
        var s = remoteURL.trim()
        if (s.isEmpty()) return null

        // ------------------------------------------------------------------
        // SCP syntax: user@host:path/repo.git  (no "://" present)
        // Detect by: contains "@" and ":" but no "://".
        // ------------------------------------------------------------------
        if (s.contains("@") && s.contains(":") && !s.contains("://")) {
            // Strip the user@ prefix.
            val atIdx = s.indexOf('@')
            if (atIdx >= 0) {
                s = s.substring(atIdx + 1)
            }
            // Replace the ":" separator between host and path with "/".
            val colonIdx = s.indexOf(':')
            if (colonIdx >= 0) {
                s = s.substring(0, colonIdx) + "/" + s.substring(colonIdx + 1)
            }
            s = "https://$s"
        }

        // ------------------------------------------------------------------
        // ssh:// → https://, strip embedded user@ credentials.
        // ------------------------------------------------------------------
        if (s.lowercase().startsWith("ssh://")) {
            s = "https://" + s.substring("ssh://".length)
            s = stripCredentials(s)
        }

        // ------------------------------------------------------------------
        // git:// → https://
        // ------------------------------------------------------------------
        if (s.lowercase().startsWith("git://")) {
            s = "https://" + s.substring("git://".length)
        }

        // ------------------------------------------------------------------
        // https:// or http:// with embedded token/user credentials.
        // e.g. https://token@github.com/… → https://github.com/…
        // ------------------------------------------------------------------
        if (s.lowercase().startsWith("https://") || s.lowercase().startsWith("http://")) {
            // Normalise http:// → https://
            if (s.lowercase().startsWith("http://")) {
                s = "https://" + s.substring("http://".length)
            }
            s = stripCredentials(s)
        }

        // ------------------------------------------------------------------
        // Strip .git suffix.
        // ------------------------------------------------------------------
        if (s.endsWith(".git")) {
            s = s.dropLast(4)
        }

        // ------------------------------------------------------------------
        // Validate: must start with https:// and have a non-empty host part.
        // A minimal structural check without a URL parser:
        //   https://<host><optional-path>
        // ------------------------------------------------------------------
        if (!s.lowercase().startsWith("https://")) return null
        val afterScheme = s.substring("https://".length)
        if (afterScheme.isEmpty()) return null

        // Host is everything up to the first "/" (or end of string).
        val host = afterScheme.substringBefore("/")
        if (host.isEmpty() || !host.contains(".")) return null

        return s
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Strip `user@` or `token@` from the authority component of an already-schemed URL.
     *
     * Only strips if the credential part (before `@`) contains no dots — dots
     * indicate it is part of a hostname, not a credential string.
     *
     * Example:
     * - `https://ghp_token@github.com/…` → `https://github.com/…`  (stripped)
     * - `https://sub.domain@github.com/…` → unchanged               (not stripped — has dot)
     */
    private fun stripCredentials(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return url
        val afterScheme = url.substring(schemeEnd + 3)
        val atIdx = afterScheme.indexOf('@')
        if (atIdx < 0) return url
        val credential = afterScheme.substring(0, atIdx)
        // Only strip if the credential contains no dots (dots → it's part of a host).
        if (credential.contains(".")) return url
        val scheme = url.substring(0, schemeEnd + 3) // "https://"
        val rest = afterScheme.substring(atIdx + 1)
        return scheme + rest
    }
}
