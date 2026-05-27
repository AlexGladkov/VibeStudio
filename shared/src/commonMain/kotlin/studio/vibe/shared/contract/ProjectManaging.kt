package studio.vibe.shared.contract

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface ProjectManaging {
    val projects: StateFlow<List<Project>>
    val activeProjectId: StateFlow<Uuid?>
    val recentHistory: StateFlow<List<Project>>
    val recentProjects: StateFlow<List<Project>>

    fun setActiveProjectId(id: Uuid?)
    fun addProject(path: FilePath): Project
    fun removeProject(id: Uuid)
    fun updateProject(id: Uuid, mutate: (Project) -> Project)
    fun moveProjects(fromIndices: Set<Int>, toDestination: Int)
    fun project(id: Uuid): Project?
    fun project(path: FilePath): Project?
    fun load()
    fun save()

    val activeProject: Project?
        get() = activeProjectId.value?.let { project(it) }
}
