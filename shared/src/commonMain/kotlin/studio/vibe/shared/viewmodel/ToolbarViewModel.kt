package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.contract.AIAgentRegistry
import studio.vibe.shared.contract.APIKeyResolving
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalSessionEvent
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.model.AgentExitSequence
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ToolbarState(
    val selectedAgent: AIAgent? = null,
    val isAgentRunning: Boolean = false,
    val activeAgentSessionId: Uuid? = null,
    val agentAvailability: Map<AIAgent, AgentAvailabilityStatus> = emptyMap(),
    val errorMessage: String? = null,
    val isCheckingAvailability: Boolean = false,
)

/**
 * Toolbar ViewModel.
 *
 * Uses [AIAgentRegistry] for the catalogue of available agents — adding a new
 * agent (built-in or plugin) does not require modifying this class.
 *
 * @param scope            ViewModel coroutine scope (typically a `MainScope` derivative).
 * @param blockingDispatcher Dispatcher used for blocking work (PTY process start, file I/O).
 *                          JVM passes `Dispatchers.IO`; macOS Native passes `Dispatchers.Default`
 *                          (Kotlin/Native lacks a dedicated IO dispatcher on the main worker).
 *                          Inject `UnconfinedTestDispatcher` from unit tests.
 */
@OptIn(ExperimentalUuidApi::class)
class ToolbarViewModel(
    private val projectManaging: ProjectManaging,
    private val terminalSessionManaging: TerminalSessionManaging,
    private val agentAvailabilityChecking: AgentAvailabilityChecking,
    private val agentRegistry: AIAgentRegistry,
    private val apiKeyResolving: APIKeyResolving,
    parentScope: CoroutineScope,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(parentScope) {
    private val runningAssistants = mutableMapOf<Uuid, Boolean>()
    private val selectedAgents = mutableMapOf<Uuid, AIAgent>()
    private val agentSessionIds = mutableMapOf<Uuid, Uuid>()

    private var availabilityJob: Job? = null

    private val _state = MutableStateFlow(ToolbarState())
    val state: StateFlow<ToolbarState> = _state.asStateFlow()

    var onResolveHomePath: (() -> String)? = null
    var onResolveEnvVar: ((String) -> String?)? = null

    private fun defaultAgent(): AIAgent? = agentRegistry.snapshot().firstOrNull()

    init {
        // Collect reactive availability updates — no polling needed.
        availabilityJob = scope.launch {
            agentAvailabilityChecking.availabilityFlow.collect { current ->
                _state.update { s -> s.copy(agentAvailability = current) }
            }
        }

        // Session-exit events reset Stop button when agent exits
        scope.launch {
            terminalSessionManaging.sessionEvents.collect { event ->
                if (event is TerminalSessionEvent.ProcessExited) {
                    val projectId = event.projectId
                    val sessionId = event.sessionId
                    if (agentSessionIds[projectId] == sessionId) {
                        runningAssistants.remove(projectId)
                        agentSessionIds.remove(projectId)
                        rebuildState()
                    }
                }
            }
        }

        // Active project changes — rebuild toolbar for the new project.
        scope.launch {
            projectManaging.activeProjectId.collect { rebuildState() }
        }

        // Registry changes — surface plugin additions in the picker immediately.
        scope.launch {
            agentRegistry.agents.collect { rebuildState() }
        }
    }

    fun selectAgent(agent: AIAgent) {
        val projectId = projectManaging.activeProject?.id ?: return
        selectedAgents[projectId] = agent
        rebuildState()
    }

    fun refreshAvailability() {
        _state.update { it.copy(isCheckingAvailability = true) }
        agentAvailabilityChecking.refreshAll()
        // availabilityFlow.collect will pick up the new values automatically;
        // just reset the loading flag after the service has had a chance to emit.
        scope.launch {
            kotlinx.coroutines.delay(100)
            _state.update { s -> s.copy(isCheckingAvailability = false) }
        }
    }

    fun launchAgent() {
        val project = projectManaging.activeProject ?: run {
            _state.update { it.copy(errorMessage = "No active project selected") }
            return
        }
        val projectId = project.id
        val agent = selectedAgents[projectId] ?: defaultAgent() ?: run {
            _state.update { it.copy(errorMessage = "No AI agent available") }
            return
        }
        val availability = agentAvailabilityChecking.check(agent)
        if (availability is AgentAvailabilityStatus.NotInstalled) {
            _state.update { it.copy(errorMessage = availability.installHint) }
            return
        }

        scope.launch {
            val apiKeyValue = resolveApiKey(agent)
            val workingDirectory = project.path.path
            val result = withContext(blockingDispatcher) {
                terminalSessionManaging.startAgentSession(
                    agent = agent,
                    projectId = projectId,
                    workingDirectory = workingDirectory,
                    apiKeyValue = apiKeyValue,
                )
            }
            result.onSuccess { session ->
                runningAssistants[projectId] = true
                agentSessionIds[projectId] = session.id
                rebuildState()
                _state.update { it.copy(errorMessage = null) }
            }.onFailure { e ->
                val msg = e.message?.takeIf { it.isNotBlank() }
                    ?: "Failed to start ${agent.displayName}: ${e::class.simpleName}"
                _state.update { it.copy(errorMessage = msg) }
            }
        }
    }

    fun stopAgent() {
        val projectId = projectManaging.activeProject?.id ?: return
        val sessionId = agentSessionIds[projectId] ?: return
        val agent = selectedAgents[projectId] ?: defaultAgent() ?: return

        runningAssistants.remove(projectId)
        agentSessionIds.remove(projectId)
        rebuildState()

        scope.launch {
            runCatching {
                when (val exitSeq = agent.exitSequence) {
                    is AgentExitSequence.CtrlC -> {
                        terminalSessionManaging.sendInput("", sessionId)
                    }
                    is AgentExitSequence.CtrlCThenCommand -> {
                        terminalSessionManaging.sendInput("", sessionId)
                        kotlinx.coroutines.delay(300)
                        terminalSessionManaging.sendInput("${exitSeq.command}\n", sessionId)
                    }
                }
            }
        }
    }

    fun cleanupProject(projectId: Uuid) {
        runningAssistants.remove(projectId)
        selectedAgents.remove(projectId)
        agentSessionIds.remove(projectId)
        rebuildState()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    override fun dispose() {
        availabilityJob?.cancel()
        availabilityJob = null
        super.dispose()
    }

    private fun resolveApiKey(agent: AIAgent): String? {
        val envVar = agent.apiKeyEnvironmentVariable ?: return null
        return onResolveEnvVar?.invoke(envVar) ?: apiKeyResolving.resolve(envVar)
    }

    private fun rebuildState() {
        val projectId = projectManaging.activeProject?.id
        val agent = projectId?.let { selectedAgents[it] } ?: defaultAgent()
        val isRunning = projectId?.let { runningAssistants[it] } == true
        val agentSessionId = projectId?.let { agentSessionIds[it] }

        _state.update { s ->
            s.copy(
                selectedAgent = agent,
                isAgentRunning = isRunning,
                activeAgentSessionId = agentSessionId,
            )
        }
    }
}
