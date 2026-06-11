package studio.vibe.shared.core.util

/**
 * Multiplatform atomic integer.
 *
 * JVM: backed by [java.util.concurrent.atomic.AtomicInteger].
 * Kotlin/Native (macOS): backed by [kotlin.concurrent.AtomicInt].
 *
 * Use [incrementAndGet] for safe increment, [value] for read.
 */
expect class AtomicInt(initialValue: Int = 0) {
    val value: Int
    fun incrementAndGet(): Int
    fun get(): Int
}
