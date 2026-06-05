package studio.vibe.shared.testutil

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.ProjectsState
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.model.ProjectManagerError
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
) : ProjectManaging {

    private val _projects = MutableStateFlow(initialProjects)
    override val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _activeProjectId = MutableStateFlow<Uuid?>(null)
    override val activeProjectId: StateFlow<Uuid?> = _activeProjectId.asStateFlow()

    private val _recentHistory = MutableStateFlow(initialRecents)
    override val recentHistory: StateFlow<List<Project>> = _recentHistory.asStateFlow()

    private val _recentProjects = MutableStateFlow(initialRecents)
    override val recentProjects: StateFlow<List<Project>> = _recentProjects.asStateFlow()

    // projectsState is derived synchronously via combine for test simplicity.
    // Tests that need a StateFlow snapshot can use .value on the individual flows.
    private val _projectsState = MutableStateFlow(
        ProjectsState(initialProjects, null, initialRecents, initialRecents)
    )
    override val projectsState: StateFlow<ProjectsState> = _projectsState.asStateFlow()

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

    override fun updateProject(id: Uuid, mutate: (Project) -> Project) {
        _projects.update { it.map { p -> if (p.id == id) mutate(p) else p } }
    }

    override fun moveProjects(fromIndices: Set<Int>, toDestination: Int) {
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
