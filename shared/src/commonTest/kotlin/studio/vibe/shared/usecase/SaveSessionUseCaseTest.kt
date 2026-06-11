@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package studio.vibe.shared.feature.session.domain.usecase

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.feature.session.domain.contract.SessionPersisting
import studio.vibe.shared.feature.session.domain.model.AppSessionSnapshot
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
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
    fun invoke_noProjects_savesSnapshotWithEmptyProjectSessions() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val persistence = CapturingSessionPersisting()

        SaveSessionUseCase(pm, tsm, persistence)()

        assertNotNull(persistence.saved)
        assertTrue(persistence.saved!!.projectSessions.isEmpty())
    }

    @Test
    fun invoke_withActiveProject_persistsActiveProjectId() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        pm.setActiveProjectId(project.id)
        val tsm = FakeTerminalSessionManaging()
        val persistence = CapturingSessionPersisting()

        SaveSessionUseCase(pm, tsm, persistence)()

        assertEquals(project.id, persistence.saved?.activeProjectId)
    }

    @Test
    fun invoke_activeIdIsNotARealProject_persistsNullActiveId() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        pm.setActiveProjectId(Uuid.random())
        val tsm = FakeTerminalSessionManaging()
        val persistence = CapturingSessionPersisting()

        SaveSessionUseCase(pm, tsm, persistence)()

        assertNull(persistence.saved?.activeProjectId)
    }

    @Test
    fun invoke_projectWithTerminalSession_includesLayoutInSnapshot() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        val tsm = FakeTerminalSessionManaging()
        tsm.createSession(projectId = project.id, shell = null, workingDirectory = null)
        val persistence = CapturingSessionPersisting()

        SaveSessionUseCase(pm, tsm, persistence)()

        val projectSession = persistence.saved?.projectSessions?.firstOrNull()
        assertEquals(project.id, projectSession?.projectId)
        assertEquals(1, projectSession?.terminalLayouts?.size)
    }

    @Test
    fun invoke_persistenceThrows_returnsFailure() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val persistence = CapturingSessionPersisting().also {
            it.throwOnSave = RuntimeException("disk full")
        }

        val result = SaveSessionUseCase(pm, tsm, persistence)()

        assertTrue(result.isFailure)
    }

    @Test
    fun invoke_snapshotVersionMatchesCurrentSnapshotVersion() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val persistence = CapturingSessionPersisting()

        SaveSessionUseCase(pm, tsm, persistence)()

        assertEquals(persistence.currentSnapshotVersion, persistence.saved?.version)
    }
}
