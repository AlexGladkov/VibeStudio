package studio.vibe.shared.usecase

import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import kotlin.uuid.ExperimentalUuidApi

/**
 * Creates a new project directory on disk and registers it in the project manager.
 *
 * Extracted from the create-project UI sheet to keep views free of filesystem
 * operations — all business logic lives in the use case layer.
 */
@OptIn(ExperimentalUuidApi::class)
class CreateProjectUseCase(
    private val projectManager: ProjectManaging,
    private val persistence: PersistenceStore,
) {
    /**
     * Create a new project directory at `parentPath/name` and activate it.
     *
     * @param name The folder name (already trimmed, non-empty).
     * @param parentPath Parent directory where the folder will be created.
     * @return The newly created and activated [Project].
     * @throws Exception if directory creation fails or the project already exists.
     */
    suspend fun execute(name: String, parentPath: FilePath): Project {
        val newPath = parentPath.child(name)
        persistence.createDirectory(newPath)
        val project = projectManager.addProject(newPath)
        projectManager.setActiveProjectId(project.id)
        return project
    }
}
