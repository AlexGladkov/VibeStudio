package studio.vibe.desktop.terminal

/**
 * Thread-safe scrollback buffer with a configurable size cap.
 *
 * Extracted from [DesktopTerminalService.receiveOutput] to isolate the
 * scrollback management logic and make it independently testable.
 *
 * Internally wraps a [StringBuilder] and uses `synchronized` for thread safety,
 * matching the original implementation.
 *
 * @param maxBytes      Soft cap on buffer size in bytes. When exceeded the oldest
 *                      content is trimmed to [targetBytes]. Default: 100 KB.
 * @param targetBytes   Size to trim to when cap is exceeded. Default: 80 KB.
 */
internal class ScrollbackBuffer(
    private val maxBytes: Int = 100_000,
    private val targetBytes: Int = 80_000,
) {
    private val buffer = StringBuilder()

    /**
     * Appends [chunk] to the buffer.
     *
     * When the buffer length exceeds [maxBytes] the oldest content is dropped
     * so that the buffer stays near [targetBytes].
     */
    fun append(chunk: String) {
        synchronized(buffer) {
            buffer.append(chunk)
            if (buffer.length > maxBytes) {
                buffer.delete(0, buffer.length - targetBytes)
            }
        }
    }

    /** Returns a snapshot of the current scrollback content. */
    fun content(): String = synchronized(buffer) { buffer.toString() }

    /** Clears all buffered content. */
    fun clear() = synchronized(buffer) { buffer.clear() }
}
