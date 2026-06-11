@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import studio.vibe.shared.feature.assistant.domain.usecase.AssistantLauncher
import kotlin.uuid.Uuid

/**
 * Handles [/api/v1/assistant] routes (start + stop).
 *
 * Extracted from [RemoteControlServer.configureApp].
 */
internal class AssistantApiHandler(
    private val assistantLauncher: AssistantLauncher?,
    private val authHandler: RemoteAuthHandler,
    private val json: Json,
) {

    /** Registers assistant start/stop routes under [parent]. */
    fun registerAssistant(parent: Route) = with(parent) {
        post("/assistant/start") { handleStart(call) }
        post("/assistant/stop") { handleStop(call) }
    }

    // ── Private handlers ───────────────────────────────────────────────────────

    private suspend fun handleStart(call: ApplicationCall) {
        authHandler.requireAuth(call) ?: return
        val launcher = assistantLauncher ?: run {
            call.respond(
                HttpStatusCode.NotImplemented,
                ErrorResponse(ErrorDetail("not_implemented", "AssistantLauncher not wired")),
            )
            return
        }
        val bodyText = runCatching { call.receiveText() }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Could not read body")))
            return
        }
        val req = runCatching { json.decodeFromString<AssistantStartRequest>(bodyText) }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Invalid JSON body")))
            return
        }
        val projectId = runCatching { Uuid.parse(req.projectId) }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Invalid project_id")))
            return
        }
        val result = launcher.start(projectId, req.assistant)
        result.onSuccess { session ->
            call.respond(AssistantStartResponse(
                sessionId = session.id.toString(),
                projectId = req.projectId,
                assistant = req.assistant,
            ))
        }.onFailure { e ->
            val (status, code, message) = when {
                e.message?.contains("not found", ignoreCase = true) == true ->
                    Triple(HttpStatusCode.NotFound, "not_found", e.message ?: "Not found")
                e.message?.contains("install", ignoreCase = true) == true ||
                    e.message?.contains("not installed", ignoreCase = true) == true ->
                    Triple(HttpStatusCode.UnprocessableEntity, "agent_not_installed", e.message ?: "Agent not installed")
                else ->
                    Triple(HttpStatusCode.InternalServerError, "launch_failed", e.message ?: "Failed to start agent")
            }
            call.respond(status, ErrorResponse(ErrorDetail(code, message)))
        }
    }

    private suspend fun handleStop(call: ApplicationCall) {
        authHandler.requireAuth(call) ?: return
        val launcher = assistantLauncher ?: run {
            call.respond(
                HttpStatusCode.NotImplemented,
                ErrorResponse(ErrorDetail("not_implemented", "AssistantLauncher not wired")),
            )
            return
        }
        val bodyText = runCatching { call.receiveText() }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Could not read body")))
            return
        }
        val req = runCatching { json.decodeFromString<AssistantStopRequest>(bodyText) }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Invalid JSON body")))
            return
        }
        val projectId = runCatching { Uuid.parse(req.projectId) }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Invalid project_id")))
            return
        }
        val result = launcher.stop(projectId, req.assistant)
        result.onSuccess {
            call.respond(OKResponse(ok = true))
        }.onFailure { e ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorDetail("stop_failed", e.message ?: "Failed to stop agent")),
            )
        }
    }
}
