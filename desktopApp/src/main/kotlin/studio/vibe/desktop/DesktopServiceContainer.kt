@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.contract.AIAgentRegistry
import studio.vibe.shared.contract.AICommitServicing
import studio.vibe.shared.contract.APIKeyResolving
import studio.vibe.shared.contract.AgentAvailabilityChecking
import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.SessionPersisting
import studio.vibe.shared.contract.TerminalSessionEvent
import studio.vibe.shared.contract.TerminalSessionManaging
import studio.vibe.shared.coordinator.AppNavigationCoordinator
import studio.vibe.shared.service.agent.DefaultAIAgentRegistry
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.SplitDirection
import studio.vibe.shared.model.TabActivityState
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.model.TerminalSize
import studio.vibe.shared.platform.JvmBinaryResolver
import studio.vibe.shared.platform.JvmCredentialStorage
import studio.vibe.shared.platform.JvmFileSystemWatchingService
import studio.vibe.shared.platform.JvmPersistenceStore
import studio.vibe.shared.platform.JvmProcessRunner
import studio.vibe.shared.platform.JvmSettingsStorage
import studio.vibe.shared.service.agent.AgentAvailabilityServiceImpl
import studio.vibe.shared.service.ai.AICommitServiceImpl
import studio.vibe.shared.service.codespeak.CodeSpeakServiceImpl
import studio.vibe.shared.service.filetree.FileTreeBuilder
import studio.vibe.shared.service.git.GitCommandExecutor
import studio.vibe.shared.service.git.GitStatusPollerImpl
import studio.vibe.shared.preferences.CodeSpeakPreferences
import studio.vibe.shared.preferences.GeneralPreferences
import studio.vibe.shared.preferences.RemoteControlPreferences
import studio.vibe.shared.service.persistence.ProjectStoreImpl
import studio.vibe.shared.service.persistence.SessionStoreImpl
import studio.vibe.shared.usecase.RestoreSessionUseCase
import studio.vibe.desktop.remote.RemoteControlServer
import studio.vibe.desktop.terminal.DesktopTerminalService
import studio.vibe.shared.viewmodel.FileTreeViewModel
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

    val projectStore: ProjectManaging = ProjectStoreImpl(
        persistence = persistenceStore,
        scope = scope,
    ).also { store ->
        // JVM-only: blocking load during DI startup is acceptable here — we are
        // not on the EDT yet.  Kotlin/Native containers must use suspend init.
        runBlocking { store.load() }
    }

    val agentRegistry: AIAgentRegistry = DefaultAIAgentRegistry()

    val agentAvailabilityChecking: AgentAvailabilityChecking = AgentAvailabilityServiceImpl(
        binaryResolver = binaryResolver,
        credentialStorage = credentialStorage,
        registry = agentRegistry,
        scope = scope,
    ).also { it.refreshAll() }

    val apiKeyResolving: APIKeyResolving = EnvironmentAPIKeyResolver()

    /** Shared JSON instance — single allocation, reused by session store and HTTP client. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        prettyPrint = false
    }

    /** Ktor HTTP client backed by OkHttp (JVM engine). Single instance for the app lifetime. */
    val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    val aiCommitService: AICommitServicing = AICommitServiceImpl(
        httpClient = httpClient,
        apiKeyResolver = apiKeyResolving,
    )

    val generalPreferences: GeneralPreferences = GeneralPreferences(settingsStorage)

    val remoteControlPreferences: RemoteControlPreferences = RemoteControlPreferences(
        storage = settingsStorage,
        credentialStorage = credentialStorage,
    )

    val codeSpeakPreferences: CodeSpeakPreferences = CodeSpeakPreferences(settingsStorage)

    val fileTreeBuilder: FileTreeBuilder = FileTreeBuilder(persistenceStore)

    val fileSystemWatchingService: JvmFileSystemWatchingService =
        JvmFileSystemWatchingService(scope = scope)

    val fileTreeViewModel: FileTreeViewModel = FileTreeViewModel(
        persistenceStore = persistenceStore,
        fileSystemWatchingService = fileSystemWatchingService,
        parentScope = scope,
    )

    val gitStatusPoller: GitStatusPollerImpl = GitStatusPollerImpl(
        gitService = gitService,
        scope = scope,
    )

    /** Real pty4j-backed terminal service.  Replaces [StubTerminalSessionManaging]. */
    val terminalService: DesktopTerminalService = DesktopTerminalService(
        serviceScope = scope,
        generalPreferences = generalPreferences,
    )

    val sessionStore: SessionPersisting = SessionStoreImpl(
        persistence = persistenceStore,
        json = json,
    )

    val restoreSessionUseCase: RestoreSessionUseCase = RestoreSessionUseCase(
        projectManager = projectStore,
        terminalManager = terminalService,
        sessionPersistence = sessionStore,
    )

    val codeSpeakService: CodeSpeakServiceImpl = CodeSpeakServiceImpl(
        persistence = persistenceStore,
    )

    val navigationCoordinator: AppNavigationCoordinator = AppNavigationCoordinator()

    /**
     * Embedded HTTP/WS server for Remote Control.
     *
     * Wired to [remoteControlPreferences] and [terminalService].
     * Auto-starts when [remoteControlPreferences.remoteControlEnabled] is `true`
     * at app launch (handled in [Main.kt] / app entry point after container creation).
     * Call [remoteControlServer.stop] and [remoteControlServer.dispose] in [dispose].
     */
    val remoteControlServer: RemoteControlServer = RemoteControlServer(
        preferences = remoteControlPreferences,
        terminalService = terminalService,
    )

    // ── ViewModels ────────────────────────────────────────────────────────────

    val toolbarViewModel: ToolbarViewModel by lazy {
        ToolbarViewModel(
            projectManaging = projectStore,
            terminalSessionManaging = terminalService,
            agentAvailabilityChecking = agentAvailabilityChecking,
            agentRegistry = agentRegistry,
            apiKeyResolving = apiKeyResolving,
            parentScope = scope,
            blockingDispatcher = Dispatchers.IO,
        ).also { vm ->
            vm.onResolveHomePath = { System.getProperty("user.home") ?: "" }
            vm.onResolveEnvVar = { name -> System.getenv(name) }
        }
    }

    val gitSidebarViewModel: GitSidebarViewModel by lazy {
        GitSidebarViewModel(
            gitService = gitService,
            aiCommitService = aiCommitService,
            parentScope = scope,
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
        // Final synchronous flush so unsaved in-memory edits hit disk before exit.
        runBlocking { projectStore.save() }
        gitStatusPoller.stopPolling()
        // Dispose ViewModels — cancels their child scopes and cleans up resources before
        // the parent scope is cancelled. fileTreeViewModel is always initialized (eager).
        // The lazy VMs are safe to access here: if never initialized the lazy block runs
        // but scope.cancel() follows immediately, so no work is started.
        fileTreeViewModel.dispose()
        toolbarViewModel.dispose()
        gitSidebarViewModel.dispose()
        fileSystemWatchingService.unwatchAll()
        // Stop Remote Control server before terminal service so bridges can detach cleanly.
        remoteControlServer.stop()
        remoteControlServer.dispose()
        terminalService.dispose()   // kills all live PTY processes before scope cancel
        httpClient.close()
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
        agent: AIAgent,
        projectId: Uuid,
        workingDirectory: String,
        apiKeyValue: String?,
    ): Result<TerminalSession> =
        Result.failure(IllegalStateException("No active project — open a folder first"))

    override fun session(id: Uuid): TerminalSession? = null

    override fun sessions(projectId: Uuid): List<TerminalSession> = emptyList()

    override fun markProjectSeen(projectId: Uuid) = Unit

    override fun sendInput(text: String, sessionId: Uuid) = Unit

    override fun scrollbackContent(sessionId: Uuid): String? = null
}
