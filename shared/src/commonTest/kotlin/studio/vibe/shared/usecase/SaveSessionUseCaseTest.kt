@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package studio.vibe.shared.usecase

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.contract.SessionPersisting
import studio.vibe.shared.model.AppSessionSnapshot
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.testutil.FakeProjectManaging
import studio.vibe.shared.testutil.FakeTerminalSessionManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SaveSessionUseCaseTest {

    private class CapturingSessionPersisting : SessionPersisting {
        var saved: AppSessionSnapshot? = null
        var saveCallCount = 0
        var throwOnSave: Throwable? = null

        override val storageDirectory: FilePath = FilePath("/support")
        override val currentSnapshotVersion: Int = 1

        override suspend fun save(snapshot: AppSessionSnapshot) {
            throwOnSave?.let { throw it }
            saved = snapshot
            saveCallCount++
        }

        override suspend fun restore(): AppSessionSnapshot? = null
        override suspend fun clear() {}
        override suspend fun saveScrollback(content: String, sessionId: Uuid) {}
        override suspend fun loadScrollback(sessionId: Uuid): String? = null
        override suspend fun deleteScrollback(sessionId: Uuid) {}
        override suspend fun pruneOrphanedScrollbacks(activeSessionIds: Set<Uuid>): Int = 0
    }

    @Test
    fun execute_noProjects_savesSnapshotWithEmptyProjectSessions() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val persistence = CapturingSessionPersisting()

        SaveSessionUseCase(pm, tsm, persistence).execute()

        assertNotNull(persistence.saved)
        assertTrue(persistence.saved!!.projectSessions.isEmpty())
    }

    @Test
    fun execute_withActiveProject_persistsActiveProjectId() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        pm.setActiveProjectId(project.id)
        val tsm = FakeTerminalSessionManaging()
        val persistence = CapturingSessionPersisting()

        SaveSessionUseCase(pm, tsm, persistence).execute()

        assertEquals(project.id, persistence.saved?.activeProjectId)
    }

    @Test
    fun execute_activeIdIsNotARealProject_persistsNullActiveId() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        // Set active to a UUID not in the project list (simulates FreeTab)
        pm.setActiveProjectId(Uuid.random())
        val tsm = FakeTerminalSessionManaging()
        val persistence = CapturingSessionPersisting()

        SaveSessionUseCase(pm, tsm, persistence).execute()

        assertNull(persistence.saved?.activeProjectId)
    }

    @Test
    fun execute_projectWithTerminalSession_includesLayoutInSnapshot() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        val tsm = FakeTerminalSessionManaging()
        tsm.createSession(projectId = project.id, shell = null, workingDirectory = null)
        val persistence = CapturingSessionPersisting()

        SaveSessionUseCase(pm, tsm, persistence).execute()

        val projectSession = persistence.saved?.projectSessions?.firstOrNull()
        assertEquals(project.id, projectSession?.projectId)
        assertEquals(1, projectSession?.terminalLayouts?.size)
    }

    @Test
    fun execute_persistenceThrows_doesNotPropagate() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val persistence = CapturingSessionPersisting().also {
            it.throwOnSave = RuntimeException("disk full")
        }

        // Must not throw
        SaveSessionUseCase(pm, tsm, persistence).execute()
    }

    @Test
    fun execute_snapshotVersionMatchesCurrentSnapshotVersion() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val persistence = CapturingSessionPersisting()

        SaveSessionUseCase(pm, tsm, persistence).execute()

        assertEquals(persistence.currentSnapshotVersion, persistence.saved?.version)
    }
}
