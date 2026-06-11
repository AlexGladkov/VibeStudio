package studio.vibe.shared.core.common

data class SyntaxToken(
    val kind: SyntaxTokenKind,
    val startOffset: Int,
    val endOffset: Int,
)
