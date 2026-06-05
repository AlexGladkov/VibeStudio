package studio.vibe.shared.contract

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.model.ProjectManagerError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface ProjectManaging {
    val projects: StateFlow<List<Project>>
    val activeProjectId: StateFlow<Uuid?>
    val recentHistory: StateFlow<List<Project>>
    val recentProjects: StateFlow<List<Project>>

    fun setActiveProjectId(id: Uuid?)

    /**
     * @throws ProjectManagerError.InvalidPath if [path] does not exist.
     * @throws ProjectManagerError.Duplicate if a project at [path] already exists.
     * @throws ProjectManagerError.ProjectLimitReached if the project cap is hit.
     * @throws CancellationException if cancelled during async work.
     */
    @Throws(ProjectManagerError::class, CancellationException::class)
    suspend fun addProject(path: FilePath): Project

    fun removeProject(id: Uuid)
    fun updateProject(id: Uuid, mutate: (Project) -> Project)
    fun moveProjects(fromIndices: Set<Int>, toDestination: Int)
    fun project(id: Uuid): Project?
    fun project(path: FilePath): Project?

    /**
     * @throws ProjectManagerError.PersistenceFailed on I/O failure.
     * @throws CancellationException if cancelled.
     */
    @Throws(ProjectManagerError::class, CancellationException::class)
    suspend fun load()

    /**
     * @throws ProjectManagerError.PersistenceFailed on I/O failure.
     * @throws CancellationException if cancelled.
     */
    @Throws(ProjectManagerError::class, CancellationException::class)
    suspend fun save()

    val activeProject: Project?
        get() = activeProjectId.value?.let { project(it) }
}
