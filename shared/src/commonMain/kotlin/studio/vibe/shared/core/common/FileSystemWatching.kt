package studio.vibe.shared.core.common

import kotlinx.coroutines.flow.Flow

interface FileSystemWatcher {
    fun watch(directory: FilePath): Flow<FileChangeEvent>
    fun stop()
}
