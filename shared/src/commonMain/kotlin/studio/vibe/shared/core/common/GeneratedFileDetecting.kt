package studio.vibe.shared.core.common

/**
 * Pure contract for detecting generated files.
 *
 * Defined in core/ so that feature/filetree can depend on it without
 * importing any codespeak-specific types. The concrete implementation
 * lives in feature/filetree/domain/GeneratedFileDetector.kt and is
 * wired by DI.
 */
interface GeneratedFileDetecting {
    fun isGenerated(contents: String): Boolean
    fun parseSpecName(contents: String): String
}
