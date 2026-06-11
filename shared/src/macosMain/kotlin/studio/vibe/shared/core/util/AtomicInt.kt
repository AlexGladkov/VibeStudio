package studio.vibe.shared.core.util

actual class AtomicInt actual constructor(initialValue: Int) {
    private val delegate = kotlin.concurrent.AtomicInt(initialValue)

    actual val value: Int get() = delegate.value
    actual fun incrementAndGet(): Int = delegate.incrementAndGet()
    actual fun get(): Int = delegate.value
}
