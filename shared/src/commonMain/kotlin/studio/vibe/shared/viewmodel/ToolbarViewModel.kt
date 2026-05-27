package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.APIKeyResolving
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.AgentAvailabilityStatus
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.model.AIAssistant
import studio.vibe.shared.model.AgentExitSequence
import studio.vibe.shared.model.TerminalSession
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
    private val _state = MutableStateFlow(ToolbarState())
    val state: StateFlow<ToolbarState> = _state.asStateFlow()

    /** Platform callback to resolve the home directory path (replaces NSHomeDirectory()) */
    var onResolveHomePath: (() -> String)? = null

    /** Platform callback to resolve environment variables (replaces ProcessInfo.processInfo.environment) */
    var onResolveEnvVar: ((String) -> String?)? = null

    fun selectAssistant(assistant: AIAssistant) {
        _state.update { it.copy(selectedAssistant = assistant) }
    }

    fun refreshAvailability() {
        _state.update { it.copy(isCheckingAvailability = true) }
        agentAvailabilityChecking.refreshAll()
        _state.update { s ->
            s.copy(
                agentAvailability = agentAvailabilityChecking.availability,
                isCheckingAvailability = false,
            )
        }
    }

    fun launchAgent() {
        val project = projectManaging.activeProject ?: run {
            _state.update { it.copy(errorMessage = "No active project selected") }
            return
        }
        val assistant = _state.value.selectedAssistant
        val availability = agentAvailabilityChecking.check(assistant)
        if (!agentAvailabilityChecking.canLaunch(assistant)) {
            val hint = when (availability) {
                is AgentAvailabilityStatus.NotInstalled -> availability.installHint
                else -> "Agent is not available"
            }
            _state.update { it.copy(errorMessage = hint) }
            return
        }

        scope.launch {
            val apiKeyValue = resolveApiKey(assistant)
            val workingDirectory = project.path.path
            val session = terminalSessionManaging.startAgentSession(
                agent = assistant,
                projectId = project.id,
                workingDirectory = workingDirectory,
                apiKeyValue = apiKeyValue,
            )
            if (session != null) {
                _state.update { s ->
                    s.copy(
                        isAgentRunning = true,
                        activeAgentSessionId = session.id,
                        errorMessage = null,
                    )
                }
            } else {
                _state.update { it.copy(errorMessage = "Failed to start agent session") }
            }
        }
    }

    fun stopAgent() {
        val sessionId = _state.value.activeAgentSessionId ?: return
        val assistant = _state.value.selectedAssistant

        scope.launch {
            runCatching {
                when (val exitSeq = assistant.exitSequence) {
                    is AgentExitSequence.CtrlC -> {
                        terminalSessionManaging.sendInput("\u0003", sessionId)
                    }
                    is AgentExitSequence.CtrlCThenCommand -> {
                        terminalSessionManaging.sendInput("\u0003", sessionId)
                        // Small delay before sending command — give the process time to respond
                        kotlinx.coroutines.delay(300)
                        terminalSessionManaging.sendInput("${exitSeq.command}\n", sessionId)
                    }
                }
            }
            _state.update { s ->
                s.copy(
                    isAgentRunning = false,
                    activeAgentSessionId = null,
                )
            }
        }
    }

    fun onSessionExited(sessionId: Uuid) {
        if (_state.value.activeAgentSessionId == sessionId) {
            _state.update { s ->
                s.copy(
                    isAgentRunning = false,
                    activeAgentSessionId = null,
                )
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun resolveApiKey(assistant: AIAssistant): String? {
        val envVar = assistant.apiKeyEnvironmentVariable ?: return null
        // Try platform callback first, then fall back to APIKeyResolving service
        return onResolveEnvVar?.invoke(envVar) ?: apiKeyResolving.resolve(envVar)
    }
}
