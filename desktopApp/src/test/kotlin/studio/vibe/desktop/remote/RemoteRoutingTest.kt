@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import studio.vibe.shared.feature.remote.domain.contract.RemoteAuthorizing
import studio.vibe.shared.feature.remote.domain.contract.SecurityEvent
import studio.vibe.shared.feature.terminal.domain.contract.TerminalRemoteHost
import studio.vibe.shared.feature.remote.domain.model.RemoteAuthError
import studio.vibe.shared.feature.remote.domain.model.RemoteAuthResult
import studio.vibe.shared.feature.remote.domain.model.RemoteDevice
import studio.vibe.shared.core.common.terminal.TerminalSession
import studio.vibe.shared.feature.settings.domain.model.RemoteControlPreferencesReading
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Integration tests for [RemoteRouting] using Ktor's [testApplication].
 *
 * No OS ports are bound. Verifies:
 * - Route existence (non-404 for registered paths)
 * - Authentication guard (401 for protected endpoints without a valid token)
 * - CORS preflight (OPTIONS returns expected headers)
 * - Auth routes accept POST
 * - Assistant and project routes exist
 */
class RemoteRoutingTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Fake preferences ───────────────────────────────────────────────────────

    private val fakePreferences = object : RemoteControlPreferencesReading {
        override val remoteControlEnabled = true
        override val remoteControlPort = 7842
        override val bindToLocalhost = true
        override val bonjourEnabled = false
        override val ngrokEnabled = false
        override val idleTimeoutMinutes = 30
        override suspend fun loadNgrokAuthtoken() = ""
    }

    // ── Fake auth service helpers ──────────────────────────────────────────────

    /**
     * Returns an auth service where all tokens are invalid (yields 401).
     */
    private fun unauthenticatedAuthService(): RemoteAuthorizing = mockk(relaxed = true) {
        every { securityEvents } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { devicesCount } returns kotlinx.coroutines.flow.MutableStateFlow(0)
        every { connectedDevices } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        every { currentPin } returns kotlinx.coroutines.flow.MutableStateFlow("123456")
        every { isLocked } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { maxDevices } returns 3
        coEvery { validateToken(any(), any()) } returns
            RemoteAuthResult.failure(RemoteAuthError.InvalidToken)
        coEvery { validatePin(any(), any(), any()) } returns
            RemoteAuthResult.failure(RemoteAuthError.InvalidPin)
    }

    /**
     * Returns an auth service where all tokens are valid (returns a fake device).
     */
    private fun authenticatedAuthService(): RemoteAuthorizing {
        val device = RemoteDevice(
            id = Uuid.random(),
            displayName = "TestDevice",
            ipAddress = "127.0.0.1",
            connectedAt = Clock.System.now(),
        )
        return mockk(relaxed = true) {
            every { securityEvents } returns kotlinx.coroutines.flow.MutableSharedFlow()
            every { devicesCount } returns kotlinx.coroutines.flow.MutableStateFlow(1)
            every { connectedDevices } returns kotlinx.coroutines.flow.MutableStateFlow(listOf(device))
            every { currentPin } returns kotlinx.coroutines.flow.MutableStateFlow("123456")
            every { isLocked } returns kotlinx.coroutines.flow.MutableStateFlow(false)
            every { maxDevices } returns 3
            coEvery { validateToken(any(), any()) } returns RemoteAuthResult.success(device)
        }
    }

    private fun fakeTerminalHost(): TerminalRemoteHost = mockk(relaxed = true) {
        every { sessionsByProject } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
        every { outputFlow(any()) } returns null
    }

    /**
     * Wires [RemoteRouting] into a [testApplication] with minimal mocks.
     */
    private fun withRoutingApp(
        authService: RemoteAuthorizing = unauthenticatedAuthService(),
        terminalService: TerminalRemoteHost = fakeTerminalHost(),
        block: suspend io.ktor.client.HttpClient.() -> Unit,
    ) = testApplication {
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val activeBridges = ConcurrentHashMap<UUID, RemoteSessionBridge>()
        val handlerJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val authHandler = RemoteAuthHandler(authService, handlerJson)
        val projectHandler = ProjectApiHandler(
            projectManaging = null,
            terminalService = terminalService,
            scrollbackAccessing = null,
            activeBridges = activeBridges,
            authHandler = authHandler,
            json = handlerJson,
        )
        val assistantHandler = AssistantApiHandler(
            assistantLauncher = null,
            authHandler = authHandler,
            json = handlerJson,
        )
        val routing = RemoteRouting(
            authHandler = authHandler,
            projectApiHandler = projectHandler,
            assistantApiHandler = assistantHandler,
            terminalService = terminalService,
            authService = authService,
            activeBridges = activeBridges,
            preferences = fakePreferences,
            serverScope = serverScope,
            json = handlerJson,
            onBridgeRegistered = {},
            onBridgeUnregistered = {},
            buildHealthResponse = {
                HealthResponse(
                    status = "healthy",
                    version = "0.2.0",
                    uptimeSeconds = 0L,
                    connectedDevices = 0,
                    maxDevices = 3,
                    tls = "none",
                )
            },
        )

        // RemoteRouting.configure() installs ContentNegotiation, WebSockets, CORS, and registers
        // all routes — do not install those plugins again here.
        application {
            routing.configure(this)
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        }
        client.block()
    }

    // ── /api/v1/auth route exists (POST returns non-404) ─────────────────────

    @Test
    fun auth_postToken_routeExists_returnsNon404() = withRoutingApp {
        val response = post("/api/v1/auth/token") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        // Route exists — even with invalid PIN we get a 4xx, not 404
        assertNotEquals(HttpStatusCode.NotFound, response.status, "POST /api/v1/auth/token must be registered")
    }

    // ── /api/v1/assistant routes exist ───────────────────────────────────────

    @Test
    fun assistant_postStart_routeExists_returnsNon404() = withRoutingApp {
        val response = post("/api/v1/assistant/start") {
            contentType(ContentType.Application.Json)
            setBody("""{"project_id":"00000000-0000-0000-0000-000000000001","assistant":"claude"}""")
        }
        assertNotEquals(HttpStatusCode.NotFound, response.status, "POST /api/v1/assistant/start must be registered")
    }

    @Test
    fun assistant_postStop_routeExists_returnsNon404() = withRoutingApp {
        val response = post("/api/v1/assistant/stop") {
            contentType(ContentType.Application.Json)
            setBody("""{"project_id":"00000000-0000-0000-0000-000000000001","assistant":"claude"}""")
        }
        assertNotEquals(HttpStatusCode.NotFound, response.status, "POST /api/v1/assistant/stop must be registered")
    }

    // ── /api/v1/projects routes exist ────────────────────────────────────────

    @Test
    fun projects_getProjects_routeExists_returnsNon404() = withRoutingApp(
        authService = authenticatedAuthService(),
    ) {
        val response = get("/api/v1/projects") {
            header("X-Auth-Token", "valid-token")
        }
        assertNotEquals(HttpStatusCode.NotFound, response.status, "GET /api/v1/projects must be registered")
    }

    @Test
    fun projects_getSessions_routeExists_returnsNon404() = withRoutingApp(
        authService = authenticatedAuthService(),
    ) {
        val response = get("/api/v1/sessions") {
            header("X-Auth-Token", "valid-token")
        }
        assertNotEquals(HttpStatusCode.NotFound, response.status, "GET /api/v1/sessions must be registered")
    }

    // ── Unauthenticated request to protected endpoint returns 401 ─────────────

    @Test
    fun unauthenticated_getProjects_returns401() = withRoutingApp {
        val response = get("/api/v1/projects")
        assertEquals(
            HttpStatusCode.Unauthorized,
            response.status,
            "GET /api/v1/projects without token must return 401",
        )
    }

    @Test
    fun unauthenticated_getSessions_returns401() = withRoutingApp {
        val response = get("/api/v1/sessions")
        assertEquals(
            HttpStatusCode.Unauthorized,
            response.status,
            "GET /api/v1/sessions without token must return 401",
        )
    }

    @Test
    fun unauthenticated_assistantStart_returns401() = withRoutingApp {
        val response = post("/api/v1/assistant/start") {
            contentType(ContentType.Application.Json)
            setBody("""{"project_id":"00000000-0000-0000-0000-000000000001","assistant":"claude"}""")
        }
        assertEquals(
            HttpStatusCode.Unauthorized,
            response.status,
            "POST /api/v1/assistant/start without token must return 401",
        )
    }

    // ── CORS preflight OPTIONS returns expected headers ────────────────────────

    @Test
    fun corsPreflightOptions_returnsExpectedHeaders() = withRoutingApp {
        val response = options("/api/v1/auth/token") {
            header(HttpHeaders.Origin, "https://app.vibestudio.ai")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "Content-Type, X-Auth-Token")
        }

        // CORS plugin returns 200 for preflight
        assertEquals(HttpStatusCode.OK, response.status, "OPTIONS preflight must return 200")

        val allowOrigin = response.headers[HttpHeaders.AccessControlAllowOrigin]
        assertTrue(
            allowOrigin != null,
            "CORS preflight response must include Access-Control-Allow-Origin",
        )
    }

    // ── Health endpoint reachable without auth ────────────────────────────────

    @Test
    fun health_getHealth_returnsOk() = withRoutingApp {
        val response = get("/health")
        assertEquals(HttpStatusCode.OK, response.status, "/health must return 200")
        val body = response.bodyAsText()
        assertTrue(body.contains("healthy"), "/health response must contain 'healthy'")
    }

    @Test
    fun health_getApiV1Health_returnsOk() = withRoutingApp {
        val response = get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, response.status, "/api/v1/health must return 200")
    }
}
