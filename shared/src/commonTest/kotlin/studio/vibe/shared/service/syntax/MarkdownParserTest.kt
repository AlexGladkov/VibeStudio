package studio.vibe.shared.service.syntax

import studio.vibe.shared.contract.LineContext
import studio.vibe.shared.contract.SyntaxTokenKind
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [MarkdownSyntaxParser].
 *
 * Each test drives [MarkdownSyntaxParser.parseLine] with a known input and
 * asserts the returned token kinds and context transitions.
 *
 * Test organisation:
 *  - Headings (#1–#6)
 *  - Code fence open / body / close
 *  - Horizontal rules
 *  - Blockquotes
 *  - List markers (unordered + ordered)
 *  - Inline tokens (bold, italic, inline code, links)
 *  - Empty and plain lines
 */
class MarkdownParserTest {

    private lateinit var parser: MarkdownSyntaxParser

    @BeforeTest
    fun setup() {
        parser = MarkdownSyntaxParser()
    }

    // ── Meta ──────────────────────────────────────────────────────────────────

    @Test
    fun supportedExtensions_containsMdAndMarkdown() {
        assertTrue("md" in parser.supportedExtensions)
        assertTrue("markdown" in parser.supportedExtensions)
    }

    // ── Headings ──────────────────────────────────────────────────────────────

    @Test
    fun heading_h1_isTokenised() {
        val (tokens, _) = parser.parseLine("# Hello World", 0, 13, LineContext.INITIAL)
        assertEquals(1, tokens.size)
        assertEquals(SyntaxTokenKind.HEADING, tokens[0].kind)
    }

    @Test
    fun heading_h6_isTokenised() {
        val (tokens, _) = parser.parseLine("###### Deep Heading", 0, 19, LineContext.INITIAL)
        assertEquals(1, tokens.size)
        assertEquals(SyntaxTokenKind.HEADING, tokens[0].kind)
    }

    @Test
    fun heading_sevenHashes_isNotAHeading() {
        val (tokens, _) = parser.parseLine("####### Not a heading", 0, 21, LineContext.INITIAL)
        // Seven hashes exceeds the ATX heading spec — should NOT produce a HEADING token.
        assertTrue(tokens.none { it.kind == SyntaxTokenKind.HEADING })
    }

    @Test
    fun heading_hashWithoutSpace_isNotAHeading() {
        val (tokens, _) = parser.parseLine("#NoSpace", 0, 8, LineContext.INITIAL)
        assertTrue(tokens.none { it.kind == SyntaxTokenKind.HEADING })
    }

    @Test
    fun heading_token_spansFullLine() {
        val line = "## Section Title"
        val (tokens, _) = parser.parseLine(line, 0, line.length, LineContext.INITIAL)
        assertEquals(SyntaxTokenKind.HEADING, tokens[0].kind)
        assertEquals(0, tokens[0].startOffset)
        assertEquals(line.length, tokens[0].endOffset)
    }

    // ── Code fences ───────────────────────────────────────────────────────────

    @Test
    fun codeFenceOpen_tripleBacktick_producesCodeBlockFenceToken() {
        val (tokens, ctx) = parser.parseLine("```kotlin", 0, 9, LineContext.INITIAL)
        assertEquals(SyntaxTokenKind.CODE_BLOCK_FENCE, tokens[0].kind)
        assertTrue(ctx.inCodeBlock, "Context must be inside code block after opening fence")
        assertEquals("```", ctx.codeBlockFence)
        assertEquals("kotlin", ctx.codeBlockLanguage)
    }

    @Test
    fun codeFenceOpen_tildeFence_producesCodeBlockFenceToken() {
        val (tokens, ctx) = parser.parseLine("~~~", 0, 3, LineContext.INITIAL)
        assertEquals(SyntaxTokenKind.CODE_BLOCK_FENCE, tokens[0].kind)
        assertTrue(ctx.inCodeBlock)
        assertEquals("~~~", ctx.codeBlockFence)
    }

    @Test
    fun codeFenceBody_insideBlock_producesCodeBlockBodyToken() {
        val openCtx = LineContext(inCodeBlock = true, codeBlockFence = "```")
        val (tokens, ctx) = parser.parseLine("val x = 42", 0, 10, openCtx)
        assertEquals(SyntaxTokenKind.CODE_BLOCK_BODY, tokens[0].kind)
        assertTrue(ctx.inCodeBlock, "Context must stay inside code block on body line")
    }

    @Test
    fun codeFenceClose_matchingFence_closesBlock() {
        val openCtx = LineContext(inCodeBlock = true, codeBlockFence = "```")
        val (tokens, ctx) = parser.parseLine("```", 0, 3, openCtx)
        assertEquals(SyntaxTokenKind.CODE_BLOCK_FENCE, tokens[0].kind)
        assertFalse(ctx.inCodeBlock, "Context must be outside code block after closing fence")
    }

    @Test
    fun codeFenceClose_mismatchedFence_doesNotClose() {
        // Opening was ~~~, attempting to close with ``` must not close.
        val openCtx = LineContext(inCodeBlock = true, codeBlockFence = "~~~")
        val (_, ctx) = parser.parseLine("```", 0, 3, openCtx)
        assertTrue(ctx.inCodeBlock, "Mismatched fence must not close the code block")
    }

    @Test
    fun codeFenceOpen_withNoLanguage_hasNullLanguage() {
        val (_, ctx) = parser.parseLine("```", 0, 3, LineContext.INITIAL)
        assertEquals(null, ctx.codeBlockLanguage)
    }

    // ── Horizontal rule ───────────────────────────────────────────────────────

    @Test
    fun horizontalRule_threeDashes_producesToken() {
        val (tokens, _) = parser.parseLine("---", 0, 3, LineContext.INITIAL)
        assertEquals(1, tokens.size)
        assertEquals(SyntaxTokenKind.HORIZONTAL_RULE, tokens[0].kind)
    }

    @Test
    fun horizontalRule_threeAsterisks_producesToken() {
        val (tokens, _) = parser.parseLine("***", 0, 3, LineContext.INITIAL)
        assertEquals(SyntaxTokenKind.HORIZONTAL_RULE, tokens[0].kind)
    }

    @Test
    fun horizontalRule_threeUnderscores_producesToken() {
        val (tokens, _) = parser.parseLine("___", 0, 3, LineContext.INITIAL)
        assertEquals(SyntaxTokenKind.HORIZONTAL_RULE, tokens[0].kind)
    }

    @Test
    fun horizontalRule_twoDashes_isNotRecognised() {
        val (tokens, _) = parser.parseLine("--", 0, 2, LineContext.INITIAL)
        assertTrue(tokens.none { it.kind == SyntaxTokenKind.HORIZONTAL_RULE })
    }

    // ── Blockquote ────────────────────────────────────────────────────────────

    @Test
    fun blockquote_leadingAngleBracket_producesToken() {
        val (tokens, _) = parser.parseLine("> This is a quote", 0, 18, LineContext.INITIAL)
        assertEquals(1, tokens.size)
        assertEquals(SyntaxTokenKind.BLOCKQUOTE, tokens[0].kind)
    }

    @Test
    fun blockquote_withLeadingSpace_isRecognised() {
        // Trimmed line starts with '>'
        val (tokens, _) = parser.parseLine("  > indented quote", 0, 18, LineContext.INITIAL)
        assertEquals(SyntaxTokenKind.BLOCKQUOTE, tokens[0].kind)
    }

    // ── List markers ──────────────────────────────────────────────────────────

    @Test
    fun listMarker_unorderedDash_producesToken() {
        val (tokens, _) = parser.parseLine("- item", 0, 6, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.LIST_MARKER })
    }

    @Test
    fun listMarker_unorderedStar_producesToken() {
        val (tokens, _) = parser.parseLine("* item", 0, 6, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.LIST_MARKER })
    }

    @Test
    fun listMarker_unorderedPlus_producesToken() {
        val (tokens, _) = parser.parseLine("+ item", 0, 6, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.LIST_MARKER })
    }

    @Test
    fun listMarker_orderedNumber_producesToken() {
        val (tokens, _) = parser.parseLine("1. First item", 0, 13, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.LIST_MARKER })
    }

    @Test
    fun listMarker_indented_producesToken() {
        val (tokens, _) = parser.parseLine("  - nested item", 0, 15, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.LIST_MARKER })
    }

    // ── Inline bold ───────────────────────────────────────────────────────────

    @Test
    fun bold_doubleAsterisk_producesToken() {
        val (tokens, _) = parser.parseLine("**bold text**", 0, 13, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.BOLD })
    }

    @Test
    fun bold_doubleUnderscore_producesToken() {
        val (tokens, _) = parser.parseLine("__bold__", 0, 8, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.BOLD })
    }

    // ── Inline italic ─────────────────────────────────────────────────────────

    @Test
    fun italic_singleAsterisk_producesToken() {
        val (tokens, _) = parser.parseLine("*italic*", 0, 8, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.ITALIC })
    }

    @Test
    fun italic_singleUnderscore_producesToken() {
        val (tokens, _) = parser.parseLine("_italic_", 0, 8, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.ITALIC })
    }

    // ── Inline code ───────────────────────────────────────────────────────────

    @Test
    fun inlineCode_backtick_producesToken() {
        val (tokens, _) = parser.parseLine("Use `val x = 1` here", 0, 20, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.INLINE_CODE })
    }

    // ── Links ─────────────────────────────────────────────────────────────────

    @Test
    fun link_markdownSyntax_producesTwoTokens() {
        val line = "[click here](https://example.com)"
        val (tokens, _) = parser.parseLine(line, 0, line.length, LineContext.INITIAL)
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.LINK }, "Expected LINK token")
        assertTrue(tokens.any { it.kind == SyntaxTokenKind.LINK_URL }, "Expected LINK_URL token")
    }

    // ── Empty / plain lines ───────────────────────────────────────────────────

    @Test
    fun emptyLine_producesNoTokens() {
        val (tokens, ctx) = parser.parseLine("", 0, 0, LineContext.INITIAL)
        assertTrue(tokens.isEmpty())
        assertFalse(ctx.inCodeBlock)
    }

    @Test
    fun plainTextLine_producesNoSpecialTokens() {
        val (tokens, _) = parser.parseLine("Just some plain text.", 0, 21, LineContext.INITIAL)
        assertTrue(tokens.none { it.kind == SyntaxTokenKind.HEADING })
        assertTrue(tokens.none { it.kind == SyntaxTokenKind.CODE_BLOCK_FENCE })
        assertTrue(tokens.none { it.kind == SyntaxTokenKind.BLOCKQUOTE })
    }

    // ── Context immutability ──────────────────────────────────────────────────

    @Test
    fun initialContext_inCodeBlock_isFalse() {
        assertFalse(LineContext.INITIAL.inCodeBlock)
    }

    @Test
    fun context_afterOpenFence_isInsideCodeBlock() {
        val (_, ctx) = parser.parseLine("```", 0, 3, LineContext.INITIAL)
        assertTrue(ctx.inCodeBlock)
    }

    // ── P1: LineContext reset between files ───────────────────────────────────

    @Test
    fun lineContext_resetBetweenFiles_secondFileStartsFresh() {
        // Arrange — first "file" ends in the middle of a code fence (never closed).
        // The context returned by the last line of file 1 will have inCodeBlock=true.
        val (_, ctxAfterFile1) = parser.parseLine("```kotlin", 0, 9, LineContext.INITIAL)
        assertTrue(ctxAfterFile1.inCodeBlock, "Sanity: context must be inside block after open fence")

        // When parsing a second file the caller must pass LineContext.INITIAL (not the
        // accumulated context from file 1). Verify that the parser behaves correctly when
        // given a fresh context — it must not carry over the previous file's state.
        val freshLine = "# Section header"
        val (tokensFile2, ctxFile2) = parser.parseLine(freshLine, 0, freshLine.length, LineContext.INITIAL)

        // Assert — the second file starts fresh: the heading must be tokenised as HEADING,
        // not as CODE_BLOCK_BODY (which would happen if the old context leaked through).
        assertEquals(SyntaxTokenKind.HEADING, tokensFile2.firstOrNull()?.kind,
            "First line of second file must be parsed as HEADING, not CODE_BLOCK_BODY")
        assertFalse(ctxFile2.inCodeBlock, "Second file must start outside of any code block")
    }

    @Test
    fun lineContext_midFencePreviousFile_bodyLineInSecondFile_notMarkedAsCodeBlock() {
        // Arrange — simulate a parser instance reused across two files.
        // File 1: open a fence, never close it.
        val openCtx = LineContext(inCodeBlock = true, codeBlockFence = "```")

        // File 2: caller correctly passes LineContext.INITIAL (reset).
        val bodyLine = "just plain text"
        val (tokens, ctx) = parser.parseLine(bodyLine, 0, bodyLine.length, LineContext.INITIAL)

        // Assert — with a fresh context, "just plain text" is NOT parsed as CODE_BLOCK_BODY.
        assertTrue(tokens.none { it.kind == SyntaxTokenKind.CODE_BLOCK_BODY },
            "Plain text with fresh context must not be CODE_BLOCK_BODY")
        assertFalse(ctx.inCodeBlock)
    }
}
