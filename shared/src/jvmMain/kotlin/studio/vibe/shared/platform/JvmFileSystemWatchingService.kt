@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.FileSystemWatchingService
import studio.vibe.shared.contract.WatchInfo
import studio.vibe.shared.contract.WatchOptions
import studio.vibe.shared.contract.WatchToken
import studio.vibe.shared.model.FileChangeEvent
import studio.vibe.shared.model.FilePath
import kotlin.time.Clock

/**
 * JVM implementation of [FileSystemWatchingService].
 *
 * Wraps one [JvmFileSystemWatcher] per active watch token.
 * All events from all active watches are merged and emitted on [events].
 *
 * Thread-safety: all mutations happen on the [scope] coroutine dispatcher.
 * The [activeWatches] snapshot list is safe to read from any thread.
 *
 * @param scope Long-lived coroutine scope that owns the watch jobs.
 *   Cancel this scope (or call [unwatchAll]) to release all OS resources.
 */
class JvmFileSystemWatchingService(
    private val scope: CoroutineScope,
) : FileSystemWatchingService {

    private val _events = MutableSharedFlow<FileChangeEvent>(extraBufferCapacity = 64)
    override val events: Flow<FileChangeEvent> = _events.asSharedFlow()

    // Maps are touched from arbitrary caller threads (Main + dispose hook +
    // window-close coroutines), so all mutations and snapshots are guarded by
    // [lock].  ConcurrentHashMap is not sufficient on its own because we need
    // *atomic* multi-map updates (info + job + watcher inserted together).
    private val lock = Any()
    private val watchInfoMap = mutableMapOf<WatchToken, WatchInfo>()
    private val watchJobs = mutableMapOf<WatchToken, Job>()
    private val watcherInstances = mutableMapOf<WatchToken, JvmFileSystemWatcher>()

    override val activeWatches: List<WatchInfo>
        get() = synchronized(lock) { watchInfoMap.values.toList() }

    override fun watch(directory: FilePath, options: WatchOptions): WatchToken {
        val token = WatchToken()
        val info = WatchInfo(
            token = token,
            directory = directory,
            options = options,
            startedAt = Clock.System.now(),
        )
        val watcher = JvmFileSystemWatcher()

        val job = scope.launch {
            watcher.watch(directory).collect { event ->
                val name = event.path.name
                val ignored = options.ignoredPatterns.any { pattern ->
                    matchesGlob(name, pattern)
                }
                if (!ignored) {
                    _events.emit(event)
                }
            }
        }

        synchronized(lock) {
            watchInfoMap[token] = info
            watcherInstances[token] = watcher
            watchJobs[token] = job
        }
        return token
    }

    override fun unwatch(token: WatchToken) {
        val (job, watcher) = synchronized(lock) {
            val j = watchJobs.remove(token)
            val w = watcherInstances.remove(token)
            watchInfoMap.remove(token)
            j to w
        }
        job?.cancel()
        watcher?.stop()
    }

    override fun unwatchAll() {
        val tokens = synchronized(lock) { watchInfoMap.keys.toList() }
        tokens.forEach { unwatch(it) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Minimal glob matcher supporting `*` (matches any chars except `/`) and
     * prefix patterns. Covers the common ignore patterns like `*.swp`, `*~`,
     * `.DS_Store`, `node_modules`, `.git`.
     */
    private fun matchesGlob(name: String, pattern: String): Boolean {
        if (!pattern.contains('*')) return name == pattern
        val regexStr = buildString {
            for (ch in pattern) {
                when (ch) {
                    '*'  -> append("[^/]*")
                    '.'  -> append("\\.")
                    else -> append(ch)
                }
            }
        }
        return Regex(regexStr).matches(name)
    }
}
