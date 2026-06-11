package studio.vibe.shared.feature.filetree.domain

data class WatchOptions(
    val debounceIntervalMs: Long = 300,
    val ignoredPatterns: List<String> = listOf(
        "node_modules", ".git", ".build", "DerivedData", ".DS_Store", "*.swp", "*~",
    ),
    val respectGitignore: Boolean = true,
    val maxDepth: Int? = null,
) {
    companion object {
        val DEFAULT = WatchOptions()
    }
}
