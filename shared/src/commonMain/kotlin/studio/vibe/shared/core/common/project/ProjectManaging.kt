@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.core.common.project

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.core.common.project.ProjectManagerError
import kotlin.uuid.Uuid

interface ProjectManaging {
    /** Unified state — prefer this over the individual flows. */
    val projectsState: StateFlow<ProjectsState>

    /** Deprecated — use [projectsState].projects. Kept for migration compatibility. */
    val projects: StateFlow<List<Project>>

    /** Deprecated — use [projectsState].activeProjectId. Kept for migration compatibility. */
    val activeProjectId: StateFlow<Uuid?>

    /** Deprecated — use [projectsState].recentHistory. Kept for migration compatibility. */
    val recentHistory: StateFlow<List<Project>>

    /** Deprecated — use [projectsState].recentProjects. Kept for migration compatibility. */
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

    @Throws(ProjectManagerError::class, CancellationException::class)
    suspend fun removeProject(id: Uuid)

    @Throws(ProjectManagerError::class, CancellationException::class)
    suspend fun updateProject(id: Uuid, mutate: (Project) -> Project)

    @Throws(ProjectManagerError::class, CancellationException::class)
    suspend fun moveProjects(fromIndices: Set<Int>, toDestination: Int)

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
