@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.project.domain.usecase

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.core.common.project.ProjectManagerError
import studio.vibe.shared.testutil.FakePersistenceStore
import studio.vibe.shared.testutil.FakeProjectManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateProjectUseCaseTest {

    @Test
    fun invoke_validNameAndParent_createsDirectoryAndRegistersProject() = runTest {
        val persistence = FakePersistenceStore(appSupportDir = FilePath("/support"))
        val pm = FakeProjectManaging()

        val result = CreateProjectUseCase(pm, persistence)(CreateProjectParams("my-app", FilePath("/projects")))

        assertTrue(result.isSuccess)
        val project = result.getOrThrow()
        assertEquals("my-app", project.name)
        assertTrue(persistence.isDirectory(FilePath("/projects/my-app")))
        assertEquals(1, pm.addProjectCallCount)
    }

    @Test
    fun invoke_activatesCreatedProject() = runTest {
        val persistence = FakePersistenceStore(appSupportDir = FilePath("/support"))
        val pm = FakeProjectManaging()

        val result = CreateProjectUseCase(pm, persistence)(CreateProjectParams("app", FilePath("/home/user")))

        assertTrue(result.isSuccess)
        val project = result.getOrThrow()
        assertEquals(project.id, pm.activeProjectId.value)
    }

    @Test
    fun invoke_nameTakenInProjectManager_returnsFailure() = runTest {
        val persistence = FakePersistenceStore(appSupportDir = FilePath("/support"))
        val pm = FakeProjectManaging()
        val existing = Project(name = "existing", path = FilePath("/projects/existing"))
        pm.addProject(FilePath("/projects/existing")) // seed it first
        pm.addProjectError = ProjectManagerError.Duplicate(existing.id, existing.path)

        val result = CreateProjectUseCase(pm, persistence)(CreateProjectParams("existing", FilePath("/projects")))

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }
}
