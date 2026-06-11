package studio.vibe.shared.core.syntax

import studio.vibe.shared.core.common.LineContext
import studio.vibe.shared.core.common.CodeSpeakSyntaxParser
import studio.vibe.shared.core.common.SyntaxTokenKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodeSpeakSyntaxParserTest {

    private val parser = CodeSpeakSyntaxParser()
    private val ctx = LineContext.INITIAL

    @Test
    fun supportedExtensions_containsCsMd() {
        assertTrue(parser.supportedExtensions.contains("cs.md"))
    }

    @Test
    fun parseLine_frontmatterDelimiter_delegatesToFrontmatterParser() {
        val result = parser.parseLine("---", 0, 3, ctx)

        assertEquals(1, result.tokens.size)
        assertEquals(SyntaxTokenKind.FRONTMATTER_DELIMITER, result.tokens.first().kind)
    }

    @Test
    fun parseLine_insideFrontmatter_delegatesToFrontmatterParser() {
        // After first '---', context has inFrontmatter = true
        val fmCtx = ctx.copy(inFrontmatter = true)
        val result = parser.parseLine("title: My Spec", 0, 14, fmCtx)

        // Should have key and value tokens (YAML key: value)
        assertTrue(result.tokens.isNotEmpty())
    }

    @Test
    fun parseLine_codeSpeak_singleLineComment_emitsCommentToken() {
        val result = parser.parseLine("// this is a comment", 0, 20, ctx)

        assertEquals(1, result.tokens.size)
        assertEquals(SyntaxTokenKind.COMMENT, result.tokens.first().kind)
        assertEquals(0, result.tokens.first().startOffset)
        assertEquals(20, result.tokens.first().endOffset)
    }

    @Test
    fun parseLine_markdownHeading_delegatesToMarkdownParser() {
        val result = parser.parseLine("## Section Header", 0, 17, ctx)

        assertTrue(result.tokens.isNotEmpty())
        assertEquals(SyntaxTokenKind.HEADING, result.tokens.first().kind)
    }

    @Test
    fun parseLine_codeFence_delegatesToMarkdownParser() {
        val result = parser.parseLine("```kotlin", 0, 9, ctx)

        assertTrue(result.tokens.isNotEmpty())
        val kinds = result.tokens.map { it.kind }
        assertTrue(kinds.any { it == SyntaxTokenKind.CODE_BLOCK_FENCE })
    }

    @Test
    fun parseLine_commentIndented_stillRecognizedAsComment() {
        val line = "  // indented comment"
        val result = parser.parseLine(line, 0, line.length, ctx)

        assertEquals(1, result.tokens.size)
        assertEquals(SyntaxTokenKind.COMMENT, result.tokens.first().kind)
    }

    @Test
    fun parseLine_plainText_delegatesToMarkdownParser_contextUnchanged() {
        val result = parser.parseLine("plain paragraph text", 0, 20, ctx)

        // Context should not have been modified for a plain line
        assertFalse(result.context.inFrontmatter)
        assertFalse(result.context.inCodeBlock)
    }
}
