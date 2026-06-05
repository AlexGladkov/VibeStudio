package studio.vibe.shared.util

actual class AtomicInt actual constructor(initialValue: Int) {
    private val delegate = java.util.concurrent.atomic.AtomicInteger(initialValue)

    actual val value: Int get() = delegate.get()
    actual fun incrementAndGet(): Int = delegate.incrementAndGet()
    actual fun get(): Int = delegate.get()
}
