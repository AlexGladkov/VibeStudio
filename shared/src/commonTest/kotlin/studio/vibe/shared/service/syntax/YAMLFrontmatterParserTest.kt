package studio.vibe.shared.core.syntax

import studio.vibe.shared.core.common.LineContext
import studio.vibe.shared.core.common.SyntaxTokenKind
import studio.vibe.shared.core.common.YAMLFrontmatterParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YAMLFrontmatterParserTest {

    private val parser = YAMLFrontmatterParser()
    private val outside = LineContext.INITIAL
    private val inside = LineContext.INITIAL.copy(inFrontmatter = true)

    @Test
    fun supportedExtensions_isEmpty() {
        assertTrue(parser.supportedExtensions.isEmpty())
    }

    // ── delimiters ────────────────────────────────────────────────────────────────

    @Test
    fun parseLine_openDelimiter_emitsDelimiterToken_setsInFrontmatterTrue() {
        val result = parser.parseLine("---", 0, 3, outside)

        assertEquals(1, result.tokens.size)
        assertEquals(SyntaxTokenKind.FRONTMATTER_DELIMITER, result.tokens.first().kind)
        assertTrue(result.context.inFrontmatter)
    }

    @Test
    fun parseLine_closeDelimiter_emitsDelimiterToken_setsInFrontmatterFalse() {
        val result = parser.parseLine("---", 0, 3, inside)

        assertEquals(1, result.tokens.size)
        assertEquals(SyntaxTokenKind.FRONTMATTER_DELIMITER, result.tokens.first().kind)
        assertFalse(result.context.inFrontmatter)
    }

    // ── inside frontmatter ────────────────────────────────────────────────────────

    @Test
    fun parseLine_insideFrontmatter_keyValue_emitsBothTokens() {
        val line = "title: My Spec"
        val result = parser.parseLine(line, 0, line.length, inside)

        val kinds = result.tokens.map { it.kind }
        assertTrue(kinds.contains(SyntaxTokenKind.FRONTMATTER_KEY))
        assertTrue(kinds.contains(SyntaxTokenKind.FRONTMATTER_VALUE))
    }

    @Test
    fun parseLine_insideFrontmatter_keyOnly_emitsKeyToken() {
        val line = "key:"
        val result = parser.parseLine(line, 0, line.length, inside)

        // Only a key token — value after colon is empty
        assertTrue(result.tokens.any { it.kind == SyntaxTokenKind.FRONTMATTER_KEY })
    }

    @Test
    fun parseLine_insideFrontmatter_plainLine_emitsValueToken() {
        // Array item or continuation line with no colon
        val line = "  - item"
        val result = parser.parseLine(line, 0, line.length, inside)

        assertEquals(1, result.tokens.size)
        assertEquals(SyntaxTokenKind.FRONTMATTER_VALUE, result.tokens.first().kind)
    }

    @Test
    fun parseLine_outsideFrontmatter_plainLine_emitsNoTokens() {
        val result = parser.parseLine("regular markdown text", 0, 21, outside)

        assertTrue(result.tokens.isEmpty())
    }

    @Test
    fun parseLine_tokenOffsets_matchLineStartAndEnd() {
        val line = "author: Alice"
        val start = 10
        val end = 10 + line.length
        val result = parser.parseLine(line, start, end, inside)

        val keyToken = result.tokens.first { it.kind == SyntaxTokenKind.FRONTMATTER_KEY }
        assertEquals(start, keyToken.startOffset)

        val valueToken = result.tokens.first { it.kind == SyntaxTokenKind.FRONTMATTER_VALUE }
        assertTrue(valueToken.endOffset == end)
    }
}
