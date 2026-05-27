package studio.vibe.shared.model

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Authentication
@Serializable
data class AuthTokenRequest(val pin: String)

@Serializable
data class AuthTokenResponseDTO(
    val token: String,
    @SerialName("expires_at") val expiresAt: Instant,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class AuthValidateResponse(
    val valid: Boolean,
    @SerialName("device_id") val deviceId: String,
    @SerialName("expires_at") val expiresAt: Instant,
)

// Projects & Sessions
@Serializable
data class ProjectResponse(
    val id: String,
    val name: String,
    val path: String,
    val color: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    val git: GitInfoResponse? = null,
    val sessions: List<SessionResponse>,
)

@Serializable
data class GitInfoResponse(
    val branch: String,
    val ahead: Int,
    val behind: Int,
)

@Serializable
data class SessionResponse(
    val id: String,
    val title: String,
    val state: String,
    @SerialName("is_agent") val isAgent: Boolean,
    @SerialName("has_remote_attachment") val hasRemoteAttachment: Boolean,
    @SerialName("attached_device_id") val attachedDeviceId: String? = null,
)

@Serializable
data class ScrollbackResponse(
    val content: String,
    @SerialName("total_lines") val totalLines: Int,
    @SerialName("returned_lines") val returnedLines: Int,
)

@Serializable
data class ProjectsListResponse(
    val projects: List<ProjectResponse>,
    @SerialName("active_project_id") val activeProjectId: String? = null,
)

// Recent Projects
@Serializable
data class RecentProjectResponse(
    val name: String,
    val path: String,
    @SerialName("last_opened") val lastOpened: String,
)

@Serializable
data class RecentProjectsListResponse(
    val projects: List<RecentProjectResponse>,
)

@Serializable
data class OpenProjectRequest(val path: String)

@Serializable
data class OpenProjectResponse(
    val ok: Boolean,
    @SerialName("project_id") val projectId: String,
)

// Devices
@Serializable
data class DeviceResponse(
    @SerialName("device_id") val deviceId: String,
    val ip: String,
    @SerialName("user_agent") val userAgent: String,
    @SerialName("connected_since") val connectedSince: Instant,
    @SerialName("last_activity") val lastActivity: Instant,
    @SerialName("attached_sessions") val attachedSessions: List<String>,
    @SerialName("is_self") val isSelf: Boolean,
)

@Serializable
data class DevicesListResponse(
    val devices: List<DeviceResponse>,
    @SerialName("max_devices") val maxDevices: Int,
)

// Health
@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    @SerialName("uptime_seconds") val uptimeSeconds: Int,
    @SerialName("connected_devices") val connectedDevices: Int,
    @SerialName("max_devices") val maxDevices: Int,
    val tls: String,
)

// Server Status
@Serializable
data class StatusResponse(
    val server: ServerInfoResponse,
    val connections: ConnectionsInfoResponse,
    val theme: ThemeInfoResponse,
) {
    @Serializable
    data class ServerInfoResponse(
        val version: String,
        @SerialName("api_version") val apiVersion: String,
        @SerialName("uptime_seconds") val uptimeSeconds: Int,
        val port: Int,
        val tls: String,
        @SerialName("bonjour_published") val bonjourPublished: Boolean,
    )

    @Serializable
    data class ConnectionsInfoResponse(
        @SerialName("connected_devices") val connectedDevices: Int,
        @SerialName("max_devices") val maxDevices: Int,
        @SerialName("active_websockets") val activeWebsockets: Int,
    )

    @Serializable
    data class ThemeInfoResponse(
        val appearance: String,
        @SerialName("terminal_colors") val terminalColors: TerminalColorsResponse,
    )
}

@Serializable
data class TerminalColorsResponse(
    val foreground: String,
    val background: String,
    val cursor: String,
    val selection: String,
    val ansi: List<String>,
)

@Serializable
data class OKResponse(val ok: Boolean)

// Assistant Control
@Serializable
data class AssistantStartRequest(val assistant: String? = null)

@Serializable
data class AssistantStartResponse(
    val ok: Boolean,
    val assistant: String,
    @SerialName("session_id") val sessionId: String,
)

// Error Responses
@Serializable
data class ErrorResponse(val error: ErrorDetail)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)

// WebSocket Messages (Client -> Server)
@Serializable
enum class WSMessageType {
    auth, input, resize, ping, detach
}

@Serializable
data class WSAuthMessage(val type: WSMessageType, val token: String)

@Serializable
data class WSInputMessage(val type: WSMessageType, val data: String)

@Serializable
data class WSResizeMessage(val type: WSMessageType, val cols: Int, val rows: Int)

@Serializable
data class WSPingMessage(val type: WSMessageType, val ts: Long)

// WebSocket Messages (Server -> Client)
@Serializable
data class WSSessionStateMessage(
    val type: String,
    @SerialName("session_id") val sessionId: String,
    val state: String,
    @SerialName("exit_code") val exitCode: Int? = null,
)

@Serializable
data class WSPongMessage(
    val type: String,
    val ts: Long,
    @SerialName("server_ts") val serverTs: Long,
)

@Serializable
data class WSThemeChangedMessage(
    val type: String,
    val appearance: String,
    @SerialName("terminal_colors") val terminalColors: TerminalColorsResponse,
)

@Serializable
data class WSSessionsChangedMessage(
    val type: String,
    @SerialName("project_id") val projectId: String,
    val sessions: List<SessionResponse>,
)

@Serializable
data class WSDeviceDisconnectedMessage(
    val type: String,
    @SerialName("device_id") val deviceId: String,
    val reason: String,
)

@Serializable
data class WSErrorMessage(
    val type: String,
    val code: String,
    val message: String,
    val fatal: Boolean,
)

@Serializable
data class WSRateLimitedMessage(
    val type: String,
    val message: String,
    @SerialName("retry_after_ms") val retryAfterMs: Int,
)
