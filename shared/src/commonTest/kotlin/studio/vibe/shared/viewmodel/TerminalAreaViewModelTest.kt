@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.terminal.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import studio.vibe.shared.core.common.AIAgent
import studio.vibe.shared.core.common.terminal.TerminalSessionEvent
import studio.vibe.shared.core.common.terminal.TerminalSessionManaging
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.core.common.terminal.SplitDirection
import studio.vibe.shared.core.common.terminal.TabActivityState
import studio.vibe.shared.core.common.terminal.TerminalSession
import studio.vibe.shared.feature.terminal.domain.model.TerminalSessionError
import studio.vibe.shared.core.common.terminal.TerminalSize
import studio.vibe.shared.testutil.FakeProjectManaging
import studio.vibe.shared.testutil.FakeTerminalSessionManaging
import studio.vibe.shared.feature.terminal.domain.usecase.CreateTerminalSessionUseCase
import studio.vibe.shared.feature.terminal.domain.usecase.ListTerminalSessionsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TerminalAreaViewModelTest {

    /** A TerminalSessionManaging stub that throws on createSession */
    private class ThrowingCreateSessionManaging : TerminalSessionManaging {
        private val _sessions = MutableStateFlow<Map<Uuid, List<TerminalSession>>>(emptyMap())
        private val _activity = MutableStateFlow<Map<Uuid, TabActivityState>>(emptyMap())
        private val _events = MutableSharedFlow<TerminalSessionEvent>()
        override val sessionsByProject: StateFlow<Map<Uuid, List<TerminalSession>>> = _sessions.asStateFlow()
        override val projectActivityStates: StateFlow<Map<Uuid, TabActivityState>> = _activity.asStateFlow()
        override val sessionEvents: Flow<TerminalSessionEvent> = _events.asSharedFlow()
        override fun createSession(projectId: Uuid, shell: String?, workingDirectory: FilePath?, size: TerminalSize): TerminalSession =
            throw TerminalSessionError.PtyCreationFailed("pty error")
        override fun resize(sessionId: Uuid, size: TerminalSize) {}
        override fun killSession(sessionId: Uuid, force: Boolean) {}
        override fun killAllSessions(projectId: Uuid) {}
        override fun split(sessionId: Uuid, direction: SplitDirection, size: TerminalSize): TerminalSession =
            throw UnsupportedOperationException()
        override fun startAgentSession(agent: AIAgent, projectId: Uuid, workingDirectory: String, apiKeyValue: String?): Result<TerminalSession> =
            Result.failure(UnsupportedOperationException())
        override fun session(id: Uuid): TerminalSession? = null
        override fun sessions(projectId: Uuid): List<TerminalSession> = emptyList()
        override fun markProjectSeen(projectId: Uuid) {}
        override fun sendInput(text: String, sessionId: Uuid) {}
        override fun scrollbackContent(sessionId: Uuid): String? = null
    }

    private fun buildVm(
        projects: List<Project> = emptyList(),
        pm: FakeProjectManaging = FakeProjectManaging(initialProjects = projects),
        tsm: TerminalSessionManaging = FakeTerminalSessionManaging(),
    ): Triple<TerminalAreaViewModel, FakeProjectManaging, TerminalSessionManaging> {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val vm = TerminalAreaViewModel(
            projectManaging = pm,
            createTerminalSessionUseCase = CreateTerminalSessionUseCase(tsm),
            listTerminalSessionsUseCase = ListTerminalSessionsUseCase(tsm),
            parentScope = scope,
        )
        return Triple(vm, pm, tsm)
    }

    // ── createSession() ───────────────────────────────────────────────────────────

    @Test
    fun createSession_unknownProjectId_setsErrorMessage() {
        val (vm, _, _) = buildVm()

        vm.createSession(Uuid.random())

        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun createSession_knownProject_addsSessionAndActivatesIt() {
        val project = Project(name = "app", path = FilePath("/app"))
        val tsm = FakeTerminalSessionManaging()
        val (vm, _, _) = buildVm(projects = listOf(project), tsm = tsm)

        vm.createSession(project.id)

        assertEquals(1, vm.state.value.sessions.size)
        assertNotNull(vm.state.value.activeSessionId)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun createSession_tsmThrows_setsErrorMessage() {
        val project = Project(name = "app", path = FilePath("/app"))
        val (vm, _, _) = buildVm(projects = listOf(project), tsm = ThrowingCreateSessionManaging())

        vm.createSession(project.id)

        assertNotNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.sessions.isEmpty())
    }

    // ── refreshSessions() ────────────────────────────────────────────────────────

    @Test
    fun refreshSessions_filtersToProjectSessions() {
        val p1 = Project(name = "p1", path = FilePath("/p1"))
        val p2 = Project(name = "p2", path = FilePath("/p2"))
        val tsm = FakeTerminalSessionManaging()
        val (vm, _, _) = buildVm(projects = listOf(p1, p2), tsm = tsm)

        tsm.createSession(p1.id, null, null)
        vm.refreshSessions(p2.id)

        assertTrue(vm.state.value.sessions.isEmpty())
        assertNull(vm.state.value.activeSessionId)
    }

    @Test
    fun refreshSessions_activeSessionNoLongerExists_clearsActiveId() {
        val project = Project(name = "app", path = FilePath("/app"))
        val tsm = FakeTerminalSessionManaging()
        val (vm, _, _) = buildVm(projects = listOf(project), tsm = tsm)

        vm.createSession(project.id)
        tsm.killAllSessions(project.id)
        vm.refreshSessions(project.id)

        assertNull(vm.state.value.activeSessionId)
    }

    // ── activateSession() ────────────────────────────────────────────────────────

    @Test
    fun activateSession_setsActiveSessionId() {
        val project = Project(name = "app", path = FilePath("/app"))
        val tsm = FakeTerminalSessionManaging()
        val (vm, _, _) = buildVm(projects = listOf(project), tsm = tsm)
        vm.createSession(project.id)
        vm.createSession(project.id)
        val secondSession = vm.state.value.sessions.last()

        vm.activateSession(secondSession.id)

        assertEquals(secondSession.id, vm.state.value.activeSessionId)
    }

    // ── clearError() ─────────────────────────────────────────────────────────────

    @Test
    fun clearError_removesErrorMessage() {
        val (vm, _, _) = buildVm()
        vm.createSession(Uuid.random())
        assertNotNull(vm.state.value.errorMessage)

        vm.clearError()

        assertNull(vm.state.value.errorMessage)
    }
}
