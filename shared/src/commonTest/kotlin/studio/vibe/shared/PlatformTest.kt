package studio.vibe.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun platformNameNotBlank() {
        assertTrue(platformName().isNotBlank())
    }
}
