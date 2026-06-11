package studio.vibe.shared.core.common

import studio.vibe.shared.core.common.BinaryResolver
import studio.vibe.shared.core.common.FilePath

/**
 * Resolves AI CLI agent executables by delegating to a platform-specific [BinaryResolver].
 *
 * Security: rejects executable names that contain path separators or traversal sequences
 * before delegating, preventing arbitrary path injection.
 */
class CLIAgentPathResolverImpl(private val binaryResolver: BinaryResolver) {

    /**
     * Resolve an executable name to its absolute path.
     *
     * @param executableName Bare binary name (e.g. "claude", "codex").
     *   Must not contain '/', '..', or be empty.
     * @return Absolute path string, or null if the name is invalid or the binary is not found.
     */
    fun resolve(executableName: String): String? {
        if (executableName.isEmpty()) return null
        if (executableName.contains('/')) return null
        if (executableName.contains("..")) return null

        return binaryResolver.findExecutable(executableName)?.path
    }
}
