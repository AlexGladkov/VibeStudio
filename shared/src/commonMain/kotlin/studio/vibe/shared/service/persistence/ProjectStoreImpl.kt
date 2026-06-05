package studio.vibe.shared.service.persistence

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.ProjectsState
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.model.ProjectManagerError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MAX_PROJECTS = 32
private const val MAX_RECENTS = 10

/**
 * Default [ProjectManaging] implementation.
 *
 * ### Concurrency
 * - In-memory mutators (`setActiveProjectId`, `removeProject`, `updateProject`,
 *   `moveProjects`) stay synchronous: they mutate StateFlows on the caller's
 *   thread and schedule background persistence via [scope] guarded by [saveMutex].
 * - I/O-bound operations (`addProject`, `load`, `save`) are `suspend` and free
 *   of any `runBlocking` — required for macOS Kotlin/Native targets where
 *   `runBlocking` is unavailable on the main dispatcher.
 *
 * @param scope Background scope used for fire-and-forget persistence writes.
 *              The scope's lifetime should match the host application's lifetime.
 */
@OptIn(ExperimentalUuidApi::class)
class ProjectStoreImpl(
    private val persistence: PersistenceStore,
    private val scope: CoroutineScope,
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true },
) : ProjectManaging {

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    override val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _activeProjectId = MutableStateFlow<Uuid?>(null)
    override val activeProjectId: StateFlow<Uuid?> = _activeProjectId.asStateFlow()

    private val _recentHistory = MutableStateFlow<List<Project>>(emptyList())
    override val recentHistory: StateFlow<List<Project>> = _recentHistory.asStateFlow()

    private val _recentProjects = MutableStateFlow<List<Project>>(emptyList())
    override val recentProjects: StateFlow<List<Project>> = _recentProjects.asStateFlow()

    override val projectsState: StateFlow<ProjectsState> = combine(
        _projects, _activeProjectId, _recentHistory, _recentProjects
    ) { projs, activeId, history, recents ->
        ProjectsState(
            projects = projs,
            activeProjectId = activeId,
            recentHistory = history,
            recentProjects = recents,
        )
    }.stateIn(scope, SharingStarted.Eagerly, ProjectsState.EMPTY)

    // O(1) lookup maps.
    //
    // @Volatile guarantees cross-thread visibility of the reference assignment on
    // Kotlin/Native (and JVM) without requiring a full mutex for every read.
    // rebuildIndex() is always called from within saveMutex or from a single-
    // threaded init path, so there is no torn-write window; we only need the
    // visibility guarantee for readers on other threads.
    @Volatile private var indexById: Map<Uuid, Project> = emptyMap()
    @Volatile private var indexByPath: Map<String, Project> = emptyMap()

    // Serializes all persistence writes to prevent torn files on concurrent saves.
    private val saveMutex = Mutex()

    private val projectsFile: FilePath
        get() = FilePath("${persistence.appSupportDirectory().path}/projects.json")

    private val recentsFile: FilePath
        get() = FilePath("${persistence.appSupportDirectory().path}/recents.json")

    override fun setActiveProjectId(id: Uuid?) {
        _activeProjectId.value = id
        updateRecentHistory(id)
    }

    override suspend fun addProject(path: FilePath): Project {
        if (!persistence.isDirectory(path)) {
            throw ProjectManagerError.InvalidPath(path)
        }

        return saveMutex.withLock {
            val existing = indexByPath[path.path]
            if (existing != null) {
                throw ProjectManagerError.Duplicate(existing.id, path)
            }

            val current = _projects.value
            if (current.size >= MAX_PROJECTS) {
                throw ProjectManagerError.ProjectLimitReached(MAX_PROJECTS)
            }

            val project = Project(
                name = path.name,
                path = path,
            )

            _projects.update { it + project }
            rebuildIndex()
            scheduleSaveProjects()

            project
        }
    }

    override suspend fun removeProject(id: Uuid) {
        saveMutex.withLock {
            var firstRemaining: Uuid? = null
            var removedActive = false
            _projects.update { current ->
                if (current.none { it.id == id }) {
                    throw ProjectManagerError.NotFound(id)
                }
                val updated = current.filter { it.id != id }
                removedActive = _activeProjectId.value == id
                firstRemaining = updated.firstOrNull()?.id
                updated
            }
            rebuildIndex()
            if (removedActive) {
                _activeProjectId.value = firstRemaining
            }
            writeProjects()
        }
    }

    override suspend fun updateProject(id: Uuid, mutate: (Project) -> Project) {
        saveMutex.withLock {
            val current = _projects.value
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) {
                throw ProjectManagerError.NotFound(id)
            }
            val updated = current.toMutableList()
            updated[index] = mutate(current[index])
            _projects.value = updated
            rebuildIndex()
            scheduleSaveProjects()
        }
    }

    override suspend fun moveProjects(fromIndices: Set<Int>, toDestination: Int) {
        saveMutex.withLock {
            val current = _projects.value.toMutableList()
            val moving = fromIndices.sortedDescending().mapNotNull { idx ->
                if (idx in current.indices) current.removeAt(idx) else null
            }.reversed()

            val insertAt = (toDestination - fromIndices.count { it < toDestination })
                .coerceIn(0, current.size)

            current.addAll(insertAt, moving)
            _projects.value = current
            rebuildIndex()
            scheduleSaveProjects()
        }
    }

    override fun project(id: Uuid): Project? = indexById[id]

    override fun project(path: FilePath): Project? = indexByPath[path.path]

    override suspend fun load() {
        loadProjects()
        loadRecents()
    }

    override suspend fun save() {
        saveMutex.withLock {
            writeProjects()
            writeRecents()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun rebuildIndex() {
        val list = _projects.value
        indexById = list.associateBy { it.id }
        indexByPath = list.associateBy { it.path.path }
        refreshRecentProjects()
    }

    private fun updateRecentHistory(activeId: Uuid?) {
        if (activeId == null) return
        val project = indexById[activeId] ?: return

        _recentHistory.update { snapshot ->
            val current = snapshot.toMutableList()
            current.removeAll { it.id == activeId }
            current.add(0, project)
            current.take(MAX_RECENTS)
        }
        refreshRecentProjects()
        scheduleSaveRecents()
    }

    private fun refreshRecentProjects() {
        val currentProjectIds = _projects.value.map { it.id }.toSet()
        _recentProjects.value = _recentHistory.value.filter { it.id !in currentProjectIds }
    }

    private suspend fun loadProjects() {
        try {
            val bytes = persistence.readFile(projectsFile) ?: return
            val text = bytes.decodeToString()
            val loaded: List<Project> = json.decodeFromString(text)
            _projects.value = loaded
            rebuildIndex()
        } catch (e: Exception) {
            // Migration from Swift format may cause parse errors on first load.
            // Start fresh; the projects.json will be overwritten with Kotlin format on next save.
            println("ProjectStore: failed to parse projects.json, starting fresh: ${e.message}")
        }
    }

    private suspend fun loadRecents() {
        try {
            val bytes = persistence.readFile(recentsFile) ?: return
            val text = bytes.decodeToString()
            val loaded: List<Project> = json.decodeFromString(text)
            _recentHistory.value = loaded.take(MAX_RECENTS)
            refreshRecentProjects()
        } catch (_: Exception) {
            // Corrupted or missing file — start fresh
        }
    }

    private suspend fun writeProjects() {
        try {
            val text = json.encodeToString(_projects.value)
            persistence.writeFile(projectsFile, text.encodeToByteArray())
        } catch (e: Exception) {
            throw ProjectManagerError.PersistenceFailed(e)
        }
    }

    private suspend fun writeRecents() {
        try {
            val text = json.encodeToString(_recentHistory.value)
            persistence.writeFile(recentsFile, text.encodeToByteArray())
        } catch (_: Exception) {
            // Best-effort: recents loss is not critical
        }
    }

    /**
     * Async fire-and-forget save.  Errors are swallowed because the write is
     * best-effort — the next mutation will reschedule.  An uncaught exception
     * here would bubble up to the [scope]'s exception handler and crash test
     * runners that surface coroutine exceptions.
     */
    private fun scheduleSaveProjects() {
        scope.launch {
            runCatching { saveMutex.withLock { writeProjects() } }
        }
    }

    private fun scheduleSaveRecents() {
        scope.launch {
            runCatching { saveMutex.withLock { writeRecents() } }
        }
    }
}
