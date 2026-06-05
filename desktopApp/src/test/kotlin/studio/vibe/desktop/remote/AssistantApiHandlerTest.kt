@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import studio.vibe.shared.contract.RemoteAuthorizing
import studio.vibe.shared.model.RemoteAuthResult
import studio.vibe.shared.model.RemoteDevice
import studio.vibe.shared.model.TerminalSession
import studio.vibe.shared.usecase.AssistantLauncher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Unit-level tests for [AssistantApiHandler].
 *
 * Uses Ktor [testApplication] with a manually-wired handler — no OS ports, no
 * full [RemoteRouting] stack.  Auth is always satisfied via a relaxed mock that
 * returns a valid [RemoteDevice] so tests stay focused on the handler logic.
 */
class AssistantApiHandlerTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private val projectId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000042")

    private fun fakeDevice(): RemoteDevice = RemoteDevice(
        id = Uuid.random(),
        displayName = "Test Device",
        ipAddress = "127.0.0.1",
        connectedAt = Clock.System.now(),
    )

    private fun authenticatedAuthHandler(): RemoteAuthHandler {
        val device = fakeDevice()
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        coEvery { authService.validateToken(any(), any()) } returns RemoteAuthResult.success(device)
        return RemoteAuthHandler(authService, json)
    }

    private fun unauthenticatedAuthHandler(): RemoteAuthHandler {
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        coEvery { authService.validateToken(any(), any()) } returns
            RemoteAuthResult.failure(studio.vibe.shared.model.RemoteAuthError.InvalidToken)
        return RemoteAuthHandler(authService, json)
    }

    private fun fakeSession(projId: Uuid = projectId): TerminalSession = TerminalSession(
        id = Uuid.random(),
        projectId = projId,
        title = "claude-agent",
        isAgentSession = true,
    )

    /**
     * Wires [AssistantApiHandler] into a minimal [testApplication].
     * Routes are registered under `/api/v1` to mirror production routing.
     */
    private fun withHandlerApp(
        launcher: AssistantLauncher?,
        authHandler: RemoteAuthHandler = authenticatedAuthHandler(),
        block: suspend io.ktor.client.HttpClient.() -> Unit,
    ) = testApplication {
        val handler = AssistantApiHandler(
            assistantLauncher = launcher,
            authHandler = authHandler,
            json = json,
        )
        application {
            install(ContentNegotiation) { json(json) }
            routing {
                route("/api/v1") {
                    handler.registerAssistant(this)
                }
            }
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        }
        client.block()
    }

    // ── handleStart — valid params → 200 with session_id ─────────────────────

    @Test
    fun handleStart_validParams_returns200WithSessionId() {
        val session = fakeSession()
        val launcher = mockk<AssistantLauncher>()
        coEvery { launcher.start(projectId, "claude") } returns Result.success(session)

        withHandlerApp(launcher) {
            val response = post("/api/v1/assistant/start") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"project_id":"$projectId","assistant":"claude"}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains(session.id.toString()), "Body must contain session id: $body")
            assertTrue(body.contains(projectId.toString()), "Body must contain project id: $body")
            assertTrue(body.contains("claude"), "Body must contain assistant name: $body")
        }
    }

    // ── handleStart — unknown project → 404 ──────────────────────────────────

    @Test
    fun handleStart_unknownProject_returns404() {
        val launcher = mockk<AssistantLauncher>()
        coEvery { launcher.start(projectId, "claude") } returns
            Result.failure(IllegalArgumentException("Project not found: $projectId"))

        withHandlerApp(launcher) {
            val response = post("/api/v1/assistant/start") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"project_id":"$projectId","assistant":"claude"}""")
            }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("not_found"), "Body must contain error code not_found: $body")
        }
    }

    // ── handleStart — agent not installed → 422 ───────────────────────────────

    @Test
    fun handleStart_agentNotInstalled_returns422() {
        val launcher = mockk<AssistantLauncher>()
        coEvery { launcher.start(projectId, "claude") } returns
            Result.failure(IllegalStateException("CLI executable not installed for agent: claude"))

        withHandlerApp(launcher) {
            val response = post("/api/v1/assistant/start") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"project_id":"$projectId","assistant":"claude"}""")
            }

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("agent_not_installed"), "Body must contain error code agent_not_installed: $body")
        }
    }

    // ── handleStart — launcher null → 501 ─────────────────────────────────────

    @Test
    fun handleStart_launcherNull_returns501() {
        withHandlerApp(launcher = null) {
            val response = post("/api/v1/assistant/start") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"project_id":"$projectId","assistant":"claude"}""")
            }

            assertEquals(HttpStatusCode.NotImplemented, response.status)
        }
    }

    // ── handleStop — running session → 200 ok ─────────────────────────────────

    @Test
    fun handleStop_runningSession_returns200() {
        val launcher = mockk<AssistantLauncher>()
        coEvery { launcher.stop(projectId, "claude") } returns Result.success(Unit)

        withHandlerApp(launcher) {
            val response = post("/api/v1/assistant/stop") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"project_id":"$projectId","assistant":"claude"}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("true"), "Body must contain ok:true: $body")
        }
    }

    // ── handleStop — stop fails (unknown session) → 500 ──────────────────────

    @Test
    fun handleStop_stopFails_returns500WithErrorCode() {
        val launcher = mockk<AssistantLauncher>()
        coEvery { launcher.stop(projectId, "claude") } returns
            Result.failure(IllegalStateException("Session not found for claude in project $projectId"))

        withHandlerApp(launcher) {
            val response = post("/api/v1/assistant/stop") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"project_id":"$projectId","assistant":"claude"}""")
            }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("stop_failed"), "Body must contain error code stop_failed: $body")
        }
    }

    // ── handleStop — launcher null → 501 ──────────────────────────────────────

    @Test
    fun handleStop_launcherNull_returns501() {
        withHandlerApp(launcher = null) {
            val response = post("/api/v1/assistant/stop") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"project_id":"$projectId","assistant":"claude"}""")
            }

            assertEquals(HttpStatusCode.NotImplemented, response.status)
        }
    }

    // ── handleStart — no auth token → 401 ────────────────────────────────────

    @Test
    fun handleStart_missingToken_returns401() {
        val launcher = mockk<AssistantLauncher>(relaxed = true)

        withHandlerApp(launcher, authHandler = unauthenticatedAuthHandler()) {
            val response = post("/api/v1/assistant/start") {
                // No X-Auth-Token header
                contentType(ContentType.Application.Json)
                setBody("""{"project_id":"$projectId","assistant":"claude"}""")
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    // ── Response mapping — AssistantStartResponse fields correct ─────────────

    @Test
    fun handleStart_responseMapping_containsAllExpectedFields() {
        val session = fakeSession()
        val launcher = mockk<AssistantLauncher>()
        coEvery { launcher.start(projectId, "codex") } returns Result.success(session)

        withHandlerApp(launcher) {
            val response = post("/api/v1/assistant/start") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"project_id":"$projectId","assistant":"codex"}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()

            // Verify all three fields of AssistantStartResponse are present
            assertTrue(body.contains("session_id"), "Response must include session_id field: $body")
            assertTrue(body.contains("project_id"), "Response must include project_id field: $body")
            assertTrue(body.contains("assistant"), "Response must include assistant field: $body")
            assertTrue(body.contains("codex"), "Response assistant value must be codex: $body")
        }
    }

    // ── handleStart — invalid JSON body → 400 ────────────────────────────────

    @Test
    fun handleStart_invalidJson_returns400() {
        val launcher = mockk<AssistantLauncher>(relaxed = true)

        withHandlerApp(launcher) {
            val response = post("/api/v1/assistant/start") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("not-valid-json")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    // ── handleStart — invalid UUID project_id → 400 ───────────────────────────

    @Test
    fun handleStart_invalidProjectId_returns400() {
        val launcher = mockk<AssistantLauncher>(relaxed = true)

        withHandlerApp(launcher) {
            val response = post("/api/v1/assistant/start") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"project_id":"not-a-uuid","assistant":"claude"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }
}
