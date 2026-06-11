package studio.vibe.shared.testutil

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.core.common.project.ProjectsState
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.core.common.project.ProjectManagerError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory [ProjectManaging] for unit tests of ViewModels.
 *
 * - All mutations succeed unless [addProjectError] is set.
 * - [save] / [load] are no-ops.
 * - Public counters expose call invocations for verification.
 */
@OptIn(ExperimentalUuidApi::class)
class FakeProjectManaging(
    initialProjects: List<Project> = emptyList(),
    initialRecents: List<Project> = emptyList(),
    /** Scope used for [projectsState] combine — default is suitable for tests. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
) : ProjectManaging {

    private val _projects = MutableStateFlow(initialProjects)
    override val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _activeProjectId = MutableStateFlow<Uuid?>(null)
    override val activeProjectId: StateFlow<Uuid?> = _activeProjectId.asStateFlow()

    private val _recentHistory = MutableStateFlow(initialRecents)
    override val recentHistory: StateFlow<List<Project>> = _recentHistory.asStateFlow()

    private val _recentProjects = MutableStateFlow(initialRecents)
    override val recentProjects: StateFlow<List<Project>> = _recentProjects.asStateFlow()

    override val projectsState: StateFlow<ProjectsState> = combine(
        _projects, _activeProjectId, _recentHistory, _recentProjects,
    ) { projs, activeId, history, recents ->
        ProjectsState(
            projects = projs,
            activeProjectId = activeId,
            recentHistory = history,
            recentProjects = recents,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ProjectsState(initialProjects, null, initialRecents, initialRecents),
    )

    /** When non-null, [addProject] throws this exception instead of registering the project. */
    var addProjectError: ProjectManagerError? = null

    var addProjectCallCount: Int = 0
    var saveCallCount: Int = 0
    var loadCallCount: Int = 0
    var setActiveCallCount: Int = 0

    override fun setActiveProjectId(id: Uuid?) {
        setActiveCallCount++
        _activeProjectId.value = id
    }

    override suspend fun addProject(path: FilePath): Project {
        addProjectCallCount++
        addProjectError?.let { throw it }
        val project = Project(name = path.name, path = path)
        _projects.update { it + project }
        return project
    }

    override suspend fun removeProject(id: Uuid) {
        _projects.update { it.filter { p -> p.id != id } }
        if (_activeProjectId.value == id) {
            _activeProjectId.value = _projects.value.firstOrNull()?.id
        }
    }

    override suspend fun updateProject(id: Uuid, mutate: (Project) -> Project) {
        _projects.update { it.map { p -> if (p.id == id) mutate(p) else p } }
    }

    override suspend fun moveProjects(fromIndices: Set<Int>, toDestination: Int) {
        // Not exercised by VM tests yet — implement on demand.
    }

    override fun project(id: Uuid): Project? = _projects.value.firstOrNull { it.id == id }

    override fun project(path: FilePath): Project? =
        _projects.value.firstOrNull { it.path.path == path.path }

    override suspend fun load() {
        loadCallCount++
    }

    override suspend fun save() {
        saveCallCount++
    }
}
