package studio.vibe.shared.core.common

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
