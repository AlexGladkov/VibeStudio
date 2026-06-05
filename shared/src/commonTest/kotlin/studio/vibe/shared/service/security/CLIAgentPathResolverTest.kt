package studio.vibe.shared.service.security

import studio.vibe.shared.contract.BinaryResolver
import studio.vibe.shared.model.FilePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CLIAgentPathResolverTest {

    private class FakeBinaryResolver(private val known: Map<String, String>) : BinaryResolver {
        override fun findExecutable(name: String): FilePath? =
            known[name]?.let { FilePath(it) }
    }

    @Test
    fun resolve_knownBinary_returnsAbsolutePath() {
        val resolver = CLIAgentPathResolverImpl(FakeBinaryResolver(mapOf("claude" to "/usr/bin/claude")))

        val path = resolver.resolve("claude")

        assertEquals("/usr/bin/claude", path)
    }

    @Test
    fun resolve_unknownBinary_returnsNull() {
        val resolver = CLIAgentPathResolverImpl(FakeBinaryResolver(emptyMap()))

        val path = resolver.resolve("notexist")

        assertNull(path)
    }

    @Test
    fun resolve_emptyName_returnsNull() {
        val resolver = CLIAgentPathResolverImpl(FakeBinaryResolver(emptyMap()))

        assertNull(resolver.resolve(""))
    }

    @Test
    fun resolve_nameWithSlash_returnsNull() {
        val resolver = CLIAgentPathResolverImpl(FakeBinaryResolver(mapOf("/usr/bin/claude" to "/usr/bin/claude")))

        assertNull(resolver.resolve("/usr/bin/claude"))
    }

    @Test
    fun resolve_nameWithDotDot_returnsNull() {
        val resolver = CLIAgentPathResolverImpl(FakeBinaryResolver(emptyMap()))

        assertNull(resolver.resolve("../../etc/passwd"))
    }

    @Test
    fun resolve_emptyPath_returnsNullEvenWhenBinaryResolverKnowsIt() {
        // Resolver with empty string mapped — but validator rejects empty names
        val resolver = CLIAgentPathResolverImpl(FakeBinaryResolver(mapOf("" to "/somewhere")))

        assertNull(resolver.resolve(""))
    }
}
