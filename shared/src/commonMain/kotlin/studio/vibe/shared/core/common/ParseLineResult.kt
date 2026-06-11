package studio.vibe.shared.core.common

data class ParseLineResult(
    val tokens: List<SyntaxToken>,
    val context: LineContext,
)
