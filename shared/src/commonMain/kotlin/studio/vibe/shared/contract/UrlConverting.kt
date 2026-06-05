package studio.vibe.shared.contract

/**
 * Converts raw git remote URLs (SSH/SCP/git://) into HTTPS browser-openable URLs.
 *
 * Abstracted so [GitSidebarViewModel] can be tested without the concrete
 * [GitURLConverter] implementation.
 */
interface UrlConverting {
    /**
     * Convert [rawUrl] to a browser-navigable HTTPS URL.
     *
     * @return The converted URL, or `null` if the input cannot be converted.
     */
    fun browserURL(rawUrl: String): String?
}
