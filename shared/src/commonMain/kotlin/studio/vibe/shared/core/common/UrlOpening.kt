package studio.vibe.shared.core.common

/**
 * Platform abstraction for opening a URL in the default browser.
 *
 * JVM: `java.awt.Desktop.browse(URI(url))`
 * macOS/Native: `NSWorkspace.shared.open(URL(string: url))`
 */
interface UrlOpening {
    /** Opens [url] in the platform's default browser. Best-effort; errors are swallowed. */
    fun openUrl(url: String)
}
