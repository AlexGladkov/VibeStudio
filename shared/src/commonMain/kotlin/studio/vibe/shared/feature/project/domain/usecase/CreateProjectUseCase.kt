package studio.vibe.shared.feature.project.domain.usecase

import studio.vibe.shared.core.common.PersistenceStore
import studio.vibe.shared.core.common.UseCase
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.core.common.project.Project
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
) : UseCase<CreateProjectParams, Project> {

    override suspend operator fun invoke(params: CreateProjectParams): Result<Project> = runCatching {
        val newPath = params.parentPath.child(params.name)
        persistence.createDirectory(newPath)
        val project = projectManager.addProject(newPath)
        projectManager.setActiveProjectId(project.id)
        project
    }
}
