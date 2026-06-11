package studio.vibe.shared.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import studio.vibe.shared.core.common.FileSystemWatcher
import studio.vibe.shared.core.common.FileChangeEvent
import studio.vibe.shared.core.common.FileChangeKind
import studio.vibe.shared.core.common.FilePath
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlin.time.Clock

class JvmFileSystemWatcher : FileSystemWatcher {

    // Tracks the active WatchService so stop() can close it if the callbackFlow
    // collector is still running. Replaced atomically; the old value is always
    // closed before being discarded.
    @Volatile
    private var watchService: WatchService? = null

    // Guards against concurrent or repeated watch() calls on the same instance.
    private val watching = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun watch(directory: FilePath): Flow<FileChangeEvent> = callbackFlow {
        if (!watching.compareAndSet(false, true)) {
            // Second call on the same instance is a programming error. Each
            // JvmFileSystemWatcher is owned by a single WatchToken in
            // JvmFileSystemWatchingService, so this should never happen in normal
            // usage. Fail fast rather than silently leaking a WatchService fd.
            throw IllegalStateException(
                "JvmFileSystemWatcher.watch() called twice on the same instance. " +
                "Create a new instance for each directory."
            )
        }
        val service: WatchService = FileSystems.getDefault().newWatchService()
        watchService = service

        val watchPath: Path = Path.of(directory.path)
        watchPath.register(
            service,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY,
        )

        val pollingJob = launch(Dispatchers.IO) {
            while (isActive) {
                val key: WatchKey = try {
                    service.take() // blocks until an event is available
                } catch (_: InterruptedException) {
                    break
                } catch (_: java.nio.file.ClosedWatchServiceException) {
                    break
                }

                for (event in key.pollEvents()) {
                    @Suppress("UNCHECKED_CAST")
                    val watchEvent = event as? WatchEvent<Path> ?: continue

                    val kind: FileChangeKind = when (watchEvent.kind()) {
                        StandardWatchEventKinds.ENTRY_CREATE -> FileChangeKind.CREATED
                        StandardWatchEventKinds.ENTRY_DELETE -> FileChangeKind.DELETED
                        StandardWatchEventKinds.ENTRY_MODIFY -> FileChangeKind.MODIFIED
                        else -> continue
                    }

                    val changedPath = watchPath.resolve(watchEvent.context())
                    trySend(
                        FileChangeEvent(
                            path = FilePath(changedPath.toString()),
                            kind = kind,
                            timestamp = Clock.System.now(),
                        )
                    )
                }

                val valid = key.reset()
                if (!valid) break
            }
        }

        awaitClose {
            pollingJob.cancel()
            runCatching { service.close() }
            watchService = null
            watching.set(false)
        }
    }

    override fun stop() {
        val svc = watchService
        watchService = null
        runCatching { svc?.close() }
        watching.set(false)
    }
}
