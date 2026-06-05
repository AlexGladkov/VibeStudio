@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalRemoteHost
import studio.vibe.shared.contract.RemoteAuthorizing
import studio.vibe.shared.contract.SecurityEvent
import studio.vibe.shared.contract.TerminalScrollbackAccessing
import studio.vibe.shared.usecase.AssistantLauncher
import studio.vibe.shared.model.RemoteDevice as SharedRemoteDevice
import studio.vibe.shared.preferences.RemoteControlPreferencesReading
import studio.vibe.shared.security.JavaSecureRandom
import studio.vibe.shared.service.remote.RemoteAuthServiceImpl
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlin.uuid.Uuid

/**
 * Embedded HTTP + WebSocket server for remote terminal control.
 *
 * Responsibilities (post-refactoring):
 * - Server lifecycle (start/stop/dispose) via two Ktor engines.
 * - Composition: instantiates and wires [RemoteAuthHandler], [ProjectApiHandler],
 *   [AssistantApiHandler], [SessionResponseMapper], [RemoteRouting].
 * - Bridge registry ([activeBridges]), device count observation, ngrok control.
 * - Security lockout reaction (stop the server).
 *
 * Route setup lives in [RemoteRouting].
 * Auth flow lives in [RemoteAuthHandler].
 * Project API lives in [ProjectApiHandler].
 * Assistant API lives in [AssistantApiHandler].
 * Session DTO mapping lives in [SessionResponseMapper].
 */
class RemoteControlServer(
    private val preferences: RemoteControlPreferencesReading,
    private val terminalService: TerminalRemoteHost,
    private val projectManaging: ProjectManaging? = null,
    private val scrollbackAccessing: TerminalScrollbackAccessing? = null,
    val authService: RemoteAuthorizing = RemoteAuthServiceImpl(JavaSecureRandom),
    private val assistantLauncher: AssistantLauncher? = null,
    parentScope: CoroutineScope? = null,
) {
    companion object {
        private val log = Logger.getLogger("RemoteControlServer")
        private const val APP_VERSION = "0.2.0"
    }

    // ── Public services ────────────────────────────────────────────────────────

    val ngrok: NgrokTunnelService

    // ── Observable state ──────────────────────────────────────────────────────

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isTransitioning = MutableStateFlow(false)
    val isTransitioning: StateFlow<Boolean> = _isTransitioning.asStateFlow()

    private val _connectedDeviceCount = MutableStateFlow(0)
    val connectedDeviceCount: StateFlow<Int> = _connectedDeviceCount.asStateFlow()

    // ── Server scope ───────────────────────────────────────────────────────────
    // Use a child of the container scope so the app's SupervisorJob hierarchy is respected.
    private val serverScope = CoroutineScope(
        SupervisorJob(parentScope?.coroutineContext?.get(kotlinx.coroutines.Job)) + Dispatchers.IO
    )

    // ── Lifecycle mutex (prevents concurrent start/stop) ───────────────────────

    private val lifecycleMutex = Mutex()

    // ── Ktor engine references (null when stopped) ─────────────────────────────

    @Volatile private var mainEngine: EmbeddedServer<*, *>? = null
    @Volatile private var loopbackEngine: EmbeddedServer<*, *>? = null
    @Volatile private var startedAt: Long? = null

    // ── Active bridges ─────────────────────────────────────────────────────────

    private val activeBridges = ConcurrentHashMap<UUID, RemoteSessionBridge>()

    // ── JSON (reused by routing and handlers) ─────────────────────────────────

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Extracted collaborators ────────────────────────────────────────────────

    private val authHandler = RemoteAuthHandler(authService, json)

    private val projectApiHandler = ProjectApiHandler(
        projectManaging = projectManaging,
        terminalService = terminalService,
        scrollbackAccessing = scrollbackAccessing,
        activeBridges = activeBridges,
        authHandler = authHandler,
        json = json,
    )

    private val assistantApiHandler = AssistantApiHandler(
        assistantLauncher = assistantLauncher,
        authHandler = authHandler,
        json = json,
    )

    private val routing = RemoteRouting(
        authHandler = authHandler,
        projectApiHandler = projectApiHandler,
        assistantApiHandler = assistantApiHandler,
        terminalService = terminalService,
        authService = authService,
        activeBridges = activeBridges,
        preferences = preferences,
        serverScope = serverScope,
        json = json,
        onBridgeRegistered = ::registerBridge,
        onBridgeUnregistered = ::unregisterBridge,
        buildHealthResponse = ::buildHealthResponse,
    )

    // ── ngrok host ref ─────────────────────────────────────────────────────────

    @Volatile private var ngrokObserveJob: Job? = null

    // ── Init ───────────────────────────────────────────────────────────────────

    init {
        ngrok = NgrokTunnelService(serverScope)
        authService.securityEvents
            .filterIsInstance<SecurityEvent.GlobalLockout>()
            .onEach { handleSecurityLockout() }
            .launchIn(serverScope)
        authService.devicesCount
            .onEach { count -> _connectedDeviceCount.value = count }
            .launchIn(serverScope)
    }

    // ── Properties ─────────────────────────────────────────────────────────────

    val port: Int get() = preferences.remoteControlPort
    val currentPin: String get() = authService.currentPin.value
    val connectedDevices get() = authService.connectedDevices.value
    val isNgrokRunning get() = ngrok.isRunning.value
    val ngrokTunnelURL: String? get() = ngrok.tunnelURL.value
    val ngrokError: String? get() = ngrok.error.value
    val isLocked: StateFlow<Boolean> get() = authService.isLocked

    val uptimeSeconds: Long
        get() = startedAt?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Start the HTTP/WS server.
     *
     * No-op if already running or transitioning. Creates two Ktor engines:
     * - Main engine on [port] (binds to 0.0.0.0 or 127.0.0.1 per [preferences.bindToLocalhost]).
     * - Loopback engine on [port]+1 (127.0.0.1 only, for local tools like ngrok).
     * Optionally starts the ngrok tunnel for external access.
     */
    fun start() {
        serverScope.launch { startAsync() }
    }

    suspend fun startAsync() {
        // Guard: claim exclusive ownership of the transitioning window.
        val claimed = lifecycleMutex.withLock {
            if (_isRunning.value || _isTransitioning.value) {
                log.warning("RemoteControlServer.start() called while already running or transitioning")
                false
            } else {
                _isTransitioning.value = true
                true
            }
        }
        if (!claimed) return

        try {
            val bindHost = if (preferences.bindToLocalhost) "127.0.0.1" else "0.0.0.0"
            val bindPort = preferences.remoteControlPort

            val main = embeddedServer(Netty, port = bindPort, host = bindHost) {
                routing.configure(this)
            }

            val loopback = embeddedServer(Netty, port = bindPort + 1, host = "127.0.0.1") {
                routing.configure(this)
            }

            main.start(wait = false)
            loopback.start(wait = false)

            // Atomically publish both engines and flip running state so stopAsync
            // cannot observe a partial state (engines assigned but !_isRunning).
            lifecycleMutex.withLock {
                mainEngine = main
                loopbackEngine = loopback
                startedAt = System.currentTimeMillis()
                _isRunning.value = true
                _isTransitioning.value = false
            }

            startNgrokObservation()

            if (preferences.ngrokEnabled) {
                val authtoken = withContext(Dispatchers.IO) {
                    preferences.loadNgrokAuthtoken()
                }
                if (authtoken.isNotBlank()) {
                    ngrok.start(httpPort = bindPort + 1, authtoken = authtoken)
                }
            }

            log.info("RemoteControlServer started on $bindHost:$bindPort (loopback: 127.0.0.1:${bindPort + 1})")

        } catch (e: Exception) {
            log.severe("RemoteControlServer failed to start: ${e.message}")
            lifecycleMutex.withLock { _isTransitioning.value = false }
        }
    }

    /** Gracefully stop the server, disconnect all devices, and cancel ngrok. */
    fun stop() {
        serverScope.launch { stopAsync() }
    }

    /** Stop the server and await full shutdown (for clean app termination). */
    suspend fun stopAsync() {
        val (main, loopback) = lifecycleMutex.withLock {
            if (!_isRunning.value || _isTransitioning.value) return
            _isTransitioning.value = true
            Pair(mainEngine, loopbackEngine)
        }

        try {
            activeBridges.values.forEach { it.detach() }
            activeBridges.clear()
            _connectedDeviceCount.value = 0
            ngrok.stop()
            ngrokObserveJob?.cancel()
            ngrokObserveJob = null
            authService.revokeAllDevices()

            lifecycleMutex.withLock {
                _isRunning.value = false
                startedAt = null
                mainEngine = null
                loopbackEngine = null
            }

            withContext(Dispatchers.IO) {
                runCatching { main?.stop(1_000L, 2_000L) }
                runCatching { loopback?.stop(1_000L, 2_000L) }
            }

            log.info("RemoteControlServer stopped (async)")
        } finally {
            _isTransitioning.value = false
        }
    }

    /** Release coroutine scope — call on app quit after [stopAsync]. */
    fun dispose() {
        serverScope.cancel()
    }

    // ── PIN management ─────────────────────────────────────────────────────────

    fun regeneratePin() = authService.regeneratePin()

    // ── ngrok control ──────────────────────────────────────────────────────────

    fun startNgrok() {
        if (!_isRunning.value) return
        serverScope.launch {
            val authtoken = withContext(Dispatchers.IO) { preferences.loadNgrokAuthtoken() }
            ngrok.start(httpPort = preferences.remoteControlPort + 1, authtoken = authtoken)
        }
    }

    fun stopNgrok() = ngrok.stop()

    // ── Device management ──────────────────────────────────────────────────────

    fun disconnect(deviceId: UUID) {
        activeBridges[deviceId]?.detach()
        activeBridges.remove(deviceId)
        serverScope.launch {
            authService.revokeDevice(Uuid.parse(deviceId.toString()))
        }
        _connectedDeviceCount.value = activeBridges.size
    }

    /** Convenience overload for callers (UI) holding the shared [Uuid] type. */
    fun disconnect(deviceId: Uuid) {
        disconnect(UUID.fromString(deviceId.toString()))
    }

    // ── Broadcast ──────────────────────────────────────────────────────────────

    fun broadcastTextMessage(message: String) {
        activeBridges.values.forEach { it.sendTextMessage(message) }
    }

    // ── Bridge registration ────────────────────────────────────────────────────

    internal fun registerBridge(bridge: RemoteSessionBridge) {
        activeBridges[bridge.deviceId] = bridge
        _connectedDeviceCount.value = activeBridges.size
        log.info("Bridge registered: device=${bridge.deviceId} session=${bridge.sessionId}")
    }

    internal fun unregisterBridge(bridge: RemoteSessionBridge) {
        bridge.detach()
        activeBridges.remove(bridge.deviceId)
        _connectedDeviceCount.value = activeBridges.size
        log.info("Bridge unregistered: device=${bridge.deviceId} session=${bridge.sessionId}")
    }

    // ── Security lockout ───────────────────────────────────────────────────────

    private fun handleSecurityLockout() {
        log.severe("Security lockout triggered — stopping Remote Control server")
        stop()
    }

    // ── ngrok host observation ─────────────────────────────────────────────────

    private fun startNgrokObservation() {
        ngrokObserveJob = ngrok.tunnelURL
            .onEach { /* no-op: ngrok URL observed for side-effects in settings pane */ }
            .launchIn(serverScope)
    }

    // ── Health response ────────────────────────────────────────────────────────

    private fun buildHealthResponse() = HealthResponse(
        status = "healthy",
        version = APP_VERSION,
        uptimeSeconds = uptimeSeconds,
        connectedDevices = activeBridges.size,
        maxDevices = authService.maxDevices,
        tls = "none",
    )
}
