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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid
import studio.vibe.shared.core.common.AgentSessionLog
import studio.vibe.shared.core.common.AIAgentRegistry
import studio.vibe.shared.core.common.AICommitServicing
import studio.vibe.shared.core.common.APIKeyResolving
import studio.vibe.shared.core.common.AgentAvailabilityChecking
import studio.vibe.shared.feature.git.domain.contract.GitServicing
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.feature.session.domain.contract.SessionPersisting
import studio.vibe.shared.coordinator.AppNavigationCoordinator
import studio.vibe.shared.core.common.DefaultAIAgentRegistry
import studio.vibe.shared.core.common.FilePath
import studio.vibe.desktop.platform.EnvironmentAPIKeyResolver
import studio.vibe.desktop.platform.JvmEnvVarResolving
import studio.vibe.desktop.platform.JvmPathResolving
import studio.vibe.desktop.platform.JvmUrlOpening
import studio.vibe.shared.platform.JvmBinaryResolver
import studio.vibe.shared.platform.JvmCredentialStorage
import studio.vibe.shared.platform.JvmFileSystemWatchingService
import studio.vibe.shared.platform.JvmPersistenceStore
import studio.vibe.shared.platform.JvmProcessRunner
import studio.vibe.shared.platform.JvmSettingsStorage
import studio.vibe.shared.core.common.AgentAvailabilityServiceImpl
import studio.vibe.shared.core.common.AICommitServiceImpl
import studio.vibe.shared.feature.codespeak.domain.contract.CodeSpeakServicing
import studio.vibe.shared.feature.tabbar.presentation.FreeTabManaging
import studio.vibe.shared.feature.codespeak.data.CodeSpeakServiceImpl
import studio.vibe.shared.feature.tabbar.data.FreeTabStoreImpl
import studio.vibe.shared.feature.filetree.data.FileTreeBuilder
import studio.vibe.shared.feature.git.data.GitCommandExecutor
import studio.vibe.shared.feature.git.data.GitStatusPollerImpl
import studio.vibe.shared.feature.codespeak.data.CodeSpeakPreferences
import studio.vibe.shared.feature.settings.data.GeneralPreferences
import studio.vibe.shared.feature.settings.data.RemoteControlPreferences
import studio.vibe.shared.core.common.AgentSessionLogImpl
import studio.vibe.shared.feature.project.data.ProjectStoreImpl
import studio.vibe.shared.feature.session.data.SessionStoreImpl
import studio.vibe.shared.core.common.CaptureAgentSessionIdUseCase
import studio.vibe.shared.core.common.CaptureAgentSessionIdParams
import studio.vibe.shared.feature.terminal.domain.usecase.CloseTerminalSessionUseCase
import studio.vibe.shared.feature.terminal.domain.usecase.CreateTerminalSessionUseCase
import studio.vibe.shared.core.common.ListRecentAgentSessionsUseCase
import studio.vibe.shared.feature.terminal.domain.usecase.ListTerminalSessionsUseCase
import studio.vibe.shared.feature.session.domain.usecase.RestoreSessionUseCase
import studio.vibe.shared.feature.codespeak.domain.usecase.RunSpecBuildUseCase
import studio.vibe.shared.feature.terminal.domain.usecase.SendTerminalInputUseCase
import studio.vibe.desktop.remote.RemoteControlServer
import studio.vibe.desktop.terminal.DesktopTerminalService
import studio.vibe.desktop.terminal.ScrollbackPersistCoordinator
import studio.vibe.shared.feature.assistant.data.AssistantLauncherImpl
import studio.vibe.shared.feature.assistant.domain.contract.AssistantLaunching
import studio.vibe.shared.feature.remote.data.RemoteAuthServiceImpl
import studio.vibe.shared.security.JavaSecureRandom
import studio.vibe.shared.feature.git.data.GitURLConverter
import studio.vibe.shared.feature.filetree.presentation.FileTreeViewModel
import studio.vibe.shared.feature.git.presentation.GitSidebarViewModel
import studio.vibe.shared.feature.toolbar.presentation.ToolbarViewModel
import studio.vibe.desktop.session.AgentSessionDataSource
import studio.vibe.desktop.session.KmpAgentSessionDataSource

/**
 * Manual DI container for the Desktop application.
 *
 * Wires JVM platform implementations to shared KMP services and ViewModels.
 * Lifetime: tied to the application window. Call [dispose] on window close.
 */
class DesktopServiceContainer {

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
    )

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

    val sessionStore: SessionPersisting = SessionStoreImpl(
        persistence = persistenceStore,
        json = json,
    )

    // ── Agent session log (created before terminalService; used by callbacks) ─

    /**
     * KMP-backed agent session log.
     *
     * Persists [studio.vibe.shared.contract.AgentSessionRecord]s to
     * `agent-sessions.jsonl` in the app-support directory.
     */
    val agentSessionLog: AgentSessionLog = AgentSessionLogImpl(
        persistence = persistenceStore,
    )

    /**
     * Extracts native session UUIDs from Claude stream-json output lines.
     * Called from [terminalService] on each output chunk via [onOutputChunk].
     */
    val captureAgentSessionIdUseCase: CaptureAgentSessionIdUseCase =
        CaptureAgentSessionIdUseCase(agentSessionLog)

    /**
     * Queries recent sessions for a project (UI-layer convenience).
     */
    val listRecentAgentSessionsUseCase: ListRecentAgentSessionsUseCase =
        ListRecentAgentSessionsUseCase(agentSessionLog)

    /**
     * Data source for recent agent session records, bridging the KMP
     * [AgentSessionLog] to the desktop UI's [AgentSessionDataSource] contract.
     */
    val agentSessionDataSource: AgentSessionDataSource = KmpAgentSessionDataSource(
        agentSessionLog = agentSessionLog,
    )

    // ── Terminal service (wired with session-capture + scrollback callbacks) ──

    /**
     * Set of session UUIDs for which the native agent session ID has already been
     * captured.  Once captured, we skip further scanning for that session to avoid
     * redundant regex work on every PTY output chunk.
     *
     * Access is confined to the IO dispatcher via [scope.launch(Dispatchers.IO)] so
     * a plain [java.util.concurrent.ConcurrentHashMap] keyset is sufficient.
     */
    private val capturedNativeIdSessions: MutableSet<Uuid> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    /** Real pty4j-backed terminal service.  Replaces [StubTerminalSessionManaging]. */
    val terminalService: DesktopTerminalService = DesktopTerminalService(
        parentScope = scope,
        generalPreferences = generalPreferences,
        onOutputChunk = { sessionId, chunk ->
            // Fast path: skip sessions whose native ID has already been captured.
            if (sessionId in capturedNativeIdSessions) return@DesktopTerminalService

            // Only agent sessions emit the stream-json init line with "session_id".
            // Avoid launching a coroutine for every chunk of regular terminal output.
            if (terminalService.session(sessionId)?.isAgentSession != true) return@DesktopTerminalService

            // The "session_id" field only appears in the first few JSON lines.
            // Only scan chunks that contain the marker string — cheap string search
            // before launching a coroutine and running the full regex.
            if (!chunk.contains("session_id")) return@DesktopTerminalService

            // Non-blocking fire-and-forget — must not block the PTY hot path.
            scope.launch(Dispatchers.IO) {
                captureAgentSessionIdUseCase(CaptureAgentSessionIdParams(sessionId, chunk))
                // Mark as captured so subsequent chunks skip this session entirely.
                capturedNativeIdSessions.add(sessionId)
            }
        },
        onFirstAgentInput = { sessionId, input ->
            // Non-blocking fire-and-forget — must not block the sendInput call path.
            scope.launch(Dispatchers.IO) {
                try {
                    agentSessionLog.updateFirstPrompt(sessionId.toString(), input.take(200))
                } catch (e: Exception) {
                    System.err.println("DesktopServiceContainer: failed to persist firstPrompt: ${e.message}")
                }
            }
        },
    )

    val restoreSessionUseCase: RestoreSessionUseCase = RestoreSessionUseCase(
        projectManager = projectStore,
        terminalManager = terminalService,
        sessionPersistence = sessionStore,
    )

    val codeSpeakService: CodeSpeakServicing = CodeSpeakServiceImpl(
        persistence = persistenceStore,
    )

    val freeTabStore: FreeTabManaging = FreeTabStoreImpl()

    val navigationCoordinator: AppNavigationCoordinator = AppNavigationCoordinator()

    // ── Scrollback persistence (Phase 2) ──────────────────────────────────────

    /**
     * Debounces scrollback dirty signals and persists scrollback content.
     * Started lazily — available for the terminal service after construction.
     */
    val scrollbackPersistCoordinator: ScrollbackPersistCoordinator = ScrollbackPersistCoordinator(
        sessionPersistence = sessionStore,
        terminalService = terminalService,
        scope = scope,
    )

    // ── Agent launcher ────────────────────────────────────────────────────────

    /**
     * Shared AI-agent start/stop domain service.
     *
     * Single instance shared between [toolbarViewModel] and [remoteControlServer] so that
     * launching an agent from mobile and from the desktop toolbar uses the same logic
     * and the same running-state tracking. Created eagerly (not lazy) because
     * [remoteControlServer] is also eager.
     */
    val assistantLauncher: AssistantLaunching = AssistantLauncherImpl(
        projectManaging = projectStore,
        terminalSessionManaging = terminalService,
        agentRegistry = agentRegistry,
        agentAvailabilityChecking = agentAvailabilityChecking,
        apiKeyResolving = apiKeyResolving,
        blockingDispatcher = Dispatchers.IO,
        onResolveEnvVar = { name -> System.getenv(name) },
        // Explicit scope injection (B4) — lifetime tied to this container's scope so
        // AssistantLauncherImpl's internal StateFlow sharing is cancelled in dispose().
        scope = CoroutineScope(scope.coroutineContext + SupervisorJob()),
        agentSessionLog = agentSessionLog,
    )

    // ── Session autosave (Phase 2) ────────────────────────────────────────────

    /**
     * Drives periodic and event-triggered autosave of the session snapshot.
     * Started by [AppLifecycleCoordinator.onStartup], stopped by [dispose].
     */
    val sessionAutosaveCoordinator: SessionAutosaveCoordinator = SessionAutosaveCoordinator(
        projectManaging = projectStore,
        terminalManager = terminalService,
        sessionPersistence = sessionStore,
        freeTabManaging = freeTabStore,
        assistantLauncher = assistantLauncher,
        scope = scope,
    )

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
        projectManaging = projectStore,
        scrollbackAccessing = terminalService,
        assistantLauncher = assistantLauncher,
        authService = RemoteAuthServiceImpl(JavaSecureRandom),
        parentScope = scope,
    )

    // ── UseCases (B5) ─────────────────────────────────────────────────────────

    /** Streams codespeak build output — used by SpecBuildPanelViewModel. */
    val runSpecBuildUseCase: RunSpecBuildUseCase = RunSpecBuildUseCase(processRunner)

    /** Terminal session lifecycle use cases — used by TerminalAreaViewModel. */
    val createTerminalSessionUseCase: CreateTerminalSessionUseCase =
        CreateTerminalSessionUseCase(terminalService)
    val listTerminalSessionsUseCase: ListTerminalSessionsUseCase =
        ListTerminalSessionsUseCase(terminalService)
    val sendTerminalInputUseCase: SendTerminalInputUseCase =
        SendTerminalInputUseCase(terminalService)
    val closeTerminalSessionUseCase: CloseTerminalSessionUseCase =
        CloseTerminalSessionUseCase(terminalService)

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
            assistantLauncher = assistantLauncher,
            pathResolving = JvmPathResolving,
            envVarResolving = JvmEnvVarResolving,
        )
    }

    val gitSidebarViewModel: GitSidebarViewModel by lazy {
        GitSidebarViewModel(
            gitService = gitService,
            aiCommitService = aiCommitService,
            parentScope = scope,
            urlOpening = JvmUrlOpening,
            urlConverter = GitURLConverter,
        )
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Loads persistent state that requires async I/O.
     * Must be called once after construction, before UI is shown.
     * A 10-second timeout guards against indefinite hang on NFS/network mounts.
     * On timeout we log and continue with an empty store — better than freezing
     * the UI forever.
     */
    suspend fun bootstrap() {
        try {
            kotlinx.coroutines.withTimeout(10_000) { projectStore.load() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            System.err.println("DesktopServiceContainer: projectStore.load() timed out after 10 s — continuing with empty store")
        }
    }

    /**
     * Shuts down all services and cancels the container scope.
     * Call from [onCloseRequest] via `scope.launch { container.shutdown(); exitApplication() }`.
     */
    suspend fun shutdown() {
        // Stop autosave background jobs before cancelling scope.
        sessionAutosaveCoordinator.stop()

        // Final flush: persist unsaved in-memory edits to disk before exit.
        kotlinx.coroutines.withContext(Dispatchers.IO) { projectStore.save() }

        gitStatusPoller.stopPolling()

        // Cancel VM scopes — fire-and-forget (no join needed, process exit handles cleanup).
        fileTreeViewModel.dispose()
        toolbarViewModel.dispose()
        gitSidebarViewModel.dispose()

        fileSystemWatchingService.unwatchAll()

        // Stop Remote Control server before terminal service so bridges can detach cleanly.
        // Hard 3-second timeout prevents a hung ngrok process from blocking exit.
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            withTimeoutOrNull(3_000) { remoteControlServer.stopAsync() }
        }

        remoteControlServer.dispose()
        terminalService.dispose()   // kills all live PTY processes before scope cancel
        httpClient.close()
        scope.cancel()
    }

    /**
     * Lightweight synchronous teardown for tests.
     * Cancels the container scope without awaiting graceful service shutdown.
     */
    fun dispose() {
        scope.cancel()
    }
}

