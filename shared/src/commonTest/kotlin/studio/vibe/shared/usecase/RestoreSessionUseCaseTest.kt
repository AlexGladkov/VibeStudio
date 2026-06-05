@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import studio.vibe.shared.contract.SessionPersisting
import studio.vibe.shared.model.AppSessionSnapshot
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.model.ProjectSessionSnapshot
import studio.vibe.shared.model.SessionPersistenceError
import studio.vibe.shared.model.TerminalLayoutSnapshot
import studio.vibe.shared.testutil.FakeProjectManaging
import studio.vibe.shared.testutil.FakeTerminalSessionManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class RestoreSessionUseCaseTest {

    // ── minimal fake ─────────────────────────────────────────────────────────────

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

    // ── tests ─────────────────────────────────────────────────────────────────────

    @Test
    fun execute_noSnapshot_returnsFalse() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()

        val result = RestoreSessionUseCase(pm, tsm, FakeSessionPersisting()).execute()

        assertFalse(result)
    }

    @Test
    fun execute_snapshotExists_returnsTrue() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val persistence = FakeSessionPersisting(snapshot = snapshot())

        val result = RestoreSessionUseCase(pm, tsm, persistence).execute()

        assertTrue(result)
    }

    @Test
    fun execute_snapshotWithActiveProject_restoresActiveProjectId() = runTest {
        val project = Project(name = "app", path = FilePath("/app"))
        val pm = FakeProjectManaging(initialProjects = listOf(project))
        val tsm = FakeTerminalSessionManaging()
        val persistence = FakeSessionPersisting(snapshot = snapshot(activeId = project.id))

        RestoreSessionUseCase(pm, tsm, persistence).execute()

        assertEquals(project.id, pm.activeProjectId.value)
    }

    @Test
    fun execute_activeProjectIdNotInProjectManager_doesNotSetActive() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val orphanId = Uuid.random()
        val persistence = FakeSessionPersisting(snapshot = snapshot(activeId = orphanId))

        RestoreSessionUseCase(pm, tsm, persistence).execute()

        assertEquals(null, pm.activeProjectId.value)
    }

    @Test
    fun execute_snapshotWithTerminalLayouts_createsTerminalForFirstLayout() = runTest {
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

        RestoreSessionUseCase(pm, tsm, persistence).execute()

        assertEquals(1, tsm.createCallCount)
    }

    @Test
    fun execute_corruptedSnapshot_returnsFalse() = runTest {
        val pm = FakeProjectManaging()
        val tsm = FakeTerminalSessionManaging()
        val persistence = FakeSessionPersisting(
            throwOnRestore = SessionPersistenceError.DecodingFailed(RuntimeException("bad json")),
        )

        // Errors are caught internally; must not propagate
        val result = RestoreSessionUseCase(pm, tsm, persistence).execute()

        assertFalse(result)
    }
}
