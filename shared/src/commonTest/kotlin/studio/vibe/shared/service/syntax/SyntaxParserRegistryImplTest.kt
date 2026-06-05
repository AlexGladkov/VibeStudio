package studio.vibe.shared.service.syntax

import studio.vibe.shared.contract.LineContext
import studio.vibe.shared.contract.ParseLineResult
import studio.vibe.shared.contract.SyntaxParsing
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SyntaxParserRegistryImplTest {

    private fun stubParser(vararg exts: String): SyntaxParsing = object : SyntaxParsing {
        override val supportedExtensions = exts.toList()
        override fun parseLine(line: String, lineStartOffset: Int, lineEndOffset: Int, context: LineContext): ParseLineResult =
            ParseLineResult(emptyList(), context)
    }

    @Test
    fun parser_unregistered_returnsNull() {
        val registry = SyntaxParserRegistryImpl()

        assertNull(registry.parser("kt"))
    }

    @Test
    fun register_andLookupByExactExtension_returnsParser() {
        val registry = SyntaxParserRegistryImpl()
        val p = stubParser("md")

        registry.register(p)

        assertSame(p, registry.parser("md"))
    }

    @Test
    fun parser_compoundExtension_preferredOverSimple() {
        val registry = SyntaxParserRegistryImpl()
        val mdParser = stubParser("md")
        val csMdParser = stubParser("cs.md")

        registry.register(mdParser)
        registry.register(csMdParser)

        // "cs.md" must resolve to the compound parser, not the plain "md" one
        assertSame(csMdParser, registry.parser("cs.md"))
    }

    @Test
    fun parser_simpleExtension_fallsBackFromCompound() {
        val registry = SyntaxParserRegistryImpl()
        val mdParser = stubParser("md")
        val csMdParser = stubParser("cs.md")

        registry.register(mdParser)
        registry.register(csMdParser)

        // Looking up "md" should still find the md parser
        assertSame(mdParser, registry.parser("md"))
    }

    @Test
    fun parser_lookupCaseInsensitive() {
        val registry = SyntaxParserRegistryImpl()
        val p = stubParser("MD")

        registry.register(p)

        assertNotNull(registry.parser("md"))
        assertNotNull(registry.parser("MD"))
    }

    @Test
    fun unregister_knownExtension_returnsTrue_andRemovesParser() {
        val registry = SyntaxParserRegistryImpl()
        registry.register(stubParser("md"))

        val removed = registry.unregister("md")

        assertTrue(removed)
        assertNull(registry.parser("md"))
    }

    @Test
    fun unregister_unknownExtension_returnsFalse() {
        val registry = SyntaxParserRegistryImpl()

        val removed = registry.unregister("nonexistent")

        assertFalse(removed)
    }

    @Test
    fun register_parserWithMultipleExtensions_allExtensionsResolvable() {
        val registry = SyntaxParserRegistryImpl()
        val p = stubParser("yaml", "yml")

        registry.register(p)

        assertSame(p, registry.parser("yaml"))
        assertSame(p, registry.parser("yml"))
    }

    @Test
    fun parser_fallbackToLastDotComponent() {
        // Register only "md"; lookup of compound "foo.md" should fallback to "md"
        val registry = SyntaxParserRegistryImpl()
        val p = stubParser("md")
        registry.register(p)

        assertSame(p, registry.parser("foo.md"))
    }

    // ── P2: null extension — file with no dot ─────────────────────────────────

    @Test
    fun parser_nullExtension_fileWithNoDot_returnsNull() {
        // Arrange — register a parser for "md" and "kt"
        val registry = SyntaxParserRegistryImpl()
        registry.register(stubParser("md"))
        registry.register(stubParser("kt"))

        // Act — lookup with an extension string that has no dot component
        // (simulates a file like "Makefile", "Dockerfile", "LICENSE")
        val result = registry.parser("Makefile")

        // Assert — no registered parser matches; must return null
        assertNull(result, "A file name with no dot extension must resolve to null")
    }

    @Test
    fun parser_emptyExtension_returnsNull() {
        // Arrange
        val registry = SyntaxParserRegistryImpl()
        registry.register(stubParser("md"))

        // Act
        val result = registry.parser("")

        // Assert
        assertNull(result, "Empty extension must resolve to null")
    }
}
