@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.client.request.get
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
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import studio.vibe.shared.feature.remote.domain.contract.RemoteAuthorizing
import studio.vibe.shared.feature.remote.domain.contract.SecurityEvent
import studio.vibe.shared.feature.remote.domain.model.RemoteAuthError
import studio.vibe.shared.feature.remote.domain.model.RemoteAuthResult
import studio.vibe.shared.feature.remote.domain.model.RemoteAuthorizationToken
import studio.vibe.shared.feature.remote.domain.model.RemoteDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * Tests for [RemoteAuthHandler] exercising the /auth/token route through a real
 * Ktor [testApplication] — no network I/O, no OS resources.
 */
class RemoteAuthHandlerTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun mockDevice(ip: String = "127.0.0.1"): RemoteDevice = RemoteDevice(
        id = Uuid.random(),
        displayName = "Test Device",
        ipAddress = ip,
        connectedAt = Clock.System.now(),
    )

    private fun mockToken(device: RemoteDevice): RemoteAuthorizationToken =
        RemoteAuthorizationToken(
            token = "test-token-abc123",
            device = device,
            expiresAt = Clock.System.now() + 4.hours,
        )

    /** Run a testApplication with the auth routes wired via [RemoteAuthHandler]. */
    private fun withAuthApp(
        authService: RemoteAuthorizing,
        block: suspend io.ktor.client.HttpClient.() -> Unit,
    ) = testApplication {
        val handler = RemoteAuthHandler(authService, json)

        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                handler.registerAuth(this)
            }
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
        }
        client.block()
    }

    // ── missing token → 401 on validate ──────────────────────────────────────

    @Test
    fun validate_missingToken_returns401() = withAuthApp(mockk(relaxed = true)) {
        val response = get("/auth/validate")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ── valid PIN → token ─────────────────────────────────────────────────────

    @Test
    fun token_validPin_returns200WithToken() {
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        val device = mockDevice()
        val tok = mockToken(device)

        coEvery { authService.validatePin(any(), any(), any()) } returns RemoteAuthResult.success(tok)

        withAuthApp(authService) {
            val response = post("/auth/token") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"123456"}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assert(body.contains("test-token-abc123")) { "Response must contain the token: $body" }
        }
    }

    // ── wrong PIN → 401 ───────────────────────────────────────────────────────

    @Test
    fun token_wrongPin_returns401() {
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        coEvery { authService.validatePin(any(), any(), any()) } returns
            RemoteAuthResult.failure(RemoteAuthError.InvalidPin)

        withAuthApp(authService) {
            val response = post("/auth/token") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"000000"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    // ── rate-limited → 429 ────────────────────────────────────────────────────

    @Test
    fun token_rateLimited_returns429() {
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        coEvery { authService.validatePin(any(), any(), any()) } returns
            RemoteAuthResult.failure(RemoteAuthError.RateLimited(retryAfterSeconds = 30))

        withAuthApp(authService) {
            val response = post("/auth/token") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"111111"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, response.status)
        }
    }

    // ── GlobalLockout → 403 ───────────────────────────────────────────────────

    @Test
    fun token_globalLockout_returns403() {
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        coEvery { authService.validatePin(any(), any(), any()) } returns
            RemoteAuthResult.failure(RemoteAuthError.GlobalLockout)

        withAuthApp(authService) {
            val response = post("/auth/token") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"222222"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    // ── MaxDevicesReached → 403 ───────────────────────────────────────────────

    @Test
    fun token_maxDevicesReached_returns403() {
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        coEvery { authService.validatePin(any(), any(), any()) } returns
            RemoteAuthResult.failure(RemoteAuthError.MaxDevicesReached)

        withAuthApp(authService) {
            val response = post("/auth/token") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"333333"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    // ── expired token → 401 on validate ──────────────────────────────────────

    @Test
    fun validate_expiredToken_returns401() {
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        coEvery { authService.validateToken(any(), any()) } returns
            RemoteAuthResult.failure(RemoteAuthError.TokenExpired)

        withAuthApp(authService) {
            val response = get("/auth/validate") {
                header("X-Auth-Token", "expired-token")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val body = response.bodyAsText()
            assert(body.contains("token_expired")) { "Response must contain error code: $body" }
        }
    }

    // ── IP mismatch → 401 on validate ────────────────────────────────────────

    @Test
    fun validate_ipMismatch_returns401() {
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        coEvery { authService.validateToken(any(), any()) } returns
            RemoteAuthResult.failure(RemoteAuthError.IpMismatch)

        withAuthApp(authService) {
            val response = get("/auth/validate") {
                header("X-Auth-Token", "mismatched-token")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val body = response.bodyAsText()
            assert(body.contains("ip_mismatch")) { "Response must contain ip_mismatch code: $body" }
        }
    }
}
