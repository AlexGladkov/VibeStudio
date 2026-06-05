package studio.vibe.shared.service.syntax

import studio.vibe.shared.contract.LineContext
import studio.vibe.shared.contract.ParseLineResult
import studio.vibe.shared.contract.SyntaxParsing
import studio.vibe.shared.contract.SyntaxToken
import studio.vibe.shared.contract.SyntaxTokenKind

/**
 * Parses YAML frontmatter between `---` delimiters.
 *
 * Emits [SyntaxTokenKind.FRONTMATTER_DELIMITER], [SyntaxTokenKind.FRONTMATTER_KEY],
 * and [SyntaxTokenKind.FRONTMATTER_VALUE] tokens.
 *
 * Not a standalone parser — used as a sub-parser by [CodeSpeakSyntaxParser]
 * and any future composite parser that needs frontmatter support.
 * [supportedExtensions] is intentionally empty.
 */
class YAMLFrontmatterParser : SyntaxParsing {

    override val supportedExtensions: List<String> = emptyList()

    override fun parseLine(
        line: String,
        lineStartOffset: Int,
        lineEndOffset: Int,
        context: LineContext,
    ): ParseLineResult {
        val trimmed = line.trim()
        var ctx = context

        // Frontmatter delimiter `---`
        if (trimmed == "---") {
            ctx = ctx.copy(inFrontmatter = !ctx.inFrontmatter)
            return ParseLineResult(
                listOf(SyntaxToken(SyntaxTokenKind.FRONTMATTER_DELIMITER, lineStartOffset, lineEndOffset)),
                ctx,
            )
        }

        if (!ctx.inFrontmatter) {
            return ParseLineResult(emptyList(), ctx)
        }

        // Key: value parsing
        val colonIndex = line.indexOf(':')
        if (colonIndex >= 0) {
            val tokens = mutableListOf<SyntaxToken>()

            // Key token: from line start to colon (exclusive)
            val keyEnd = lineStartOffset + colonIndex
            if (keyEnd > lineStartOffset) {
                tokens.add(SyntaxToken(SyntaxTokenKind.FRONTMATTER_KEY, lineStartOffset, keyEnd))
            }

            // Value token: from after the colon to end of line
            val valueStart = lineStartOffset + colonIndex + 1
            if (valueStart < lineEndOffset) {
                tokens.add(SyntaxToken(SyntaxTokenKind.FRONTMATTER_VALUE, valueStart, lineEndOffset))
            }

            return ParseLineResult(tokens, ctx)
        }

        // Plain frontmatter line (array items, continuation lines, etc.)
        return ParseLineResult(
            listOf(SyntaxToken(SyntaxTokenKind.FRONTMATTER_VALUE, lineStartOffset, lineEndOffset)),
            ctx,
        )
    }
}
