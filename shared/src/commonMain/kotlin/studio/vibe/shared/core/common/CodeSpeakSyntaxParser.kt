package studio.vibe.shared.core.common

import studio.vibe.shared.core.common.LineContext
import studio.vibe.shared.core.common.ParseLineResult
import studio.vibe.shared.core.common.SyntaxParsing
import studio.vibe.shared.core.common.SyntaxToken
import studio.vibe.shared.core.common.SyntaxTokenKind

/**
 * Composite parser for CodeSpeak `.cs.md` files.
 *
 * Delegates to [YAMLFrontmatterParser] for `---` blocks and [MarkdownSyntaxParser]
 * for standard markdown, then adds CodeSpeak-specific tokens:
 *
 * - `//` single-line comments ([SyntaxTokenKind.COMMENT])
 */
class CodeSpeakSyntaxParser : SyntaxParsing {

    override val supportedExtensions: List<String> = listOf("cs.md")

    private val frontmatterParser = YAMLFrontmatterParser()
    private val markdownParser = MarkdownSyntaxParser()

    override fun parseLine(
        line: String,
        lineStartOffset: Int,
        lineEndOffset: Int,
        context: LineContext,
    ): ParseLineResult {
        val trimmed = line.trim()

        // Frontmatter delimiter — delegate to frontmatter parser
        if (trimmed == "---") {
            return frontmatterParser.parseLine(line, lineStartOffset, lineEndOffset, context)
        }

        // Inside frontmatter — delegate to frontmatter parser
        if (context.inFrontmatter) {
            return frontmatterParser.parseLine(line, lineStartOffset, lineEndOffset, context)
        }

        // CodeSpeak // single-line comment
        if (trimmed.startsWith("//")) {
            return ParseLineResult(
                listOf(SyntaxToken(SyntaxTokenKind.COMMENT, lineStartOffset, lineEndOffset)),
                context,
            )
        }

        // Standard Markdown
        return markdownParser.parseLine(line, lineStartOffset, lineEndOffset, context)
    }
}
