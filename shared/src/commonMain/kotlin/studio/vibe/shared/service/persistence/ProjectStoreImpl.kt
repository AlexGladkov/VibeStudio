package studio.vibe.shared.service.persistence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.model.ProjectManagerError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MAX_PROJECTS = 32
private const val MAX_RECENTS = 10

@OptIn(ExperimentalUuidApi::class)
class ProjectStoreImpl(
    private val persistence: PersistenceStore,
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

    // O(1) lookup maps
    private var indexById: Map<Uuid, Project> = emptyMap()
    private var indexByPath: Map<String, Project> = emptyMap()

    private val projectsFile: FilePath
        get() = FilePath("${persistence.appSupportDirectory().path}/projects.json")

    private val recentsFile: FilePath
        get() = FilePath("${persistence.appSupportDirectory().path}/recents.json")

    override fun setActiveProjectId(id: Uuid?) {
        _activeProjectId.value = id
        updateRecentHistory(id)
    }

    override fun addProject(path: FilePath): Project {
        val isDir = runBlocking { persistence.isDirectory(path) }
        if (!isDir) {
            throw ProjectManagerError.InvalidPath(path)
        }

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

        val updated = current + project
        _projects.value = updated
        rebuildIndex()
        save()

        return project
    }

    override fun removeProject(id: Uuid) {
        val current = _projects.value
        if (current.none { it.id == id }) {
            throw ProjectManagerError.NotFound(id)
        }

        val updated = current.filter { it.id != id }
        _projects.value = updated
        rebuildIndex()

        if (_activeProjectId.value == id) {
            _activeProjectId.value = updated.firstOrNull()?.id
        }

        save()
    }

    override fun updateProject(id: Uuid, mutate: (Project) -> Project) {
        val current = _projects.value
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) {
            throw ProjectManagerError.NotFound(id)
        }

        val updated = current.toMutableList()
        updated[index] = mutate(current[index])
        _projects.value = updated
        rebuildIndex()
        save()
    }

    override fun moveProjects(fromIndices: Set<Int>, toDestination: Int) {
        val current = _projects.value.toMutableList()
        val moving = fromIndices.sortedDescending().mapNotNull { idx ->
            if (idx in current.indices) current.removeAt(idx) else null
        }.reversed()

        val insertAt = (toDestination - fromIndices.count { it < toDestination })
            .coerceIn(0, current.size)

        current.addAll(insertAt, moving)
        _projects.value = current
        rebuildIndex()
        save()
    }

    override fun project(id: Uuid): Project? = indexById[id]

    override fun project(path: FilePath): Project? = indexByPath[path.path]

    override fun load() {
        loadProjects()
        loadRecents()
    }

    override fun save() {
        saveProjects()
        saveRecents()
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

        val current = _recentHistory.value.toMutableList()
        current.removeAll { it.id == activeId }
        current.add(0, project)
        _recentHistory.value = current.take(MAX_RECENTS)
        refreshRecentProjects()
        saveRecents()
    }

    private fun refreshRecentProjects() {
        val currentProjectIds = _projects.value.map { it.id }.toSet()
        _recentProjects.value = _recentHistory.value.filter { it.id !in currentProjectIds }
    }

    private fun loadProjects() {
        try {
            val bytes = runBlocking { persistence.readFile(projectsFile) } ?: return
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

    private fun saveProjects() {
        try {
            val text = json.encodeToString(_projects.value)
            runBlocking { persistence.writeFile(projectsFile, text.encodeToByteArray()) }
        } catch (e: Exception) {
            throw ProjectManagerError.PersistenceFailed(e)
        }
    }

    private fun loadRecents() {
        try {
            val bytes = runBlocking { persistence.readFile(recentsFile) } ?: return
            val text = bytes.decodeToString()
            val loaded: List<Project> = json.decodeFromString(text)
            _recentHistory.value = loaded.take(MAX_RECENTS)
            refreshRecentProjects()
        } catch (_: Exception) {
            // Corrupted or missing file — start fresh
        }
    }

    private fun saveRecents() {
        try {
            val text = json.encodeToString(_recentHistory.value)
            runBlocking { persistence.writeFile(recentsFile, text.encodeToByteArray()) }
        } catch (_: Exception) {
            // Best-effort: recents loss is not critical
        }
    }
}
