@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.usecase

import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.testutil.FakeProjectManaging
import studio.vibe.shared.testutil.FakeTerminalSessionManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivateFirstProjectUseCaseTest {

    @Test
    fun execute_noProjects_doesNothing() {
        val pm = FakeProjectManaging(initialProjects = emptyList())
        val tsm = FakeTerminalSessionManaging()

        ActivateFirstProjectUseCase(pm, tsm).execute()

        assertNull(pm.activeProjectId.value)
        assertEquals(0, tsm.createCallCount)
    }

    @Test
    fun execute_projectsExist_noActive_activatesFirst() {
        val first = Project(name = "first", path = FilePath("/first"))
        val second = Project(name = "second", path = FilePath("/second"))
        val pm = FakeProjectManaging(initialProjects = listOf(first, second))
        val tsm = FakeTerminalSessionManaging()

        ActivateFirstProjectUseCase(pm, tsm).execute()

        assertEquals(first.id, pm.activeProjectId.value)
    }

    @Test
    fun execute_alreadyActiveProject_doesNotChangeActive() {
        val p1 = Project(name = "p1", path = FilePath("/p1"))
        val p2 = Project(name = "p2", path = FilePath("/p2"))
        val pm = FakeProjectManaging(initialProjects = listOf(p1, p2))
        pm.setActiveProjectId(p2.id)
        val tsm = FakeTerminalSessionManaging()

        ActivateFirstProjectUseCase(pm, tsm).execute()

        // p2 stays active — no override
        assertEquals(p2.id, pm.activeProjectId.value)
        assertEquals(0, tsm.createCallCount)
    }

    @Test
    fun execute_sessionAlreadyExists_doesNotCreateNew() {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        val tsm = FakeTerminalSessionManaging()
        // Pre-create a session for the project
        tsm.createSession(projectId = project.id, shell = null, workingDirectory = null)

        ActivateFirstProjectUseCase(pm, tsm).execute()

        assertEquals(project.id, pm.activeProjectId.value)
        // Only 1 session from the pre-create, no additional session created by the use case
        assertEquals(1, tsm.createCallCount)
    }

    @Test
    fun execute_noExistingSession_createsTerminalSession() {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        val tsm = FakeTerminalSessionManaging()

        ActivateFirstProjectUseCase(pm, tsm).execute()

        assertEquals(1, tsm.createCallCount)
    }
}
