package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vibe.shared.contract.APIKeyResolving
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalSessionEvent
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.model.AIAssistant
import studio.vibe.shared.model.AgentExitSequence
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ToolbarState(
    val selectedAssistant: AIAssistant = AIAssistant.CLAUDE,
    val isAgentRunning: Boolean = false,
    val activeAgentSessionId: Uuid? = null,
    val agentAvailability: Map<AIAssistant, AgentAvailabilityStatus> = emptyMap(),
    val errorMessage: String? = null,
    val isCheckingAvailability: Boolean = false,
)

@OptIn(ExperimentalUuidApi::class)
class ToolbarViewModel(
    private val projectManaging: ProjectManaging,
    private val terminalSessionManaging: TerminalSessionManaging,
    private val agentAvailabilityChecking: AgentAvailabilityChecking,
    private val apiKeyResolving: APIKeyResolving,
    private val scope: CoroutineScope,
) {
    // ── Per-project state (matches Swift ToolbarViewModel) ─────────────────

    private val runningAssistants = mutableMapOf<Uuid, Boolean>()
    private val selectedAssistants = mutableMapOf<Uuid, AIAssistant>()
    private val agentSessionIds = mutableMapOf<Uuid, Uuid>()

    // ── Public state (derived from per-project maps + active project) ──────

    private val _state = MutableStateFlow(ToolbarState())
    val state: StateFlow<ToolbarState> = _state.asStateFlow()

    /** Platform callback to resolve the home directory path */
    var onResolveHomePath: (() -> String)? = null

    /** Platform callback to resolve environment variables */
    var onResolveEnvVar: ((String) -> String?)? = null

    init {
        // Poll agent availability periodically
        scope.launch {
            kotlinx.coroutines.delay(1_000)
            while (true) {
                val current = agentAvailabilityChecking.availability
                _state.update { s -> s.copy(agentAvailability = current) }
                kotlinx.coroutines.delay(5_000)
            }
        }

        // Subscribe to session exit events so Stop button resets when agent exits
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

        // Watch active project changes — rebuild toolbar state for the new project
        scope.launch {
            projectManaging.activeProjectId.collect {
                rebuildState()
            }
        }
    }

    fun selectAssistant(assistant: AIAssistant) {
        val projectId = projectManaging.activeProject?.id ?: return
        selectedAssistants[projectId] = assistant
        rebuildState()
    }

    fun refreshAvailability() {
        _state.update { it.copy(isCheckingAvailability = true) }
        agentAvailabilityChecking.refreshAll()
        scope.launch {
            kotlinx.coroutines.delay(1_500)
            _state.update { s ->
                s.copy(
                    agentAvailability = agentAvailabilityChecking.availability,
                    isCheckingAvailability = false,
                )
            }
        }
    }

    fun launchAgent() {
        val project = projectManaging.activeProject ?: run {
            _state.update { it.copy(errorMessage = "No active project selected") }
            return
        }
        val projectId = project.id
        val assistant = selectedAssistants[projectId] ?: AIAssistant.CLAUDE
        val availability = agentAvailabilityChecking.check(assistant)
        // Block only on explicit NotInstalled. Allow launch when status is
        // still Checking (availability not yet resolved after app start).
        if (availability is AgentAvailabilityStatus.NotInstalled) {
            _state.update { it.copy(errorMessage = availability.installHint) }
            return
        }

        scope.launch {
            val apiKeyValue = resolveApiKey(assistant)
            val workingDirectory = project.path.path
            // startAgentSession calls PtyProcessBuilder.start() which blocks —
            // must run on IO dispatcher, not Main (EDT).
            val session = withContext(Dispatchers.IO) {
                terminalSessionManaging.startAgentSession(
                    agent = assistant,
                    projectId = projectId,
                    workingDirectory = workingDirectory,
                    apiKeyValue = apiKeyValue,
                )
            }
            if (session != null) {
                runningAssistants[projectId] = true
                agentSessionIds[projectId] = session.id
                rebuildState()
                _state.update { it.copy(errorMessage = null) }
            } else {
                _state.update { it.copy(errorMessage = "Failed to start agent session") }
            }
        }
    }

    fun stopAgent() {
        val projectId = projectManaging.activeProject?.id ?: return
        val sessionId = agentSessionIds[projectId] ?: return
        val assistant = selectedAssistants[projectId] ?: AIAssistant.CLAUDE

        // Optimistic UI reset — matches Swift behaviour where the button
        // switches to Play immediately after sending the stop signal.
        runningAssistants.remove(projectId)
        agentSessionIds.remove(projectId)
        rebuildState()

        scope.launch {
            runCatching {
                when (val exitSeq = assistant.exitSequence) {
                    is AgentExitSequence.CtrlC -> {
                        terminalSessionManaging.sendInput("\u0003", sessionId)
                    }
                    is AgentExitSequence.CtrlCThenCommand -> {
                        terminalSessionManaging.sendInput("\u0003", sessionId)
                        kotlinx.coroutines.delay(300)
                        terminalSessionManaging.sendInput("${exitSeq.command}\n", sessionId)
                    }
                }
            }
        }
    }

    /** Release all per-project cached state for a removed project. */
    fun cleanupProject(projectId: Uuid) {
        runningAssistants.remove(projectId)
        selectedAssistants.remove(projectId)
        agentSessionIds.remove(projectId)
        rebuildState()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun resolveApiKey(assistant: AIAssistant): String? {
        val envVar = assistant.apiKeyEnvironmentVariable ?: return null
        return onResolveEnvVar?.invoke(envVar) ?: apiKeyResolving.resolve(envVar)
    }

    /** Rebuilds the public ToolbarState from per-project maps. */
    private fun rebuildState() {
        val projectId = projectManaging.activeProject?.id
        val assistant = projectId?.let { selectedAssistants[it] } ?: AIAssistant.CLAUDE
        val isRunning = projectId?.let { runningAssistants[it] } == true
        val agentSessionId = projectId?.let { agentSessionIds[it] }

        _state.update { s ->
            s.copy(
                selectedAssistant = assistant,
                isAgentRunning = isRunning,
                activeAgentSessionId = agentSessionId,
            )
        }
    }
}
