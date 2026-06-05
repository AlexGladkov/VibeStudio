@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.routing.get
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import studio.vibe.shared.contract.RemoteAuthorizing
import studio.vibe.shared.contract.TerminalRemoteHost
import studio.vibe.shared.model.RemoteAuthResult
import studio.vibe.shared.preferences.RemoteControlPreferencesReading
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Configures the Ktor [Application] with all plugins and routes for Remote Control.
 *
 * Extracted from [RemoteControlServer.configureApp] to separate route wiring from
 * server lifecycle management.
 *
 * Call [Application.configure] from within `embeddedServer { ... }` to wire all
 * routes using the provided handler instances.
 */
internal class RemoteRouting(
    private val authHandler: RemoteAuthHandler,
    private val projectApiHandler: ProjectApiHandler,
    private val assistantApiHandler: AssistantApiHandler,
    private val terminalService: TerminalRemoteHost,
    private val authService: RemoteAuthorizing,
    private val activeBridges: ConcurrentHashMap<UUID, RemoteSessionBridge>,
    private val preferences: RemoteControlPreferencesReading,
    private val serverScope: CoroutineScope,
    private val json: Json,
    private val onBridgeRegistered: (RemoteSessionBridge) -> Unit,
    private val onBridgeUnregistered: (RemoteSessionBridge) -> Unit,
    private val buildHealthResponse: () -> HealthResponse,
) {
    private val log = Logger.getLogger("RemoteRouting")

    /** Installs plugins and registers all routes on [app]. */
    fun configure(app: Application) = with(app) {
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
            get("/health") { call.respond(buildHealthResponse()) }

            route("/api/v1") {
                get("/health") { call.respond(buildHealthResponse()) }

                authHandler.registerAuth(this)
                projectApiHandler.registerProjects(this)
                assistantApiHandler.registerAssistant(this)
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
                                    onBridgeRegistered(bridge!!)
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
                                val pong = WSPongMessage(type = "pong", ts = ts, serverTs = System.currentTimeMillis())
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
                    bridge?.let { onBridgeUnregistered(it) }
                    bridgeScope.cancel()
                }
            }
        }
    }
}
