package studio.vibe.shared.service.syntax

import studio.vibe.shared.contract.LineContext
import studio.vibe.shared.contract.ParseLineResult
import studio.vibe.shared.contract.SyntaxParsing
import studio.vibe.shared.contract.SyntaxToken
import studio.vibe.shared.contract.SyntaxTokenKind

/**
 * Line-by-line Markdown syntax parser.
 *
 * Handles headings (#–######), bold (**text** or __text__), italic (*text* or _text_),
 * inline code (`text`), code fence blocks (``` or ~~~), links ([text](url)),
 * blockquotes (>), list markers (- * + and ordered), and horizontal rules (---).
 * Multiline state (code blocks) is carried via [LineContext].
 */
class MarkdownSyntaxParser : SyntaxParsing {

    override val supportedExtensions: List<String> = listOf("md", "markdown")

    override fun parseLine(
        line: String,
        lineStartOffset: Int,
        lineEndOffset: Int,
        context: LineContext,
    ): ParseLineResult {
        var ctx = context
        val trimmed = line.trim()

        // Code fence toggle (``` or ~~~)
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            val fence = trimmed.take(3)
            ctx = if (ctx.inCodeBlock && ctx.codeBlockFence == fence) {
                ctx.copy(inCodeBlock = false, codeBlockFence = null, codeBlockLanguage = null)
            } else if (!ctx.inCodeBlock) {
                val lang = trimmed.drop(3).trim().takeIf { it.isNotEmpty() }
                ctx.copy(inCodeBlock = true, codeBlockFence = fence, codeBlockLanguage = lang)
            } else {
                ctx
            }
            return ParseLineResult(
                listOf(SyntaxToken(SyntaxTokenKind.CODE_BLOCK_FENCE, lineStartOffset, lineEndOffset)),
                ctx,
            )
        }

        // Inside code block — whole line is code body
        if (ctx.inCodeBlock) {
            return ParseLineResult(
                listOf(SyntaxToken(SyntaxTokenKind.CODE_BLOCK_BODY, lineStartOffset, lineEndOffset)),
                ctx,
            )
        }

        // Horizontal rule: line of only --- or *** or ___ (3+)
        if (isHorizontalRule(trimmed)) {
            return ParseLineResult(
                listOf(SyntaxToken(SyntaxTokenKind.HORIZONTAL_RULE, lineStartOffset, lineEndOffset)),
                ctx,
            )
        }

        // Heading: # through ######
        headingToken(line, lineStartOffset, lineEndOffset)?.let { token ->
            return ParseLineResult(listOf(token), ctx)
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            return ParseLineResult(
                listOf(SyntaxToken(SyntaxTokenKind.BLOCKQUOTE, lineStartOffset, lineEndOffset)),
                ctx,
            )
        }

        // List marker
        listMarkerToken(line, lineStartOffset, lineEndOffset)?.let { token ->
            return ParseLineResult(listOf(token), ctx)
        }

        // Inline tokens (bold, italic, inline code, links)
        val inlineTokens = parseInline(line, lineStartOffset)
        return ParseLineResult(inlineTokens, ctx)
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun isHorizontalRule(trimmed: String): Boolean {
        if (trimmed.length < 3) return false
        val ch = trimmed[0]
        if (ch != '-' && ch != '*' && ch != '_') return false
        return trimmed.all { it == ch || it == ' ' } && trimmed.count { it == ch } >= 3
    }

    private fun headingToken(line: String, lineStart: Int, lineEnd: Int): SyntaxToken? {
        if (!line.startsWith("#")) return null
        var count = 0
        for (ch in line) {
            if (ch == '#') count++ else break
        }
        if (count > 6) return null
        val afterHashes = line.drop(count)
        if (!afterHashes.startsWith(" ")) return null
        return SyntaxToken(SyntaxTokenKind.HEADING, lineStart, lineEnd)
    }

    private fun listMarkerToken(line: String, lineStart: Int, lineEnd: Int): SyntaxToken? {
        val trimmed = line.trimStart(' ', '\t')
        val isUnordered = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")
        val isOrdered = trimmed.firstOrNull()?.isDigit() == true && trimmed.contains(". ")

        if (!isUnordered && !isOrdered) return null

        val leadingSpaces = line.length - trimmed.length

        val markerLen: Int = if (isUnordered) {
            2
        } else {
            val dotIndex = trimmed.indexOf('.')
            if (dotIndex < 0) return null
            dotIndex + 2
        }

        val available = (lineEnd - lineStart) - leadingSpaces
        if (available <= 0) return null

        val markerStart = lineStart + leadingSpaces
        val markerEnd = markerStart + minOf(markerLen, available)
        return SyntaxToken(SyntaxTokenKind.LIST_MARKER, markerStart, markerEnd)
    }

    private fun parseInline(line: String, lineStart: Int): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        val length = line.length
        var i = 0

        while (i < length) {
            val ch = line[i]

            // Bold **text** or __text__
            if (i + 1 < length) {
                val pair = line.substring(i, i + 2)
                if (pair == "**" || pair == "__") {
                    val endIdx = findClosing(pair, line, i + 2)
                    if (endIdx != null) {
                        val tokenEnd = lineStart + endIdx + 2
                        tokens.add(SyntaxToken(SyntaxTokenKind.BOLD, lineStart + i, tokenEnd))
                        i = endIdx + 2
                        continue
                    }
                }
            }

            // Italic *text* or _text_
            if (ch == '*' || ch == '_') {
                val marker = ch.toString()
                val endIdx = findClosing(marker, line, i + 1)
                if (endIdx != null) {
                    val tokenEnd = lineStart + endIdx + 1
                    tokens.add(SyntaxToken(SyntaxTokenKind.ITALIC, lineStart + i, tokenEnd))
                    i = endIdx + 1
                    continue
                }
            }

            // Inline code `text`
            if (ch == '`') {
                val endIdx = findClosing("`", line, i + 1)
                if (endIdx != null) {
                    val tokenEnd = lineStart + endIdx + 1
                    tokens.add(SyntaxToken(SyntaxTokenKind.INLINE_CODE, lineStart + i, tokenEnd))
                    i = endIdx + 1
                    continue
                }
            }

            // Link [text](url)
            if (ch == '[') {
                val linkResult = parseLinkAt(i, line, lineStart)
                if (linkResult != null) {
                    val (linkToken, urlToken) = linkResult
                    tokens.add(linkToken)
                    tokens.add(urlToken)
                    i = urlToken.endOffset - lineStart
                    continue
                }
            }

            i++
        }

        return tokens
    }

    /** Returns the index of the first occurrence of [marker] in [line] at or after [start]. */
    private fun findClosing(marker: String, line: String, start: Int): Int? {
        val markerLen = marker.length
        val limit = line.length - markerLen + 1
        var i = start
        while (i < limit) {
            if (line.regionMatches(i, marker, 0, markerLen)) return i
            i++
        }
        return null
    }

    /**
     * Parse a markdown link starting at [start] (the '[' character).
     *
     * @return Pair of (linkToken covering [text], urlToken covering (url)) or null.
     */
    private fun parseLinkAt(start: Int, line: String, lineStart: Int): Pair<SyntaxToken, SyntaxToken>? {
        val length = line.length

        // Find closing ]
        val closeBracket = (start + 1 until length).firstOrNull { line[it] == ']' } ?: return null

        // Must be followed by (
        if (closeBracket + 1 >= length || line[closeBracket + 1] != '(') return null

        // Find closing )
        val closeParen = (closeBracket + 2 until length).firstOrNull { line[it] == ')' } ?: return null

        // [text] token — covers from '[' to ']' inclusive
        val linkToken = SyntaxToken(
            kind = SyntaxTokenKind.LINK,
            startOffset = lineStart + start,
            endOffset = lineStart + closeBracket + 1,
        )
        // (url) token — covers from '(' to ')' inclusive
        val urlToken = SyntaxToken(
            kind = SyntaxTokenKind.LINK_URL,
            startOffset = lineStart + closeBracket + 1,
            endOffset = lineStart + closeParen + 1,
        )
        return linkToken to urlToken
    }
}
