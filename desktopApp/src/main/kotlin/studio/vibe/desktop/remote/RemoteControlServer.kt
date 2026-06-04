@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.contract.TerminalRemoteHost
import studio.vibe.shared.contract.RemoteAuthorizing
import studio.vibe.shared.contract.TerminalScrollbackAccessing
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.RemoteAuthError
import studio.vibe.shared.model.RemoteAuthResult
import studio.vibe.shared.model.RemoteDevice as SharedRemoteDevice
import studio.vibe.shared.model.TerminalSessionState
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
 * Matches Swift `RemoteControlServer` behavior:
 * - HTTP server on [port] (plain HTTP; TLS via reverse-proxy / ngrok is supported).
 * - Additional plain-HTTP listener on [port]+1 bound to 127.0.0.1 for local tools.
 * - WebSocket upgrade on `/ws/terminal/{sessionId}`.
 * - REST API: `/health`, `/api/v1/auth/token`, `/api/v1/auth/validate`, `/api/v1/sessions`.
 * - ngrok tunnel management via [NgrokTunnelService].
 * - Security lockout: 10 failed PIN attempts → server auto-stops.
 * - Stoppable and restartable.
 *
 * **TLS note:** Self-signed TLS via keytool is available via [TlsCertificateManager].
 * Full HTTPS requires `ktor-server-netty` with SSL connector support. For the
 * initial implementation the server runs plain HTTP and relies on the ngrok tunnel
 * for external HTTPS access, matching the Swift version's fallback behavior.
 *
 * Thread safety: [_isRunning], [_isTransitioning], [activeBridges] are safe for
 * concurrent access. [embeddedServer] is [Volatile].
 */
class RemoteControlServer(
    private val preferences: RemoteControlPreferencesReading,
    private val terminalService: TerminalRemoteHost,
    private val projectManaging: ProjectManaging? = null,
    private val scrollbackAccessing: TerminalScrollbackAccessing? = null,
    val authService: RemoteAuthorizing = RemoteAuthServiceImpl(JavaSecureRandom),
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

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Lifecycle mutex (prevents concurrent start/stop) ───────────────────────

    private val lifecycleMutex = Mutex()

    // ── Ktor engine references (null when stopped) ─────────────────────────────

    @Volatile private var mainEngine: EmbeddedServer<*, *>? = null
    @Volatile private var loopbackEngine: EmbeddedServer<*, *>? = null
    @Volatile private var startedAt: Long? = null

    // ── Active bridges ─────────────────────────────────────────────────────────

    private val activeBridges = ConcurrentHashMap<UUID, RemoteSessionBridge>()

    // ── JSON (non-strict, reused in routing) ──────────────────────────────────

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── ngrok host ref ─────────────────────────────────────────────────────────

    @Volatile private var ngrokObserveJob: Job? = null

    // ── Init ───────────────────────────────────────────────────────────────────

    init {
        ngrok = NgrokTunnelService(serverScope)
        authService.onSecurityLockout = { handleSecurityLockout() }
        authService.onDevicesChanged = { count -> _connectedDeviceCount.value = count }
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
     * - Main engine on [port] (binds to 0.0.0.0 or 127.0.0.1 per [RemoteControlPreferences.bindToLocalhost]).
     * - Loopback engine on [port]+1 (127.0.0.1 only, for local tools like ngrok).
     * Optionally starts the ngrok tunnel for external access.
     */
    fun start() {
        serverScope.launch { startAsync() }
    }

    suspend fun startAsync() {
        lifecycleMutex.withLock {
            if (_isRunning.value || _isTransitioning.value) {
                log.warning("RemoteControlServer.start() called while already running or transitioning")
                return
            }
            _isTransitioning.value = true
        }

        try {
            val bindHost = if (preferences.bindToLocalhost) "127.0.0.1" else "0.0.0.0"
            val bindPort = preferences.remoteControlPort

            val main = embeddedServer(Netty, port = bindPort, host = bindHost) {
                configureApp()
            }

            val loopback = embeddedServer(Netty, port = bindPort + 1, host = "127.0.0.1") {
                configureApp()
            }

            main.start(wait = false)
            loopback.start(wait = false)

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

    /**
     * Gracefully stop the server, disconnect all devices, and cancel ngrok.
     */
    fun stop() {
        serverScope.launch { stopAsync() }
    }

    /**
     * Stop the server and await full shutdown (for clean app termination).
     */
    suspend fun stopAsync() {
        val (main, loopback) = lifecycleMutex.withLock {
            if (!_isRunning.value || _isTransitioning.value) return
            _isTransitioning.value = true
            Pair(mainEngine, loopbackEngine)
        }

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

        _isTransitioning.value = false
        log.info("RemoteControlServer stopped (async)")
    }

    private fun UUID.toKotlinUuid(): Uuid = Uuid.parse(this.toString())

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
            authService.revokeDevice(deviceId.toKotlinUuid())
        }
        _connectedDeviceCount.value = activeBridges.size
    }

    /** Convenience overload for callers (UI) holding the shared [Uuid] type. */
    fun disconnect(deviceId: Uuid) {
        disconnect(UUID.fromString(deviceId.toString()))
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

    // ── Broadcast ──────────────────────────────────────────────────────────────

    fun broadcastTextMessage(message: String) {
        activeBridges.values.forEach { it.sendTextMessage(message) }
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

    // ── Ktor application module ────────────────────────────────────────────────

    private fun Application.configureApp() {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }

        install(WebSockets) {
            pingPeriodMillis = 30_000L
            timeoutMillis = 60_000L
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader("X-Auth-Token")
            anyHost()
        }

        routing {

            // ── Health (no auth) ───────────────────────────────────────────────
            get("/health") {
                call.respond(buildHealthResponse())
            }

            route("/api/v1") {

                get("/health") {
                    call.respond(buildHealthResponse())
                }

                // ── Auth ──────────────────────────────────────────────────────
                route("/auth") {

                    post("/token") {
                        val clientIP = call.request.local.remoteHost
                        val userAgent = call.request.headers[HttpHeaders.UserAgent] ?: ""

                        val bodyText = runCatching { call.receiveText() }.getOrElse {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse(ErrorDetail("bad_request", "Could not read body")),
                            )
                            return@post
                        }

                        val body = runCatching {
                            json.decodeFromString<AuthTokenRequest>(bodyText)
                        }.getOrElse {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse(ErrorDetail("bad_request", "Invalid JSON body")),
                            )
                            return@post
                        }

                        when (val result = authService.validatePin(body.pin, clientIP, userAgent)) {
                            is RemoteAuthResult.Success -> {
                                call.respond(
                                    AuthTokenResponseDTO(
                                        token = result.value.token,
                                        expiresAt = result.value.expiresAt.toEpochMilliseconds(),
                                        deviceId = result.value.device.id.toString(),
                                    )
                                )
                            }

                            is RemoteAuthResult.Failure -> {
                                val (status, code, message) = when (val err = result.error) {
                                    RemoteAuthError.InvalidPin ->
                                        Triple(HttpStatusCode.Unauthorized, "invalid_pin", "Invalid PIN")
                                    is RemoteAuthError.RateLimited ->
                                        Triple(HttpStatusCode.TooManyRequests, "rate_limited",
                                            "Too many attempts. Retry after ${err.retryAfterSeconds}s")
                                    RemoteAuthError.GlobalLockout ->
                                        Triple(HttpStatusCode.Forbidden, "global_lockout", "Server is locked")
                                    RemoteAuthError.MaxDevicesReached ->
                                        Triple(HttpStatusCode.Forbidden, "max_devices",
                                            "Maximum device limit reached")
                                    else ->
                                        Triple(HttpStatusCode.Unauthorized, "auth_error", "Authentication failed")
                                }
                                call.respond(status, ErrorResponse(ErrorDetail(code, message)))
                            }
                        }
                    }

                    get("/validate") {
                        val device = call.requireAuth() ?: return@get
                        call.respond(
                            AuthValidateResponse(
                                valid = true,
                                deviceId = device.id.toString(),
                                expiresAt = System.currentTimeMillis() + 4 * 3600 * 1000L,
                            )
                        )
                    }
                }

                // ── Sessions ──────────────────────────────────────────────────
                get("/sessions") {
                    call.requireAuth() ?: return@get
                    val sessions = terminalService.sessionsByProject.value.values.flatten()
                    call.respond(
                        sessions.map { s ->
                            SessionResponse(
                                id = s.id.toString(),
                                title = s.title,
                                state = when (s.state) {
                                    is TerminalSessionState.Running -> "running"
                                    is TerminalSessionState.Exited -> "exited"
                                    else -> "unknown"
                                },
                                isAgent = s.isAgentSession,
                                hasRemoteAttachment = activeBridges.values.any {
                                    it.sessionId.toString() == s.id.toString()
                                },
                                attachedDeviceId = activeBridges.values.firstOrNull {
                                    it.sessionId.toString() == s.id.toString()
                                }?.deviceId?.toString(),
                            )
                        }
                    )
                }

                // ── Status ────────────────────────────────────────────────────
                get("/status") {
                    call.requireAuth() ?: return@get
                    call.respond(StatusResponse(theme = "dark"))
                }

                // ── Projects ──────────────────────────────────────────────────
                get("/projects") {
                    call.requireAuth() ?: return@get
                    val pm = projectManaging
                    if (pm == null) {
                        call.respond(ProjectListResponse(projects = emptyList(), activeProjectId = null))
                        return@get
                    }
                    val sessionMap = terminalService.sessionsByProject.value
                    val activeId = pm.activeProjectId.value?.toString()
                    val projects = pm.projects.value.map { project ->
                        val sessions = sessionMap[project.id] ?: emptyList()
                        ProjectResponse(
                            id = project.id.toString(),
                            name = project.name,
                            path = project.path.path,
                            sessions = sessions.map { s ->
                                SessionResponse(
                                    id = s.id.toString(),
                                    title = s.title,
                                    state = when (s.state) {
                                        is TerminalSessionState.Running -> "running"
                                        is TerminalSessionState.Exited -> "exited"
                                        else -> "unknown"
                                    },
                                    isAgent = s.isAgentSession,
                                    hasRemoteAttachment = activeBridges.values.any {
                                        it.sessionId.toString() == s.id.toString()
                                    },
                                    attachedDeviceId = activeBridges.values.firstOrNull {
                                        it.sessionId.toString() == s.id.toString()
                                    }?.deviceId?.toString(),
                                )
                            },
                        )
                    }
                    call.respond(ProjectListResponse(projects = projects, activeProjectId = activeId))
                }

                get("/projects/recent") {
                    call.requireAuth() ?: return@get
                    val pm = projectManaging
                    if (pm == null) {
                        call.respond(ProjectListResponse(projects = emptyList(), activeProjectId = null))
                        return@get
                    }
                    val sessionMap = terminalService.sessionsByProject.value
                    val activeId = pm.activeProjectId.value?.toString()
                    val projects = pm.recentProjects.value.map { project ->
                        val sessions = sessionMap[project.id] ?: emptyList()
                        ProjectResponse(
                            id = project.id.toString(),
                            name = project.name,
                            path = project.path.path,
                            sessions = sessions.map { s ->
                                SessionResponse(
                                    id = s.id.toString(),
                                    title = s.title,
                                    state = when (s.state) {
                                        is TerminalSessionState.Running -> "running"
                                        is TerminalSessionState.Exited -> "exited"
                                        else -> "unknown"
                                    },
                                    isAgent = s.isAgentSession,
                                    hasRemoteAttachment = activeBridges.values.any {
                                        it.sessionId.toString() == s.id.toString()
                                    },
                                    attachedDeviceId = activeBridges.values.firstOrNull {
                                        it.sessionId.toString() == s.id.toString()
                                    }?.deviceId?.toString(),
                                )
                            },
                        )
                    }
                    call.respond(ProjectListResponse(projects = projects, activeProjectId = activeId))
                }

                post("/projects/open") {
                    call.requireAuth() ?: return@post
                    val pm = projectManaging
                    if (pm == null) {
                        call.respond(
                            HttpStatusCode.NotImplemented,
                            ErrorResponse(ErrorDetail("not_implemented", "ProjectManaging not wired")),
                        )
                        return@post
                    }
                    val bodyText = runCatching { call.receiveText() }.getOrElse {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Could not read body")))
                        return@post
                    }
                    val req = runCatching { json.decodeFromString<OpenProjectRequest>(bodyText) }.getOrElse {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Invalid JSON body")))
                        return@post
                    }
                    val project = runCatching {
                        pm.addProject(FilePath(req.path))
                    }.getOrElse { e ->
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse(ErrorDetail("open_failed", e.message ?: "Unknown error")))
                        return@post
                    }
                    val sessionMap = terminalService.sessionsByProject.value
                    call.respond(
                        ProjectResponse(
                            id = project.id.toString(),
                            name = project.name,
                            path = project.path.path,
                            sessions = (sessionMap[project.id] ?: emptyList()).map { s ->
                                SessionResponse(
                                    id = s.id.toString(),
                                    title = s.title,
                                    state = "running",
                                    isAgent = s.isAgentSession,
                                    hasRemoteAttachment = false,
                                    attachedDeviceId = null,
                                )
                            },
                        )
                    )
                }

                route("/projects/{projectId}") {
                    post("/activate") {
                        call.requireAuth() ?: return@post
                        val pm = projectManaging
                        if (pm == null) {
                            call.respond(
                                HttpStatusCode.NotImplemented,
                                ErrorResponse(ErrorDetail("not_implemented", "ProjectManaging not wired")),
                            )
                            return@post
                        }
                        val projectIdStr = call.parameters["projectId"] ?: run {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Missing projectId")))
                            return@post
                        }
                        val projectId = runCatching { Uuid.parse(projectIdStr) }.getOrElse {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Invalid projectId")))
                            return@post
                        }
                        pm.setActiveProjectId(projectId)
                        call.respond(OKResponse(ok = true))
                    }

                    get("/sessions/{sessionId}/scrollback") {
                        call.requireAuth() ?: return@get
                        val sessionIdStr = call.parameters["sessionId"] ?: run {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Missing sessionId")))
                            return@get
                        }
                        val sessionId = runCatching { Uuid.parse(sessionIdStr) }.getOrElse {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Invalid sessionId")))
                            return@get
                        }
                        val content = scrollbackAccessing?.scrollbackContent(sessionId) ?: ""
                        call.respond(ScrollbackResponse(content = content, totalLines = if (content.isEmpty()) 0 else content.lines().size))
                    }
                }

                // ── Assistant ─────────────────────────────────────────────────
                post("/assistant/start") {
                    call.requireAuth() ?: return@post
                    call.respond(
                        HttpStatusCode.NotImplemented,
                        ErrorResponse(ErrorDetail("not_implemented", "Start AI agent from mobile not yet supported")),
                    )
                }

                post("/assistant/stop") {
                    call.requireAuth() ?: return@post
                    call.respond(
                        HttpStatusCode.NotImplemented,
                        ErrorResponse(ErrorDetail("not_implemented", "Stop AI agent from mobile not yet supported")),
                    )
                }
            }

            // ── WebSocket terminal ─────────────────────────────────────────────
            webSocket("/ws/terminal/{sessionId}") {
                val sessionIdStr = call.parameters["sessionId"] ?: run {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing sessionId"))
                    return@webSocket
                }

                val sessionUuid = runCatching { UUID.fromString(sessionIdStr) }.getOrElse {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid sessionId"))
                    return@webSocket
                }

                val clientIP = call.request.local.remoteHost
                val bridgeScope = CoroutineScope(SupervisorJob(serverScope.coroutineContext[Job]) + Dispatchers.IO)

                var bridge: RemoteSessionBridge? = null
                var authTimeoutJob: Job? = null

                authTimeoutJob = bridgeScope.launch {
                    delay(10_000)
                    if (bridge == null) {
                        log.warning("WS auth timeout from $clientIP — closing unauthenticated connection")
                        runCatching { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Auth timeout")) }
                    }
                }

                // Hard cap on total session lifetime. RemoteSessionBridge already
                // cancels the wsSession after idleTimeoutMinutes of silence; this
                // watchdog is a belt-and-suspenders against half-open TCP states
                // where ping/pong + idle detection both fail.  Without it the
                // [bridgeScope] would survive for the lifetime of the parent
                // application scope when `incoming.receive()` is stuck.
                val maxSessionMs = (preferences.idleTimeoutMinutes.toLong() + 5L) * 60_000L
                val sessionWatchdog = bridgeScope.launch {
                    delay(maxSessionMs)
                    log.warning("WS session watchdog fired ($maxSessionMs ms) for $clientIP — force-closing")
                    runCatching { close(CloseReason(CloseReason.Codes.GOING_AWAY, "Session watchdog")) }
                }

                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) {
                            if (frame is Frame.Close) break
                            continue
                        }

                        val text = frame.readText()
                        if (text.length > 65_536) {
                            log.warning("WS oversized frame from $clientIP — rejected")
                            continue
                        }

                        val type = runCatching {
                            json.decodeFromString<WSTypeEnvelope>(text).type
                        }.getOrNull() ?: continue

                        if (bridge == null) {
                            // Expect auth as the first message.
                            if (type != "auth") {
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication required"))
                                break
                            }

                            val authToken = runCatching {
                                json.decodeFromString<WSAuthMessage>(text).token
                            }.getOrElse {
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid auth message"))
                                break
                            }

                            when (val result = authService.validateToken(authToken, clientIP)) {
                                is RemoteAuthResult.Success -> {
                                    authTimeoutJob?.cancel()
                                    val device = result.value
                                    bridge = RemoteSessionBridge(
                                        deviceId = UUID.fromString(device.id.toString()),
                                        sessionId = sessionUuid,
                                        wsSession = this,
                                        terminalService = terminalService,
                                        idleTimeoutMinutes = preferences.idleTimeoutMinutes,
                                        bridgeScope = bridgeScope,
                                    )
                                    registerBridge(bridge!!)
                                    bridge!!.startStreaming()
                                    send(Frame.Text("{\"type\":\"auth_ok\"}"))
                                    log.info("WS authenticated: device=${device.id} session=$sessionUuid")
                                }

                                is RemoteAuthResult.Failure -> {
                                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication failed"))
                                    break
                                }
                            }
                            continue
                        }

                        // Authenticated — dispatch message.
                        val currentBridge = bridge ?: continue
                        when (type) {
                            "input" -> {
                                val data = runCatching {
                                    json.decodeFromString<WSInputMessage>(text).data
                                }.getOrNull() ?: continue
                                currentBridge.handleInput(data)
                            }
                            "resize" -> {
                                val msg = runCatching {
                                    json.decodeFromString<WSResizeMessage>(text)
                                }.getOrNull() ?: continue
                                currentBridge.handleResize(msg.cols, msg.rows)
                            }
                            "ping" -> {
                                val ts = runCatching {
                                    json.decodeFromString<WSPingMessage>(text).ts
                                }.getOrNull() ?: continue
                                val pong = WSPongMessage(
                                    type = "pong",
                                    ts = ts,
                                    serverTs = System.currentTimeMillis(),
                                )
                                send(Frame.Text(json.encodeToString<WSPongMessage>(pong)))
                            }
                            "detach" -> {
                                currentBridge.detach()
                                break
                            }
                            "auth" -> { /* already authenticated — ignore duplicate */ }
                            else -> log.fine("Unknown WS message type '$type' from $clientIP")
                        }
                    }
                } finally {
                    authTimeoutJob?.cancel()
                    sessionWatchdog.cancel()
                    bridge?.let { unregisterBridge(it) }
                    bridgeScope.cancel()
                }
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun buildHealthResponse() = HealthResponse(
        status = "healthy",
        version = APP_VERSION,
        uptimeSeconds = uptimeSeconds,
        connectedDevices = activeBridges.size,
        maxDevices = authService.maxDevices,
        tls = "none",
    )

    private suspend fun ApplicationCall.requireAuth(): SharedRemoteDevice? {
        val token = request.headers["X-Auth-Token"]
            ?: request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()

        if (token.isNullOrBlank()) {
            respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(ErrorDetail("missing_token", "Authorization required")),
            )
            return null
        }

        return when (val result = authService.validateToken(token, request.local.remoteHost)) {
            is RemoteAuthResult.Success -> result.value
            is RemoteAuthResult.Failure -> {
                val (status, code, message) = when (result.error) {
                    RemoteAuthError.TokenExpired ->
                        Triple(HttpStatusCode.Unauthorized, "token_expired", "Token has expired")
                    RemoteAuthError.IpMismatch ->
                        Triple(HttpStatusCode.Unauthorized, "ip_mismatch", "IP address mismatch")
                    else ->
                        Triple(HttpStatusCode.Unauthorized, "invalid_token", "Invalid or unknown token")
                }
                respond(status, ErrorResponse(ErrorDetail(code, message)))
                null
            }
        }
    }
}
