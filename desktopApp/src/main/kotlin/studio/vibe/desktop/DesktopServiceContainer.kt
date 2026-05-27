@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.Uuid
import studio.vibe.shared.contract.AICommitServicing
import studio.vibe.shared.contract.APIKeyResolving
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalSessionEvent
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.model.AIAssistant
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.SplitDirection
import studio.vibe.shared.model.TabActivityState
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSize
import studio.vibe.shared.platform.JvmBinaryResolver
import studio.vibe.shared.platform.JvmCredentialStorage
import studio.vibe.shared.platform.JvmPersistenceStore
import studio.vibe.shared.platform.JvmProcessRunner
import studio.vibe.shared.platform.JvmSettingsStorage
import studio.vibe.shared.service.agent.AgentAvailabilityServiceImpl
import studio.vibe.shared.service.filetree.FileTreeBuilder
import studio.vibe.shared.service.git.GitCommandExecutor
import studio.vibe.shared.preferences.CodeSpeakPreferences
import studio.vibe.shared.preferences.GeneralPreferences
import studio.vibe.shared.preferences.RemoteControlPreferences
import studio.vibe.shared.service.persistence.ProjectStoreImpl
import studio.vibe.desktop.terminal.DesktopTerminalService
import studio.vibe.shared.viewmodel.GitSidebarViewModel
import studio.vibe.shared.viewmodel.ToolbarViewModel

/**
 * Manual DI container for the Desktop application.
 *
 * Wires JVM platform implementations to shared KMP services and ViewModels.
 * Lifetime: tied to the application window. Call [dispose] on window close.
 */
class DesktopServiceContainer {

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Platform implementations (jvmMain) ────────────────────────────────────

    val processRunner = JvmProcessRunner()
    val persistenceStore = JvmPersistenceStore()
    val credentialStorage = JvmCredentialStorage()
    val binaryResolver = JvmBinaryResolver()
    val settingsStorage = JvmSettingsStorage()

    // ── Shared services (commonMain) ──────────────────────────────────────────

    val gitService: GitServicing = GitCommandExecutor(processRunner)

    val projectStore: ProjectManaging = ProjectStoreImpl(persistenceStore).also { it.load() }

    val agentAvailabilityChecking: AgentAvailabilityChecking = AgentAvailabilityServiceImpl(
        binaryResolver = binaryResolver,
        credentialStorage = credentialStorage,
        scope = scope,
    ).also { it.refreshAll() }

    val apiKeyResolving: APIKeyResolving = EnvironmentAPIKeyResolver()

    val aiCommitService: AICommitServicing = StubAICommitService()

    val generalPreferences: GeneralPreferences = GeneralPreferences(settingsStorage)

    val remoteControlPreferences: RemoteControlPreferences = RemoteControlPreferences(
        storage = settingsStorage,
        credentialStorage = credentialStorage,
    )

    val codeSpeakPreferences: CodeSpeakPreferences = CodeSpeakPreferences(settingsStorage)

    val fileTreeBuilder: FileTreeBuilder = FileTreeBuilder(persistenceStore)

    /** Real pty4j-backed terminal service.  Replaces [StubTerminalSessionManaging]. */
    val terminalService: DesktopTerminalService = DesktopTerminalService(serviceScope = scope)

    // ── ViewModels ────────────────────────────────────────────────────────────

    val toolbarViewModel: ToolbarViewModel by lazy {
        ToolbarViewModel(
            projectManaging = projectStore,
            terminalSessionManaging = terminalService,
            agentAvailabilityChecking = agentAvailabilityChecking,
            apiKeyResolving = apiKeyResolving,
            scope = scope,
        ).also { vm ->
            vm.onResolveHomePath = { System.getProperty("user.home") ?: "" }
            vm.onResolveEnvVar = { name -> System.getenv(name) }
        }
    }

    val gitSidebarViewModel: GitSidebarViewModel by lazy {
        GitSidebarViewModel(
            gitService = gitService,
            aiCommitService = aiCommitService,
            scope = scope,
        ).also { vm ->
            vm.onOpenURL = { url ->
                try {
                    val desktop = java.awt.Desktop.getDesktop()
                    if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                        desktop.browse(java.net.URI(url))
                    }
                } catch (_: Exception) { /* Ignore — best effort */ }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun dispose() {
        projectStore.save()
        terminalService.dispose()   // kills all live PTY processes before scope cancel
        scope.cancel()
    }
}

// ── Lightweight stubs for services not yet ported ─────────────────────────────

/**
 * Resolves API keys from process environment variables.
 * Replaces NSProcessInfo.processInfo.environment on JVM.
 */
private class EnvironmentAPIKeyResolver : APIKeyResolving {
    override fun resolve(envVar: String): String? = System.getenv(envVar)
}

/**
 * Stub for AICommitServicing until a real implementation is wired in.
 * Returns a placeholder message — does not make network calls.
 */
private class StubAICommitService : AICommitServicing {
    override suspend fun generateCommitMessage(diff: String): String =
        "chore: update files\n\nAI commit service not yet configured."
}

/**
 * Stub for TerminalSessionManaging until pty4j integration is complete.
 * All operations are no-ops or return safe defaults.
 * [createSession] and [split] throw [UnsupportedOperationException] — they must not
 * be called before the real implementation is wired in.
 */
private class StubTerminalSessionManaging : TerminalSessionManaging {

    private val _sessionsByProject =
        MutableStateFlow<Map<Uuid, List<TerminalSession>>>(emptyMap())
    private val _projectActivityStates =
        MutableStateFlow<Map<Uuid, TabActivityState>>(emptyMap())
    private val _sessionEvents =
        MutableSharedFlow<TerminalSessionEvent>()

    override val sessionsByProject: StateFlow<Map<Uuid, List<TerminalSession>>> =
        _sessionsByProject
    override val projectActivityStates: StateFlow<Map<Uuid, TabActivityState>> =
        _projectActivityStates
    override val sessionEvents: Flow<TerminalSessionEvent> =
        _sessionEvents

    override fun createSession(
        projectId: Uuid,
        shell: String?,
        workingDirectory: FilePath?,
        size: TerminalSize,
    ): TerminalSession = error("pty4j integration pending — createSession not available in scaffold")

    override fun resize(sessionId: Uuid, size: TerminalSize) = Unit

    override fun killSession(sessionId: Uuid, force: Boolean) = Unit

    override fun killAllSessions(projectId: Uuid) = Unit

    override fun split(
        sessionId: Uuid,
        direction: SplitDirection,
        size: TerminalSize,
    ): TerminalSession = error("pty4j integration pending — split not available in scaffold")

    override fun startAgentSession(
        agent: AIAssistant,
        projectId: Uuid,
        workingDirectory: String,
        apiKeyValue: String?,
    ): TerminalSession? = null  // No-op: toolbar will show error message

    override fun session(id: Uuid): TerminalSession? = null

    override fun sessions(projectId: Uuid): List<TerminalSession> = emptyList()

    override fun markProjectSeen(projectId: Uuid) = Unit

    override fun sendInput(text: String, sessionId: Uuid) = Unit

    override fun scrollbackContent(sessionId: Uuid): String? = null
}
