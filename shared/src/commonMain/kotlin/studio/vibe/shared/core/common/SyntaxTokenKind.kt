package studio.vibe.shared.core.common

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
