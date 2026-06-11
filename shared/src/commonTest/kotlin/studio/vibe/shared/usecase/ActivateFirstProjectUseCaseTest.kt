@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.project.domain.usecase

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.testutil.FakeProjectManaging
import studio.vibe.shared.testutil.FakeTerminalSessionManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivateFirstProjectUseCaseTest {

    @Test
    fun invoke_noProjects_doesNothing() = runTest {
        val pm = FakeProjectManaging(initialProjects = emptyList())
        val tsm = FakeTerminalSessionManaging()

        ActivateFirstProjectUseCase(pm, tsm)()

        assertNull(pm.activeProjectId.value)
        assertEquals(0, tsm.createCallCount)
    }

    @Test
    fun invoke_projectsExist_noActive_activatesFirst() = runTest {
        val first = Project(name = "first", path = FilePath("/first"))
        val second = Project(name = "second", path = FilePath("/second"))
        val pm = FakeProjectManaging(initialProjects = listOf(first, second))
        val tsm = FakeTerminalSessionManaging()

        ActivateFirstProjectUseCase(pm, tsm)()

        assertEquals(first.id, pm.activeProjectId.value)
    }

    @Test
    fun invoke_alreadyActiveProject_doesNotChangeActive() = runTest {
        val p1 = Project(name = "p1", path = FilePath("/p1"))
        val p2 = Project(name = "p2", path = FilePath("/p2"))
        val pm = FakeProjectManaging(initialProjects = listOf(p1, p2))
        pm.setActiveProjectId(p2.id)
        val tsm = FakeTerminalSessionManaging()

        ActivateFirstProjectUseCase(pm, tsm)()

        assertEquals(p2.id, pm.activeProjectId.value)
        assertEquals(0, tsm.createCallCount)
    }

    @Test
    fun invoke_sessionAlreadyExists_doesNotCreateNew() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        val tsm = FakeTerminalSessionManaging()
        tsm.createSession(projectId = project.id, shell = null, workingDirectory = null)

        ActivateFirstProjectUseCase(pm, tsm)()

        assertEquals(project.id, pm.activeProjectId.value)
        assertEquals(1, tsm.createCallCount)
    }

    @Test
    fun invoke_noExistingSession_createsTerminalSession() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        val tsm = FakeTerminalSessionManaging()

        ActivateFirstProjectUseCase(pm, tsm)()

        assertEquals(1, tsm.createCallCount)
    }
}
