package studio.vibe.shared.core.common

interface SyntaxParsing {
    val supportedExtensions: List<String>
    fun parseLine(
        line: String,
        lineStartOffset: Int,
        lineEndOffset: Int,
        context: LineContext,
    ): ParseLineResult
}
