package studio.vibe.shared.contract

// Extensible token kind
data class SyntaxTokenKind(val rawValue: String) {
    companion object {
        // Markdown
        val HEADING = SyntaxTokenKind("heading")
        val BOLD = SyntaxTokenKind("bold")
        val ITALIC = SyntaxTokenKind("italic")
        val INLINE_CODE = SyntaxTokenKind("inlineCode")
        val CODE_BLOCK_FENCE = SyntaxTokenKind("codeBlockFence")
        val CODE_BLOCK_BODY = SyntaxTokenKind("codeBlockBody")
        val LINK = SyntaxTokenKind("link")
        val LINK_URL = SyntaxTokenKind("linkURL")
        val BLOCKQUOTE = SyntaxTokenKind("blockquote")
        val LIST_MARKER = SyntaxTokenKind("listMarker")
        val HORIZONTAL_RULE = SyntaxTokenKind("horizontalRule")

        // YAML Frontmatter
        val FRONTMATTER_DELIMITER = SyntaxTokenKind("frontmatterDelimiter")
        val FRONTMATTER_KEY = SyntaxTokenKind("frontmatterKey")
        val FRONTMATTER_VALUE = SyntaxTokenKind("frontmatterValue")

        // CodeSpeak-specific
        val CS_DIRECTIVE = SyntaxTokenKind("csDirective")
        val CS_FILE_REF = SyntaxTokenKind("csFileRef")

        // Generic
        val COMMENT = SyntaxTokenKind("comment")
        val PLAIN = SyntaxTokenKind("plain")
    }
}

// Token with range
data class SyntaxToken(
    val kind: SyntaxTokenKind,
    val startOffset: Int,
    val endOffset: Int,
)

// Line context for multiline parsing state
data class LineContext(
    val inFrontmatter: Boolean = false,
    val inCodeBlock: Boolean = false,
    val codeBlockFence: String? = null,
    val codeBlockLanguage: String? = null,
) {
    companion object {
        val INITIAL = LineContext()
    }
}

/** Result of parsing a single line. */
data class ParseLineResult(
    val tokens: List<SyntaxToken>,
    val context: LineContext,
)

// Syntax parser (line-by-line)
interface SyntaxParsing {
    val supportedExtensions: List<String>
    fun parseLine(
        line: String,
        lineStartOffset: Int,
        lineEndOffset: Int,
        context: LineContext,
    ): ParseLineResult
}

// Parser registry
interface SyntaxParserRegistering {
    fun register(parser: SyntaxParsing)

    /**
     * Unregisters all extensions associated with [parser] from the registry.
     *
     * @param fileExtension The extension key to remove (e.g. `"md"`, `"cs.md"`).
     * @return `true` if the extension was present and removed; `false` if it was not registered.
     */
    fun unregister(fileExtension: String): Boolean

    fun parser(fileExtension: String): SyntaxParsing?
}
