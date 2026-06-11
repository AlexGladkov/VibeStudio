@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.core.common.terminal.TerminalScrollbackAccessing
import studio.vibe.shared.feature.terminal.domain.contract.TerminalRemoteHost
import studio.vibe.shared.core.common.FilePath
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.uuid.Uuid

/**
 * Handles [/api/v1/projects] and [/api/v1/sessions] routes.
 *
 * Extracted from [RemoteControlServer.configureApp].
 * Uses [SessionResponseMapper] to build session DTOs — eliminates 4x duplication.
 */
internal class ProjectApiHandler(
    private val projectManaging: ProjectManaging?,
    private val terminalService: TerminalRemoteHost,
    private val scrollbackAccessing: TerminalScrollbackAccessing?,
    private val activeBridges: ConcurrentHashMap<UUID, RemoteSessionBridge>,
    private val authHandler: RemoteAuthHandler,
    private val json: Json,
) {

    /** Registers all project-related routes under [parent]. */
    fun registerProjects(parent: Route) = with(parent) {

        // ── Sessions (flat list) ───────────────────────────────────────────
        get("/sessions") {
            authHandler.requireAuth(call) ?: return@get
            val sessions = terminalService.sessionsByProject.value.values.flatten()
            call.respond(sessions.map { s ->
                SessionResponseMapper.map(s) { activeBridges.values }
            })
        }

        // ── Status ────────────────────────────────────────────────────────
        get("/status") {
            authHandler.requireAuth(call) ?: return@get
            call.respond(StatusResponse(theme = "dark"))
        }

        // ── Projects ──────────────────────────────────────────────────────
        get("/projects") {
            authHandler.requireAuth(call) ?: return@get
            val pm = projectManaging
            if (pm == null) {
                call.respond(ProjectListResponse(projects = emptyList(), activeProjectId = null))
                return@get
            }
            val sessionMap = terminalService.sessionsByProject.value
            val activeId = pm.activeProjectId.value?.toString()
            val projects = pm.projects.value.map { project ->
                val sessions = (sessionMap[project.id] ?: emptyList()).map { s ->
                    SessionResponseMapper.map(s) { activeBridges.values }
                }
                ProjectResponse(id = project.id.toString(), name = project.name, path = project.path.path, sessions = sessions)
            }
            call.respond(ProjectListResponse(projects = projects, activeProjectId = activeId))
        }

        get("/projects/recent") {
            authHandler.requireAuth(call) ?: return@get
            val pm = projectManaging
            if (pm == null) {
                call.respond(ProjectListResponse(projects = emptyList(), activeProjectId = null))
                return@get
            }
            val sessionMap = terminalService.sessionsByProject.value
            val activeId = pm.activeProjectId.value?.toString()
            val projects = pm.recentProjects.value.map { project ->
                val sessions = (sessionMap[project.id] ?: emptyList()).map { s ->
                    SessionResponseMapper.map(s) { activeBridges.values }
                }
                ProjectResponse(id = project.id.toString(), name = project.name, path = project.path.path, sessions = sessions)
            }
            call.respond(ProjectListResponse(projects = projects, activeProjectId = activeId))
        }

        post("/projects/open") {
            authHandler.requireAuth(call) ?: return@post
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
            val sessions = (sessionMap[project.id] ?: emptyList()).map { s ->
                SessionResponseMapper.map(s) { activeBridges.values }
            }
            call.respond(ProjectResponse(
                id = project.id.toString(),
                name = project.name,
                path = project.path.path,
                sessions = sessions,
            ))
        }

        route("/projects/{projectId}") {
            post("/activate") {
                authHandler.requireAuth(call) ?: return@post
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
                authHandler.requireAuth(call) ?: return@get
                val sessionIdStr = call.parameters["sessionId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Missing sessionId")))
                    return@get
                }
                val sessionId = runCatching { Uuid.parse(sessionIdStr) }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("bad_request", "Invalid sessionId")))
                    return@get
                }
                val content = scrollbackAccessing?.scrollbackContent(sessionId) ?: ""
                call.respond(ScrollbackResponse(
                    content = content,
                    totalLines = if (content.isEmpty()) 0 else content.lines().size,
                ))
            }
        }
    }
}
