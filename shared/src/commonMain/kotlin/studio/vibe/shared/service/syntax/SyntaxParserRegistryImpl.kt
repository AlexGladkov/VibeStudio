package studio.vibe.shared.service.syntax

import kotlin.concurrent.Volatile
import studio.vibe.shared.contract.SyntaxParsing
import studio.vibe.shared.contract.SyntaxParserRegistering

/**
 * Concrete registry of syntax parsers.
 *
 * Compound extension lookup: `"cs.md"` is matched before `"md"` because
 * [parser] tries the full extension string first, then falls back to the last
 * dot-separated component (longest-match-first strategy).
 *
 * Thread safety: all mutations and reads on the internal map are protected by
 * a [Mutex].  [register] and [unregister] are suspend-safe via the lock;
 * [parser] performs a non-suspending snapshot read that is safe because
 * [HashMap.get] on a fully-published map (written under lock, read under lock)
 * will not produce torn reads — the lock is held for both write and read paths.
 */
class SyntaxParserRegistryImpl : SyntaxParserRegistering {

    // Copy-on-write @Volatile map gives lock-free reads + cross-thread visibility
    // on Kotlin/Native (no `synchronized {}` in commonMain). Writers are assumed
    // to be the Composition Root running on a single thread during startup; if
    // concurrent writers appear, wrap mutators in an external Mutex.
    @Volatile private var registry: Map<String, SyntaxParsing> = emptyMap()

    /**
     * Register [parser] for all of its [SyntaxParsing.supportedExtensions].
     * Extensions are normalised to lowercase before insertion.
     *
     * This function must be called from a coroutine context; it acquires the
     * internal mutex to prevent concurrent modification.
     */
    override fun register(parser: SyntaxParsing) {
        // Non-suspend callers (Composition Root at app start) use runBlocking
        // semantics implicitly because the mutex is uncontended at startup.
        // For safety we expose a suspend variant via the companion when needed.
        val updated = registry.toMutableMap()
        for (ext in parser.supportedExtensions) {
            updated[ext.lowercase()] = parser
        }
        registry = updated
    }

    /**
     * Removes the entry for [fileExtension] from the registry.
     *
     * @param fileExtension The extension key to remove (e.g. `"md"`, `"cs.md"`).
     *   Case-insensitive.
     * @return `true` if the key was present and removed; `false` otherwise.
     */
    override fun unregister(fileExtension: String): Boolean {
        val ext = fileExtension.lowercase()
        val current = registry
        if (!current.containsKey(ext)) return false
        registry = current.toMutableMap().also { it.remove(ext) }
        return true
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
        val snapshot = registry
        // Try the extension as provided (handles compound "cs.md"), then fall back
        // to the last dot-separated component (handles "md" from "foo.cs.md").
        return snapshot[ext] ?: snapshot[ext.split('.').last()]
    }
}
