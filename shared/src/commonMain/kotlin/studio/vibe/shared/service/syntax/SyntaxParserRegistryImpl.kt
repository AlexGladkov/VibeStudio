package studio.vibe.shared.service.syntax

import studio.vibe.shared.contract.SyntaxParsing
import studio.vibe.shared.contract.SyntaxParserRegistering

/**
 * Concrete registry of syntax parsers.
 *
 * Compound extension lookup: `"cs.md"` is matched before `"md"` because
 * [parser] tries the full extension string first, then falls back to the last
 * dot-separated component (longest-match-first strategy).
 *
 * All mutations should happen at app startup (Composition Root). After
 * registration is complete the registry is effectively read-only.
 */
class SyntaxParserRegistryImpl : SyntaxParserRegistering {

    private val registry = mutableMapOf<String, SyntaxParsing>()

    /**
     * Register [parser] for all of its [SyntaxParsing.supportedExtensions].
     * Extensions are normalised to lowercase before insertion.
     */
    override fun register(parser: SyntaxParsing) {
        for (ext in parser.supportedExtensions) {
            registry[ext.lowercase()] = parser
        }
    }

    /**
     * Look up a parser for the given file extension.
     *
     * @param fileExtension May be a compound extension like `"cs.md"` or a
     *   simple one like `"md"`. Case-insensitive.
     * @return Matching [SyntaxParsing] instance, or null if none is registered.
     */
    override fun parser(fileExtension: String): SyntaxParsing? {
        val ext = fileExtension.lowercase()

        // Try the extension as provided (handles compound "cs.md")
        registry[ext]?.let { return it }

        // Fall back to the last dot-separated component (handles "md" from "foo.cs.md")
        val lastComponent = ext.split('.').last()
        return registry[lastComponent]
    }
}
