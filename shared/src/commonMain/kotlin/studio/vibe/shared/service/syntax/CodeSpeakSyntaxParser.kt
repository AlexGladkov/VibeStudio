package studio.vibe.shared.service.syntax

import studio.vibe.shared.contract.LineContext
import studio.vibe.shared.contract.SyntaxParsing
import studio.vibe.shared.contract.SyntaxToken
import studio.vibe.shared.contract.SyntaxTokenKind

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
    ): Pair<List<SyntaxToken>, LineContext> {
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
            return listOf(SyntaxToken(SyntaxTokenKind.COMMENT, lineStartOffset, lineEndOffset)) to context
        }

        // Standard Markdown
        return markdownParser.parseLine(line, lineStartOffset, lineEndOffset, context)
    }
}
