package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.FileSystemWatchingService
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.contract.WatchOptions
import studio.vibe.shared.contract.WatchToken
import studio.vibe.shared.model.DirectoryEntry
import studio.vibe.shared.model.FileChangeKind
import studio.vibe.shared.model.FileEntry
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.FileTreeNode
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class FileTreeState(
    val nodes: List<FileTreeNode> = emptyList(),
    val expandedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/** Minimum interval between file-tree refreshes triggered by FS events (ms). */
private const val FILE_WATCH_DEBOUNCE_MS = 500L

@OptIn(ExperimentalUuidApi::class)
class FileTreeViewModel(
    private val persistenceStore: PersistenceStore,
    private val fileSystemWatchingService: FileSystemWatchingService,
    parentScope: CoroutineScope,
) : BaseViewModel(parentScope) {

    private val _state = MutableStateFlow(FileTreeState())
    val state: StateFlow<FileTreeState> = _state.asStateFlow()

    private var watchToken: WatchToken? = null
    private var watchJob: Job? = null
    private var currentRootPath: FilePath? = null

    fun loadTree(rootPath: FilePath) {
        currentRootPath = rootPath
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val nodes = buildTree(rootPath, _state.value.expandedPaths)
                _state.update { it.copy(nodes = nodes, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun startWatching(rootPath: FilePath) {
        stopWatching()
        watchToken = fileSystemWatchingService.watch(
            directory = rootPath,
            options = WatchOptions.DEFAULT,
        )
        watchJob = scope.launch {
            val rootPrefix = rootPath.path.trimEnd('/', '\\') + "/"
            fileSystemWatchingService.events
                .debounce(FILE_WATCH_DEBOUNCE_MS.milliseconds)
                .collect { event ->
                    // Ignore events outside this tree's root to prevent cross-project reloads.
                    if (!event.path.path.startsWith(rootPrefix) && event.path.path != rootPath.path) return@collect
                    when (event.kind) {
                        FileChangeKind.CREATED,
                        FileChangeKind.MODIFIED,
                        FileChangeKind.DELETED,
                        FileChangeKind.RENAMED -> {
                            currentRootPath?.let { loadTree(it) }
                        }
                    }
                }
        }
    }

    fun stopWatching() {
        watchToken?.let { token ->
            fileSystemWatchingService.unwatch(token)
            watchToken = null
        }
        watchJob?.cancel()
        watchJob = null
    }

    fun toggleExpanded(path: String) {
        _state.update { s ->
            val expanded = s.expandedPaths.toMutableSet()
            if (path in expanded) expanded.remove(path) else expanded.add(path)
            s.copy(expandedPaths = expanded)
        }
        currentRootPath?.let { loadTree(it) }
    }

    fun refresh() {
        currentRootPath?.let { loadTree(it) }
    }

    override fun dispose() {
        stopWatching()
        super.dispose()
    }

    private suspend fun buildTree(
        dirPath: FilePath,
        expandedPaths: Set<String>,
        depth: Int = 0,
        maxDepth: Int = 10,
    ): List<FileTreeNode> {
        if (depth > maxDepth) return emptyList()
        val entries = runCatching { persistenceStore.listDirectory(dirPath) }.getOrElse { emptyList() }
        val nodes = mutableListOf<FileTreeNode>()
        for (entry in entries.sortedWith(compareBy { it.name })) {
            val isDir = runCatching { persistenceStore.isDirectory(entry) }.getOrElse { false }
            if (isDir) {
                val isExpanded = entry.path in expandedPaths
                val children = if (isExpanded) {
                    buildTree(entry, expandedPaths, depth + 1, maxDepth)
                } else {
                    emptyList()
                }
                nodes.add(
                    FileTreeNode.Directory(
                        DirectoryEntry(
                            path = entry,
                            children = children,
                            isExpanded = isExpanded,
                        )
                    )
                )
            } else {
                nodes.add(FileTreeNode.File(FileEntry(path = entry)))
            }
        }
        return nodes
    }
}
