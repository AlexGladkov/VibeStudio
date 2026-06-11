package studio.vibe.shared.feature.filetree.domain

import studio.vibe.shared.core.common.FilePath

sealed class FileSystemWatcherError(override val message: String) : Exception(message) {
    data class StreamCreationFailed(val path: FilePath) : FileSystemWatcherError("Failed to create file watch for: ${path.path}")
    data class PathNotFound(val path: FilePath) : FileSystemWatcherError("Watch path does not exist: ${path.path}")
    data class AlreadyWatching(val path: FilePath) : FileSystemWatcherError("Already watching: ${path.path}")
}
