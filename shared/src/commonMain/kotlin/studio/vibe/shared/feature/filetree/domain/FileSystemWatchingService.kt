package studio.vibe.shared.feature.filetree.domain

import kotlinx.coroutines.flow.Flow
import studio.vibe.shared.core.common.FileChangeEvent
import studio.vibe.shared.core.common.FilePath
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
interface FileSystemWatchingService {
    fun watch(directory: FilePath, options: WatchOptions = WatchOptions.DEFAULT): WatchToken
    fun unwatch(token: WatchToken)
    fun unwatchAll()
    val events: Flow<FileChangeEvent>
    val activeWatches: List<WatchInfo>
}
