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
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.contract.AIAgentRegistry
import studio.vibe.shared.contract.APIKeyResolving
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.EnvVarResolving
import studio.vibe.shared.contract.PathResolving
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalSessionEvent
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.usecase.AssistantLauncher

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
 * Launch/stop logic is delegated to [AssistantLauncher] so [RemoteControlServer]
 * can trigger the same behaviour without depending on the ViewModel layer.
 *
 * @param scope              ViewModel coroutine scope (typically a `MainScope` derivative).
 * @param blockingDispatcher Dispatcher used for blocking work (PTY process start, file I/O).
 *                           JVM passes `Dispatchers.IO`; macOS Native passes `Dispatchers.Default`.
 *                           Inject `UnconfinedTestDispatcher` from unit tests.
 * @param assistantLauncher  Shared start/stop domain service. Must be injected explicitly.
 */
/** Per-project mutable state kept as a snapshot inside a StateFlow. */
@OptIn(ExperimentalUuidApi::class)
private data class ToolbarProjectData(
    val selectedAgents: Map<Uuid, AIAgent> = emptyMap(),
)

@OptIn(ExperimentalUuidApi::class)
class ToolbarViewModel(
    private val projectManaging: ProjectManaging,
    private val terminalSessionManaging: TerminalSessionManaging,
    private val agentAvailabilityChecking: AgentAvailabilityChecking,
    private val agentRegistry: AIAgentRegistry,
    private val apiKeyResolving: APIKeyResolving,
    parentScope: CoroutineScope,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    assistantLauncher: AssistantLauncher,
    /**
     * Platform hook for resolving the user's home directory.
     * JVM: `System.getProperty("user.home")`. Injected so platform code stays out of commonMain.
     */
    private val pathResolving: PathResolving? = null,
    /**
     * Platform hook for reading process environment variables.
     * When provided, it is forwarded to [launcher] so env-var API keys are resolved
     * via the process environment on JVM (same behaviour as the previous callback var).
     */
    private val envVarResolving: EnvVarResolving? = null,
) : BaseViewModel(parentScope) {

    private val launcher: AssistantLauncher = assistantLauncher

    private val _projectData = MutableStateFlow(ToolbarProjectData())

    private var availabilityJob: Job? = null

    private val _state = MutableStateFlow(ToolbarState())
    val state: StateFlow<ToolbarState> = _state.asStateFlow()

    private fun defaultAgent(): AIAgent? = agentRegistry.snapshot().firstOrNull()

    init {
        // Forward env-var resolver to launcher so process environment is used on JVM.
        if (envVarResolving != null) {
            launcher.onResolveEnvVar = { name -> envVarResolving.resolve(name) }
        }

        // Collect reactive availability updates — no polling needed.
        availabilityJob = scope.launch {
            agentAvailabilityChecking.availabilityFlow.collect { current ->
                _state.update { s -> s.copy(agentAvailability = current) }
            }
        }

        // Session-exit events reset Stop button when agent exits.
        scope.launch {
            terminalSessionManaging.sessionEvents.collect { event ->
                if (event is TerminalSessionEvent.ProcessExited) {
                    launcher.notifySessionExited(event.projectId, event.sessionId)
                    rebuildState()
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

        // Keep toolbar in sync when launcher state changes (e.g. start triggered
        // from RemoteControlServer while the toolbar is visible).
        scope.launch {
            launcher.runningByProject.collect { rebuildState() }
        }
    }

    fun selectAgent(agent: AIAgent) {
        val projectId = projectManaging.activeProject?.id ?: return
        _projectData.update { it.copy(selectedAgents = it.selectedAgents + (projectId to agent)) }
        rebuildState()
    }

    fun refreshAvailability() {
        _state.update { it.copy(isCheckingAvailability = true) }
        agentAvailabilityChecking.refreshAll()
        // availabilityFlow.collect will pick up the new values automatically;
        // the loading flag is reset once the service emits new values (observed
        // in the availability collect block). No artificial delay needed.
        _state.update { s -> s.copy(isCheckingAvailability = false) }
    }

    fun launchAgent() {
        val project = projectManaging.activeProject ?: run {
            _state.update { it.copy(errorMessage = "No active project selected") }
            return
        }
        val projectId = project.id
        val agent = _projectData.value.selectedAgents[projectId] ?: defaultAgent() ?: run {
            _state.update { it.copy(errorMessage = "No AI agent available") }
            return
        }

        // Availability guard stays in ViewModel — it is a UI-layer concern.
        val availability = agentAvailabilityChecking.check(agent)
        if (availability is AgentAvailabilityStatus.NotInstalled) {
            _state.update { it.copy(errorMessage = availability.installHint) }
            return
        }

        scope.launch {
            val result = launcher.start(projectId, agent.id)
            result.onSuccess {
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
        val project = projectManaging.activeProject ?: return
        val projectId = project.id
        val agent = _projectData.value.selectedAgents[projectId] ?: defaultAgent() ?: return

        // Eagerly update UI — same optimistic behaviour as before.
        rebuildState()

        scope.launch {
            launcher.stop(projectId, agent.id)
        }
    }

    fun cleanupProject(projectId: Uuid) {
        _projectData.update { data ->
            data.copy(selectedAgents = data.selectedAgents - projectId)
        }
        launcher.removeProject(projectId)
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

    private fun rebuildState() {
        val projectId = projectManaging.activeProject?.id
        val running = projectId?.let { launcher.runningByProject.value[it] } ?: emptySet()
        val data = _projectData.value
        val agent = projectId?.let { data.selectedAgents[it] } ?: defaultAgent()
        val isRunning = running.isNotEmpty()
        // Surface the session UUID of the currently selected agent so the UI can
        // display a session indicator / link to the terminal tab.
        val agentSessionId: Uuid? = if (projectId != null && agent != null) {
            launcher.sessionIdFor(projectId, agent.id)
        } else null

        _state.update { s ->
            s.copy(
                selectedAgent = agent,
                isAgentRunning = isRunning,
                activeAgentSessionId = agentSessionId,
            )
        }
    }
}
