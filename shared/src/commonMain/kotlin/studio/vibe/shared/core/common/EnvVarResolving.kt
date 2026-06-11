package studio.vibe.shared.core.common

/**
 * Platform abstraction for reading process environment variables.
 *
 * JVM: `System.getenv(name)`
 * macOS/Native: `ProcessInfo.processInfo.environment[name]`
 */
interface EnvVarResolving {
    /** Returns the value of the environment variable [name], or `null` if not set. */
    fun resolve(name: String): String?
}
