@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.usecase

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.model.ProjectManagerError
import studio.vibe.shared.testutil.FakePersistenceStore
import studio.vibe.shared.testutil.FakeProjectManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateProjectUseCaseTest {

    @Test
    fun execute_validNameAndParent_createsDirectoryAndRegistersProject() = runTest {
        val persistence = FakePersistenceStore(appSupportDir = FilePath("/support"))
        val pm = FakeProjectManaging()

        val project = CreateProjectUseCase(pm, persistence).execute("my-app", FilePath("/projects"))

        assertEquals("my-app", project.name)
        assertTrue(persistence.isDirectory(FilePath("/projects/my-app")))
        assertEquals(1, pm.addProjectCallCount)
    }

    @Test
    fun execute_activatesCreatedProject() = runTest {
        val persistence = FakePersistenceStore(appSupportDir = FilePath("/support"))
        val pm = FakeProjectManaging()

        val project = CreateProjectUseCase(pm, persistence).execute("app", FilePath("/home/user"))

        assertEquals(project.id, pm.activeProjectId.value)
    }

    @Test
    fun execute_nameTakenInProjectManager_propagatesError() = runTest {
        val persistence = FakePersistenceStore(appSupportDir = FilePath("/support"))
        val pm = FakeProjectManaging()
        val existing = Project(name = "existing", path = FilePath("/projects/existing"))
        pm.addProject(FilePath("/projects/existing")) // seed it first
        pm.addProjectError = ProjectManagerError.Duplicate(existing.id, existing.path)

        assertFailsWith<ProjectManagerError.Duplicate> {
            CreateProjectUseCase(pm, persistence).execute("existing", FilePath("/projects"))
        }
    }
}
