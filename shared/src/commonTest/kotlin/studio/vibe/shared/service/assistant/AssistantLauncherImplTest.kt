@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package studio.vibe.shared.service.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSessionState
import studio.vibe.shared.service.agent.ClaudeAgent
import studio.vibe.shared.service.agent.CodexAgent
import studio.vibe.shared.service.agent.DefaultAIAgentRegistry
import studio.vibe.shared.testutil.FakeAPIKeyResolving
import studio.vibe.shared.testutil.FakeAgentAvailabilityChecking
import studio.vibe.shared.testutil.FakeProjectManaging
import studio.vibe.shared.testutil.FakeTerminalSessionManaging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantLauncherImplTest {

    private fun build(
        projects: FakeProjectManaging = FakeProjectManaging(),
        terminal: FakeTerminalSessionManaging = FakeTerminalSessionManaging(),
        availability: FakeAgentAvailabilityChecking = FakeAgentAvailabilityChecking(),
        registry: DefaultAIAgentRegistry = DefaultAIAgentRegistry(),
        apiKeys: FakeAPIKeyResolving = FakeAPIKeyResolving(),
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    ) = AssistantLauncherImpl(
        projectManaging = projects,
        terminalSessionManaging = terminal,
        agentRegistry = registry,
        agentAvailabilityChecking = availability,
        apiKeyResolving = apiKeys,
        blockingDispatcher = UnconfinedTestDispatcher(),
        scope = scope,
    )

    @Test
    fun start_unknownProject_returnsFailure() = runTest {
        val launcher = build()
        val result = launcher.start(
            projectId = kotlin.uuid.Uuid.random(),
            agentId = ClaudeAgent.id,
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("not found", ignoreCase = true))
    }

    @Test
    fun start_unknownAgent_returnsFailure() = runTest {
        val projects = FakeProjectManaging()
        val project = projects.addProject(FilePath("/tmp/test"))
        projects.setActiveProjectId(project.id)
        val launcher = build(projects = projects)

        val result = launcher.start(project.id, "unknown-agent-xyz")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Unknown agent", ignoreCase = true))
    }

    @Test
    fun start_notInstalled_returnsFailure() = runTest {
        val projects = FakeProjectManaging()
        val project = projects.addProject(FilePath("/tmp/test"))
        val availability = FakeAgentAvailabilityChecking()
        availability.setAvailability(
            ClaudeAgent,
            AgentAvailabilityStatus.NotInstalled(installHint = "npm install claude"),
        )
        val launcher = build(projects = projects, availability = availability)

        val result = launcher.start(project.id, ClaudeAgent.id)
        assertTrue(result.isFailure)
        assertEquals("npm install claude", result.exceptionOrNull()!!.message)
    }

    @Test
    fun start_success_updatesRunningByProject() = runTest {
        val projects = FakeProjectManaging()
        val project = projects.addProject(FilePath("/tmp/p"))
        val availability = FakeAgentAvailabilityChecking()
        availability.setAvailability(
            ClaudeAgent,
            AgentAvailabilityStatus.Available(path = "/usr/bin/claude", hasAPIKey = true),
        )
        val launcher = build(projects = projects, availability = availability)

        val result = launcher.start(project.id, ClaudeAgent.id)

        assertTrue(result.isSuccess)
        val running = launcher.runningByProject.value[project.id]
        assertNotNull(running)
        assertTrue(ClaudeAgent.id in running)
    }

    @Test
    fun start_success_sessionIdAccessible() = runTest {
        val projects = FakeProjectManaging()
        val project = projects.addProject(FilePath("/tmp/p"))
        val availability = FakeAgentAvailabilityChecking()
        availability.setAllAvailable(listOf(ClaudeAgent))
        val launcher = build(projects = projects, availability = availability)

        val result = launcher.start(project.id, ClaudeAgent.id)
        assertTrue(result.isSuccess)

        val sessionId = launcher.sessionIdFor(project.id, ClaudeAgent.id)
        assertNotNull(sessionId)
        assertEquals(result.getOrNull()!!.id, sessionId)
    }

    @Test
    fun stop_agentNotRunning_returnsSuccessIdempotent() = runTest {
        val launcher = build()
        val result = launcher.stop(kotlin.uuid.Uuid.random(), ClaudeAgent.id)
        assertTrue(result.isSuccess)
    }

    @Test
    fun stop_agentRunning_removesFromRunningByProject() = runTest {
        val projects = FakeProjectManaging()
        val project = projects.addProject(FilePath("/tmp/p"))
        val availability = FakeAgentAvailabilityChecking()
        availability.setAllAvailable(listOf(ClaudeAgent))
        val launcher = build(projects = projects, availability = availability)

        launcher.start(project.id, ClaudeAgent.id)
        assertTrue(ClaudeAgent.id in (launcher.runningByProject.value[project.id] ?: emptySet()))

        launcher.stop(project.id, ClaudeAgent.id)

        assertFalse(ClaudeAgent.id in (launcher.runningByProject.value[project.id] ?: emptySet()))
        assertNull(launcher.sessionIdFor(project.id, ClaudeAgent.id))
    }

    @Test
    fun notifySessionExited_clearsRunningState() = runTest {
        val projects = FakeProjectManaging()
        val project = projects.addProject(FilePath("/tmp/p"))
        val availability = FakeAgentAvailabilityChecking()
        availability.setAllAvailable(listOf(ClaudeAgent))
        val launcher = build(projects = projects, availability = availability)

        val result = launcher.start(project.id, ClaudeAgent.id)
        val sessionId = result.getOrNull()!!.id

        launcher.notifySessionExited(project.id, sessionId)

        assertNull(launcher.runningByProject.value[project.id])
        assertNull(launcher.sessionIdFor(project.id, ClaudeAgent.id))
    }

    @Test
    fun notifySessionExited_oneOfTwoAgents_keepsRemainingAgentInRunning() = runTest {
        // Validates that runningByProject stays consistent when one of two
        // concurrently-running agents exits (the TOCTOU fix).
        val projects = FakeProjectManaging()
        val project = projects.addProject(FilePath("/tmp/p"))
        val availability = FakeAgentAvailabilityChecking()
        availability.setAllAvailable(listOf(ClaudeAgent, CodexAgent))
        val launcher = build(projects = projects, availability = availability)

        val claudeResult = launcher.start(project.id, ClaudeAgent.id)
        assertTrue(claudeResult.isSuccess)
        val codexResult = launcher.start(project.id, CodexAgent.id)
        assertTrue(codexResult.isSuccess)

        val claudeSessionId = claudeResult.getOrNull()!!.id

        // Claude exits — Codex must remain in running state
        launcher.notifySessionExited(project.id, claudeSessionId)

        val running = launcher.runningByProject.value[project.id]
        assertNotNull(running, "project must still be in runningByProject after one of two agents exits")
        assertFalse(ClaudeAgent.id in running, "Claude must be removed from running after exit")
        assertTrue(CodexAgent.id in running, "Codex must remain in running")
        assertNull(launcher.sessionIdFor(project.id, ClaudeAgent.id))
        assertNotNull(launcher.sessionIdFor(project.id, CodexAgent.id))
    }

    @Test
    fun removeProject_clearsAllState() = runTest {
        val projects = FakeProjectManaging()
        val project = projects.addProject(FilePath("/tmp/p"))
        val availability = FakeAgentAvailabilityChecking()
        availability.setAllAvailable(listOf(ClaudeAgent))
        val terminal = FakeTerminalSessionManaging()
        terminal.startAgentSessionResult = Result.success(
            TerminalSession(
                projectId = project.id,
                title = "claude",
                state = TerminalSessionState.Running,
                isAgentSession = true,
            )
        )
        val launcher = build(projects = projects, availability = availability, terminal = terminal)

        launcher.start(project.id, ClaudeAgent.id)
        assertNotNull(launcher.runningByProject.value[project.id])

        launcher.removeProject(project.id)

        assertNull(launcher.runningByProject.value[project.id])
    }

    // ── P1: concurrent start same project+agent ───────────────────────────────

    @Test
    fun start_concurrentStartSameProjectAndAgent_onlyOneSessionRegistered() = runTest {
        // Arrange — launch the same agent twice without stopping.
        // The second start must overwrite (not duplicate) the session entry so only
        // one entry per (projectId, agentId) key is maintained.
        val projects = FakeProjectManaging()
        val project = projects.addProject(FilePath("/tmp/p"))
        val availability = FakeAgentAvailabilityChecking()
        availability.setAllAvailable(listOf(ClaudeAgent))
        val launcher = build(projects = projects, availability = availability)

        // Act — start twice (simulates two rapid taps / race condition)
        val r1 = launcher.start(project.id, ClaudeAgent.id)
        val r2 = launcher.start(project.id, ClaudeAgent.id)

        assertTrue(r1.isSuccess)
        assertTrue(r2.isSuccess)

        // Assert — only one agentId entry per project
        val agentSet = launcher.runningByProject.value[project.id]
        assertNotNull(agentSet)
        assertEquals(1, agentSet.size, "Each (projectId, agentId) pair must have exactly one entry")
        assertTrue(ClaudeAgent.id in agentSet)
    }

    // ── P1: notifySessionExited unknown sessionId ──────────────────────────────

    @Test
    fun notifySessionExited_unknownSessionId_isNoOp() = runTest {
        // Arrange — start one agent so there is real state to check against
        val projects = FakeProjectManaging()
        val project = projects.addProject(FilePath("/tmp/p"))
        val availability = FakeAgentAvailabilityChecking()
        availability.setAllAvailable(listOf(ClaudeAgent))
        val launcher = build(projects = projects, availability = availability)
        launcher.start(project.id, ClaudeAgent.id)
        val stateBefore = launcher.runningByProject.value.toMap()

        // Act — notify with a completely unknown session id
        launcher.notifySessionExited(project.id, kotlin.uuid.Uuid.random())

        // Assert — state is unchanged
        assertEquals(stateBefore, launcher.runningByProject.value)
    }
}
