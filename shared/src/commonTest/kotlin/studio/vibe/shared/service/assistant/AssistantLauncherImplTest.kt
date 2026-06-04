@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package studio.vibe.shared.service.assistant

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSessionState
import studio.vibe.shared.service.agent.ClaudeAgent
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
    ) = AssistantLauncherImpl(
        projectManaging = projects,
        terminalSessionManaging = terminal,
        agentRegistry = registry,
        agentAvailabilityChecking = availability,
        apiKeyResolving = apiKeys,
        blockingDispatcher = UnconfinedTestDispatcher(),
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
}
