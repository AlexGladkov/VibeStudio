package studio.vibe.shared.feature.remote.data

/**
 * Platform-injectable structured logger.
 *
 * Platform implementations map these calls to the appropriate system logging facility:
 * - macOS/iOS: `os_log` / `Logger`
 * - JVM: `java.util.logging` or SLF4J
 * - Browser: `console.*`
 */
interface PlatformLogger {
    fun info(tag: String, message: String)
    fun warning(tag: String, message: String)
    fun error(tag: String, message: String)
}
