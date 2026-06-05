package studio.vibe.shared.contract

/**
 * Platform abstraction for resolving the current user's home directory path.
 *
 * JVM: `System.getProperty("user.home")`
 * macOS/Native: `NSHomeDirectory()`
 */
interface PathResolving {
    /** Returns the absolute path to the current user's home directory. */
    fun resolveHomePath(): String
}
