@file:OptIn(ExperimentalForeignApi::class)

package studio.vibe.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.stat
import studio.vibe.shared.contract.FileSystemWatcher
import studio.vibe.shared.model.FileChangeEvent
import studio.vibe.shared.model.FileChangeKind
import studio.vibe.shared.model.FilePath
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Polling-based file system watcher.
 * Uses NSFileManager for directory listing, POSIX stat for modification times.
 *
 * TODO: Replace with FSEventStream for production (lower latency, less CPU).
 */
class MacosFileSystemWatcher : FileSystemWatcher {

    private val fileManager = NSFileManager.defaultManager

    // Tracks the active polling Job so stop() can cancel it when called from
    // outside the callbackFlow collector. Guarded by the same constraint as the
    // JVM counterpart: each instance must be used for exactly one watch().
    private var pollingJob: Job? = null

    override fun watch(directory: FilePath): Flow<FileChangeEvent> = callbackFlow {
        if (pollingJob != null) {
            throw IllegalStateException(
                "MacosFileSystemWatcher.watch() called twice on the same instance. " +
                "Create a new instance for each directory."
            )
        }
        var previousSnapshot = buildSnapshot(directory)

        val job = launch(Dispatchers.Default) {
            while (isActive) {
                delay(POLL_INTERVAL_MS.milliseconds)
                val currentSnapshot = buildSnapshot(directory)
                val events = diffSnapshots(previousSnapshot, currentSnapshot)
                events.forEach { event -> trySend(event) }
                previousSnapshot = currentSnapshot
            }
        }

        pollingJob = job
        awaitClose {
            job.cancel()
            pollingJob = null
        }
    }

    override fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun buildSnapshot(directory: FilePath): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        collectEntries(directory, result)
        return result
    }

    private fun collectEntries(dir: FilePath, accumulator: MutableMap<String, Long>) {
        @Suppress("UNCHECKED_CAST")
        val contents = fileManager.contentsOfDirectoryAtPath(
            path = dir.path,
            error = null,
        ) as? List<String> ?: return

        for (name in contents) {
            val childPath = "${dir.path}/$name"
            val info = statFile(childPath) ?: continue

            accumulator[childPath] = info.modTime

            if (info.isDirectory) {
                collectEntries(FilePath(childPath), accumulator)
            }
        }
    }

    private data class FileInfo(val modTime: Long, val isDirectory: Boolean)

    private fun statFile(path: String): FileInfo? = memScoped {
        val statBuf = alloc<stat>()
        if (stat(path, statBuf.ptr) != 0) return null
        FileInfo(
            modTime = statBuf.st_mtimespec.tv_sec,
            isDirectory = (statBuf.st_mode.toInt() and S_IFMT) == S_IFDIR,
        )
    }

    private fun diffSnapshots(
        previous: Map<String, Long>,
        current: Map<String, Long>,
    ): List<FileChangeEvent> {
        val events = mutableListOf<FileChangeEvent>()
        val now = Clock.System.now()

        for ((path, modTime) in current) {
            val prevModTime = previous[path]
            when {
                prevModTime == null -> events.add(
                    FileChangeEvent(path = FilePath(path), kind = FileChangeKind.CREATED, timestamp = now)
                )
                prevModTime != modTime -> events.add(
                    FileChangeEvent(path = FilePath(path), kind = FileChangeKind.MODIFIED, timestamp = now)
                )
            }
        }

        for (path in previous.keys) {
            if (!current.containsKey(path)) {
                events.add(
                    FileChangeEvent(path = FilePath(path), kind = FileChangeKind.DELETED, timestamp = now)
                )
            }
        }

        return events
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L
    }
}
