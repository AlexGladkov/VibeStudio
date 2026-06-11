package studio.vibe.shared.core.common

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
