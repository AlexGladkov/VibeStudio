package studio.vibe.shared.feature.project.data

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
import studio.vibe.shared.core.common.PersistenceStore
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.core.common.project.ProjectsState
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.core.common.project.ProjectManagerError
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
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        // Build initial value from the MutableStateFlow fields whose values are
        // known at construction time — avoids the EMPTY flash that occurred before
        // all four upstream flows emitted their first value to combine().
        ProjectsState(
            projects = _projects.value,
            activeProjectId = _activeProjectId.value,
            recentHistory = _recentHistory.value,
            recentProjects = _recentProjects.value,
        ),
    )

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

            // Rebuild index INSIDE the update lambda so the new index is written
            // before _projects publishes the new list to observers (reordering fix).
            _projects.update { current ->
                val next = current + project
                rebuildIndex(next)
                next
            }
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
                // Rebuild index inside the update lambda so it is consistent with
                // the new list before _projects publishes (reordering fix).
                rebuildIndex(updated)
                updated
            }
            if (removedActive) {
                _activeProjectId.value = firstRemaining
            }
            // Prune the removed project from recentHistory so it does not resurface
            // after a restart (the history is persisted and filtered on load).
            _recentHistory.update { history -> history.filterNot { it.id == id } }
            scheduleSaveRecents()
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
            // Rebuild index INSIDE the update lambda so the new index is visible
            // before _projects publishes the new list to observers. Previously,
            // rebuildIndex(updated) ran before _projects.value = updated, but
            // rebuildIndex calls refreshRecentProjects which reads _projects.value —
            // still the old list at that point. Moving both into update() fixes the
            // window where index is new but recentProjects is stale.
            _projects.update { _ ->
                rebuildIndex(updated)
                updated
            }
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
            // Rebuild index inside update lambda for the same reason as updateProject:
            // refreshRecentProjects() reads _projects.value, so both must happen atomically.
            _projects.update { _ ->
                rebuildIndex(current)
                current
            }
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

    /**
     * Rebuild lookup indexes from [list].
     *
     * Always called with the authoritative list BEFORE or DURING the
     * corresponding [_projects] emission so that readers who observe the new
     * [_projects.value] never see a stale index.
     *
     * When called from inside a [_projects.update] lambda, pass the `next`
     * list rather than reading [_projects.value] — the update has not yet
     * been published at that point.
     */
    private fun rebuildIndex(list: List<Project> = _projects.value) {
        indexById = list.associateBy { it.id }
        indexByPath = list.associateBy { it.path.path }
        refreshRecentProjects(list)
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

    /**
     * Recompute [_recentProjects] from [_recentHistory] using [projectsList] as
     * the authoritative set of current projects.
     *
     * Accepts an explicit [projectsList] so callers inside a [_projects.update]
     * lambda can pass the new list directly rather than reading [_projects.value],
     * which still holds the old value until the lambda returns (stale-read fix).
     *
     * Defaults to [_projects.value] for call sites that run after the update has
     * already been published (e.g. [updateRecentHistory], [loadRecents]).
     */
    private fun refreshRecentProjects(projectsList: List<Project> = _projects.value) {
        val currentProjectIds = projectsList.map { it.id }.toSet()
        _recentProjects.value = _recentHistory.value.filter { it.id !in currentProjectIds }
    }

    private suspend fun loadProjects() {
        try {
            val bytes = persistence.readFile(projectsFile) ?: return
            val text = bytes.decodeToString()
            val loaded: List<Project> = json.decodeFromString(text)
            // Rebuild index inside update lambda so refreshRecentProjects() sees
            // the new list rather than the still-empty _projects.value.
            _projects.update { _ ->
                rebuildIndex(loaded)
                loaded
            }
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
