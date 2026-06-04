package studio.vibe.desktop.ui.codespeak

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.shared.contract.LineContext
import studio.vibe.shared.contract.SyntaxTokenKind
import studio.vibe.shared.service.syntax.CodeSpeakSyntaxParser

internal fun highlightCodeSpeak(content: String, colors: DSColors): AnnotatedString {
    val parser = CodeSpeakSyntaxParser()
    return buildAnnotatedString {
        append(content)
        var context = LineContext.INITIAL
        var offset = 0
        for (line in content.lines()) {
            val lineEnd = offset + line.length
            val (tokens, nextContext) = parser.parseLine(line, offset, lineEnd, context)
            for (token in tokens) {
                val style = tokenStyle(token.kind, colors)
                if (style != null) {
                    addStyle(style, token.startOffset, token.endOffset.coerceAtMost(length))
                }
            }
            context = nextContext
            offset = lineEnd + 1
        }
    }
}

private fun tokenStyle(kind: SyntaxTokenKind, colors: DSColors): SpanStyle? = when (kind) {
    SyntaxTokenKind.HEADING -> SpanStyle(color = colors.accentPrimary, fontWeight = FontWeight.Bold)
    SyntaxTokenKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    SyntaxTokenKind.ITALIC -> SpanStyle(fontWeight = FontWeight.Light)
    SyntaxTokenKind.INLINE_CODE -> SpanStyle(color = colors.gitModified, fontFamily = FontFamily.Monospace)
    SyntaxTokenKind.CODE_BLOCK_FENCE -> SpanStyle(color = colors.textMuted)
    SyntaxTokenKind.CODE_BLOCK_BODY -> SpanStyle(color = colors.textSecondary)
    SyntaxTokenKind.COMMENT -> SpanStyle(color = colors.textMuted)
    SyntaxTokenKind.FRONTMATTER_DELIMITER -> SpanStyle(color = colors.textMuted)
    SyntaxTokenKind.FRONTMATTER_KEY -> SpanStyle(color = colors.accentPrimary)
    SyntaxTokenKind.FRONTMATTER_VALUE -> SpanStyle(color = colors.gitAdded)
    SyntaxTokenKind.CS_DIRECTIVE -> SpanStyle(color = colors.agentCodeSpeak, fontWeight = FontWeight.Bold)
    SyntaxTokenKind.CS_FILE_REF -> SpanStyle(color = colors.accentSecondary)
    SyntaxTokenKind.LINK -> SpanStyle(color = colors.accentPrimary)
    SyntaxTokenKind.LINK_URL -> SpanStyle(color = colors.accentSecondary)
    SyntaxTokenKind.BLOCKQUOTE -> SpanStyle(color = colors.textSecondary)
    SyntaxTokenKind.LIST_MARKER -> SpanStyle(color = colors.accentPrimary)
    SyntaxTokenKind.HORIZONTAL_RULE -> SpanStyle(color = colors.textMuted)
    SyntaxTokenKind.PLAIN -> null
    else -> null
}
