package studio.vibe.desktop.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [ScrollbackBuffer].
 *
 * Covers the four stated scenarios:
 * 1. append + content round-trip
 * 2. trim when buffer exceeds the cap
 * 3. clear empties the buffer
 * 4. concurrent appends are safe
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrollbackBufferTest {

    // ── 1. Append + content round-trip ────────────────────────────────────────

    @Test
    fun `append followed by content returns the accumulated text`() {
        val buf = ScrollbackBuffer(maxBytes = 1_000, targetBytes = 800)

        buf.append("hello")
        buf.append(" ")
        buf.append("world")

        assertEquals("hello world", buf.content())
    }

    @Test
    fun `content is empty before any append`() {
        val buf = ScrollbackBuffer()
        assertEquals("", buf.content())
    }

    // ── 2. Trim when over cap ────────────────────────────────────────────────

    @Test
    fun `buffer trims oldest content when maxBytes is exceeded`() {
        // cap = 10 chars, target = 5 chars
        val buf = ScrollbackBuffer(maxBytes = 10, targetBytes = 5)

        // Fill to exactly the cap — no trim yet.
        buf.append("0123456789") // 10 chars
        assertEquals(10, buf.content().length)

        // One more char pushes over the cap → trimmed to last 5 chars.
        buf.append("X")
        val content = buf.content()
        assertEquals(5, content.length, "Buffer should be trimmed to targetBytes")
        assertTrue(content.endsWith("X"), "Newest content must be retained after trim")
    }

    @Test
    fun `multiple appends that cross cap only retain the tail`() {
        val buf = ScrollbackBuffer(maxBytes = 20, targetBytes = 10)

        buf.append("AAAAAAAAAA") // 10 chars — under cap
        buf.append("BBBBBBBBBB") // 10 chars — total 20, still at cap
        buf.append("C")          // 21 chars — triggers trim to last 10

        val content = buf.content()
        assertEquals(10, content.length)
        assertTrue(content.endsWith("C"), "Newest character must be present after trim")
        assertTrue(
            content.all { it == 'B' || it == 'C' },
            "Only the tail (B and C) should remain; oldest A content must be dropped",
        )
    }

    // ── 3. Clear empties the buffer ───────────────────────────────────────────

    @Test
    fun `clear removes all content`() {
        val buf = ScrollbackBuffer(maxBytes = 1_000, targetBytes = 800)
        buf.append("some content that should disappear")
        buf.clear()
        assertEquals("", buf.content())
    }

    @Test
    fun `append after clear works normally`() {
        val buf = ScrollbackBuffer(maxBytes = 1_000, targetBytes = 800)
        buf.append("first")
        buf.clear()
        buf.append("second")
        assertEquals("second", buf.content())
    }

    // ── 4. Concurrent appends are thread-safe ─────────────────────────────────

    /**
     * Spawns 50 coroutines each appending 200 single-char chunks.
     * After all coroutines complete the buffer must be internally consistent:
     * - No exception thrown
     * - Content length <= maxBytes (cap invariant not violated)
     * - content() returns a non-null string (no partial StringBuilder state)
     */
    @Test
    fun `concurrent appends do not corrupt the buffer`() = runTest {
        val maxBytes = 10_000
        val targetBytes = 8_000
        val buf = ScrollbackBuffer(maxBytes = maxBytes, targetBytes = targetBytes)

        val jobs = (1..50).map {
            launch(Dispatchers.Default) {
                repeat(200) { buf.append("X") }
            }
        }
        jobs.forEach { it.join() }

        val content = buf.content()
        assertTrue(
            content.length <= maxBytes,
            "Buffer length ${content.length} exceeds maxBytes $maxBytes after concurrent appends",
        )
        // Verify every character is the expected 'X' — no internal corruption.
        assertTrue(
            content.all { it == 'X' },
            "Buffer contains unexpected characters — internal state is corrupted",
        )
    }
}
