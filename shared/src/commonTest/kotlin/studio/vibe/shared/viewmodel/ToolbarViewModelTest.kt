package studio.vibe.shared.viewmodel

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.TerminalSessionEvent
import studio.vibe.shared.service.agent.ClaudeAgent
import studio.vibe.shared.service.agent.CodexAgent
import studio.vibe.shared.service.agent.DefaultAIAgentRegistry
import studio.vibe.shared.testutil.FakeAPIKeyResolving
import studio.vibe.shared.testutil.FakeAgentAvailabilityChecking
import studio.vibe.shared.testutil.FakeProjectManaging
import studio.vibe.shared.testutil.FakeTerminalSessionManaging
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Demonstrates that [ToolbarViewModel] is fully mockable through the
 * [FakeProjectManaging] / [FakeTerminalSessionManaging] / etc. doubles in
 * `testutil`.  Establishes the pattern other ViewModel tests should follow.
 */
@OptIn(ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ToolbarViewModelTest {

    private fun build(
        projects: FakeProjectManaging = FakeProjectManaging(),
        terminal: FakeTerminalSessionManaging = FakeTerminalSessionManaging(),
        availability: FakeAgentAvailabilityChecking = FakeAgentAvailabilityChecking(),
        registry: DefaultAIAgentRegistry = DefaultAIAgentRegistry(),
        apiKeys: FakeAPIKeyResolving = FakeAPIKeyResolving(),
        scope: TestScope = TestScope(UnconfinedTestDispatcher()),
    ): Pair<ToolbarViewModel, ToolbarTestEnv> {
        val vm = ToolbarViewModel(
            projectManaging = projects,
            terminalSessionManaging = terminal,
            agentAvailabilityChecking = availability,
            agentRegistry = registry,
            apiKeyResolving = apiKeys,
            scope = scope,
            blockingDispatcher = UnconfinedTestDispatcher(),
        )
        return vm to ToolbarTestEnv(projects, terminal, availability, registry, apiKeys, scope)
    }

    private class ToolbarTestEnv(
        val projects: FakeProjectManaging,
        val terminal: FakeTerminalSessionManaging,
        val availability: FakeAgentAvailabilityChecking,
        val registry: DefaultAIAgentRegistry,
        val apiKeys: FakeAPIKeyResolving,
        val scope: TestScope,
    )

    @Test
    fun initialState_hasFirstRegistryAgentSelected() = runTest {
        val (vm, _) = build()
        assertEquals(ClaudeAgent, vm.state.value.selectedAgent)
    }

    @Test
    fun launchAgent_noActiveProject_reportsError() = runTest {
        val (vm, _) = build()
        vm.launchAgent()
        assertEquals("No active project selected", vm.state.value.errorMessage)
    }

    @Test
    fun launchAgent_notInstalled_surfacesInstallHint() = runTest {
        val (vm, env) = build()
        val project = env.projects.addProject(FilePath("/tmp/p"))
        env.projects.setActiveProjectId(project.id)
        env.availability.setAvailability(
            ClaudeAgent,
            AgentAvailabilityStatus.NotInstalled(installHint = "npm install foo"),
        )

        vm.launchAgent()

        assertEquals("npm install foo", vm.state.value.errorMessage)
        assertEquals(0, env.terminal.startAgentCallCount)
    }

    @Test
    fun launchAgent_available_startsSessionAndUpdatesState() = runTest {
        val (vm, env) = build()
        val project = env.projects.addProject(FilePath("/tmp/p2"))
        env.projects.setActiveProjectId(project.id)
        env.availability.setAvailability(
            ClaudeAgent,
            AgentAvailabilityStatus.Available(path = "/usr/bin/claude", hasAPIKey = true),
        )

        vm.launchAgent()

        assertEquals(1, env.terminal.startAgentCallCount)
        val (agent, _, workDir) = env.terminal.startAgentCalls.single()
        assertEquals(ClaudeAgent, agent)
        assertEquals(project.path.path, workDir)
        assertTrue(vm.state.value.isAgentRunning)
        assertNotNull(vm.state.value.activeAgentSessionId)
    }

    @Test
    fun selectAgent_switchesSelection_andUsesItOnLaunch() = runTest {
        val (vm, env) = build()
        val project = env.projects.addProject(FilePath("/tmp/p3"))
        env.projects.setActiveProjectId(project.id)
        env.availability.setAllAvailable(env.registry.snapshot())

        vm.selectAgent(CodexAgent)
        assertEquals(CodexAgent, vm.state.value.selectedAgent)

        vm.launchAgent()
        assertEquals(CodexAgent, env.terminal.startAgentCalls.single().first)
    }

    @Test
    fun processExitedEvent_clearsIsAgentRunning() = runTest {
        val (vm, env) = build()
        val project = env.projects.addProject(FilePath("/tmp/p4"))
        env.projects.setActiveProjectId(project.id)
        env.availability.setAllAvailable(env.registry.snapshot())

        // Pre-populate terminal so we know which session id will be returned.
        val session = TerminalSession(
            projectId = project.id,
            title = "claude",
            state = TerminalSessionState.Running,
            isAgentSession = true,
        )
        env.terminal.startAgentSessionResult = Result.success(session)

        vm.launchAgent()
        assertTrue(vm.state.value.isAgentRunning)

        env.terminal.emitEvent(
            TerminalSessionEvent.ProcessExited(
                sessionId = session.id,
                projectId = project.id,
                exitCode = 0,
            ),
        )

        assertEquals(false, vm.state.value.isAgentRunning)
        assertNull(vm.state.value.activeAgentSessionId)
    }
}
