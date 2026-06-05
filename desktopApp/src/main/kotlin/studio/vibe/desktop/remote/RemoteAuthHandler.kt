@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import studio.vibe.shared.contract.RemoteAuthorizing
import studio.vibe.shared.model.RemoteAuthError
import studio.vibe.shared.model.RemoteAuthResult
import studio.vibe.shared.model.RemoteDevice

/**
 * Handles [/api/v1/auth] routes (token + validate).
 *
 * Extracted from [RemoteControlServer.configureApp] to keep routing size manageable.
 * Also exposes [requireAuth] as a shared helper used by all other handlers.
 */
internal class RemoteAuthHandler(
    private val authService: RemoteAuthorizing,
    private val json: Json,
) {

    /** Registers the auth sub-routes under [parent]. */
    fun registerAuth(parent: Route) {
        parent.route("/auth") {
            post("/token") { handleToken(call) }
            get("/validate") { handleValidate(call) }
        }
    }

    /**
     * Validates the `X-Auth-Token` / `Authorization: Bearer` header on [call].
     *
     * Returns the authenticated [RemoteDevice] on success, or responds with an
     * appropriate 4xx status and returns `null` (the caller should return early).
     */
    suspend fun requireAuth(call: ApplicationCall): RemoteDevice? {
        val token = call.request.headers["X-Auth-Token"]
            ?: call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()

        if (token.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(ErrorDetail("missing_token", "Authorization required")),
            )
            return null
        }

        return when (val result = authService.validateToken(token, call.request.local.remoteHost)) {
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
                call.respond(status, ErrorResponse(ErrorDetail(code, message)))
                null
            }
        }
    }

    // ── Private handlers ───────────────────────────────────────────────────────

    private suspend fun handleToken(call: ApplicationCall) {
        val clientIP = call.request.local.remoteHost
        val userAgent = call.request.headers[HttpHeaders.UserAgent] ?: ""

        val bodyText = runCatching { call.receiveText() }.getOrElse {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorDetail("bad_request", "Could not read body")),
            )
            return
        }

        val body = runCatching {
            json.decodeFromString<AuthTokenRequest>(bodyText)
        }.getOrElse {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorDetail("bad_request", "Invalid JSON body")),
            )
            return
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
                        Triple(
                            HttpStatusCode.TooManyRequests,
                            "rate_limited",
                            "Too many attempts. Retry after ${err.retryAfterSeconds}s",
                        )
                    RemoteAuthError.GlobalLockout ->
                        Triple(HttpStatusCode.Forbidden, "global_lockout", "Server is locked")
                    RemoteAuthError.MaxDevicesReached ->
                        Triple(HttpStatusCode.Forbidden, "max_devices", "Maximum device limit reached")
                    else ->
                        Triple(HttpStatusCode.Unauthorized, "auth_error", "Authentication failed")
                }
                call.respond(status, ErrorResponse(ErrorDetail(code, message)))
            }
        }
    }

    private suspend fun handleValidate(call: ApplicationCall) {
        val device = requireAuth(call) ?: return
        call.respond(
            AuthValidateResponse(
                valid = true,
                deviceId = device.id.toString(),
                expiresAt = System.currentTimeMillis() + 4 * 3600 * 1000L,
            )
        )
    }
}
