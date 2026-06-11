@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.session.domain.usecase

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.feature.session.domain.contract.SessionPersisting
import studio.vibe.shared.feature.session.domain.model.AppSessionSnapshot
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.core.common.project.ProjectSessionSnapshot
import studio.vibe.shared.feature.session.domain.model.SessionPersistenceError
import studio.vibe.shared.core.common.terminal.TerminalLayoutSnapshot
import studio.vibe.shared.testutil.FakeProjectManaging
import studio.vibe.shared.testutil.FakeTerminalSessionManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class RestoreSessionUseCaseTest {

    private class FakeSessionPersisting(
        private val snapshot: AppSessionSnapshot? = null,
        private val throwOnRestore: Throwable? = null,
    ) : SessionPersisting {

        override val storageDirectory: FilePath = FilePath("/support")
        override val currentSnapshotVersion: Int = 1

        override suspend fun restore(): AppSessionSnapshot? {
            throwOnRestore?.let { throw it }
            return snapshot
        }
        override suspend fun save(snapshot: AppSessionSnapshot) {}
        override suspend fun clear() {}
        override suspend fun saveScrollback(content: String, sessionId: Uuid) {}
        override suspend fun loadScrollback(sessionId: Uuid): String? = null
        override suspend fun deleteScrollback(sessionId: Uuid) {}
        override suspend fun pruneOrphanedScrollbacks(activeSessionIds: Set<Uuid>): Int = 0
    }

    private fun snapshot(activeId: Uuid? = null, projectSessions: List<ProjectSessionSnapshot> = emptyList()) =
        AppSessionSnapshot(
            version = 1,
            capturedAt = Clock.System.now(),
            activeProjectId = activeId,
            projectSessions = projectSessions,
        )

    @Test
    fun invoke_noSnapshot_returnsFalse() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()

        val result = RestoreSessionUseCase(pm, tsm, FakeSessionPersisting())()

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow())
    }

    @Test
    fun invoke_snapshotExists_returnsTrue() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val persistence = FakeSessionPersisting(snapshot = snapshot())

        val result = RestoreSessionUseCase(pm, tsm, persistence)()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
    }

    @Test
    fun invoke_snapshotWithActiveProject_restoresActiveProjectId() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        val tsm = FakeTerminalSessionManaging()
        val persistence = FakeSessionPersisting(snapshot = snapshot(activeId = project.id))

        RestoreSessionUseCase(pm, tsm, persistence)()

        assertEquals(project.id, pm.activeProjectId.value)
    }

    @Test
    fun invoke_activeProjectIdNotInProjectManager_doesNotSetActive() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val orphanId = Uuid.random()
        val persistence = FakeSessionPersisting(snapshot = snapshot(activeId = orphanId))

        RestoreSessionUseCase(pm, tsm, persistence)()

        assertEquals(null, pm.activeProjectId.value)
    }

    @Test
    fun invoke_snapshotWithTerminalLayouts_createsTerminalForFirstLayout() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        val tsm = FakeTerminalSessionManaging()
        val layout = TerminalLayoutSnapshot(sessionId = Uuid.random(), title = "bash", workingDirectory = project.path)
        val projSession = ProjectSessionSnapshot(
            projectId = project.id,
            terminalLayouts = listOf(layout),
            sidebarVisible = true,
            sidebarWidth = 220.0,
        )
        val persistence = FakeSessionPersisting(snapshot = snapshot(projectSessions = listOf(projSession)))

        RestoreSessionUseCase(pm, tsm, persistence)()

        assertEquals(1, tsm.createCallCount)
    }

    @Test
    fun invoke_corruptedSnapshot_returnsFailure() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val persistence = FakeSessionPersisting(
            throwOnRestore = SessionPersistenceError.DecodingFailed(RuntimeException("bad json")),
        )

        val result = RestoreSessionUseCase(pm, tsm, persistence)()

        assertTrue(result.isFailure)
    }
}
