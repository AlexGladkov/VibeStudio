package studio.vibe.shared.contract

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import studio.vibe.shared.model.FileChangeEvent
import studio.vibe.shared.model.FilePath
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class WatchToken(val id: Uuid = Uuid.random())

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

@OptIn(ExperimentalUuidApi::class)
data class WatchInfo(
    val token: WatchToken,
    val directory: FilePath,
    val options: WatchOptions,
    val startedAt: Instant,
)

@OptIn(ExperimentalUuidApi::class)
interface FileSystemWatchingService {
    fun watch(directory: FilePath, options: WatchOptions = WatchOptions.DEFAULT): WatchToken
    fun unwatch(token: WatchToken)
    fun unwatchAll()
    val events: Flow<FileChangeEvent>
    val activeWatches: List<WatchInfo>
}
