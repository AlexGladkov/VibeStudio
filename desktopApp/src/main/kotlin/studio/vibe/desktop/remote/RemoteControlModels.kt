package studio.vibe.desktop.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Domain types (RemoteDevice, AuthError, etc.) now live in shared:
// studio.vibe.shared.model.RemoteDevice, RemoteAuthError, RemoteAuthResult
// and are surfaced via studio.vibe.shared.contract.RemoteAuthorizing.

// ── REST API DTOs ──────────────────────────────────────────────────────────────

@Serializable
data class AuthTokenRequest(
    val pin: String,
)

@Serializable
data class AuthTokenResponseDTO(
    val token: String,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class AuthValidateResponse(
    val valid: Boolean,
    @SerialName("device_id") val deviceId: String,
    @SerialName("expires_at") val expiresAt: Long,
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    @SerialName("uptime_seconds") val uptimeSeconds: Long,
    @SerialName("connected_devices") val connectedDevices: Int,
    @SerialName("max_devices") val maxDevices: Int,
    val tls: String,
)

@Serializable
data class OKResponse(val ok: Boolean)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
)

@Serializable
data class ErrorResponse(val error: ErrorDetail)

@Serializable
data class SessionResponse(
    val id: String,
    val title: String,
    val state: String,
    @SerialName("is_agent") val isAgent: Boolean,
    @SerialName("has_remote_attachment") val hasRemoteAttachment: Boolean,
    @SerialName("attached_device_id") val attachedDeviceId: String?,
)

@Serializable
data class ProjectResponse(
    val id: String,
    val name: String,
    val path: String,
    val sessions: List<SessionResponse>,
)

@Serializable
data class ProjectListResponse(
    val projects: List<ProjectResponse>,
    @SerialName("active_project_id") val activeProjectId: String?,
)

@Serializable
data class ScrollbackResponse(
    val content: String,
    @SerialName("total_lines") val totalLines: Int,
)

@Serializable
data class StatusResponse(
    val theme: String?,
)

@Serializable
data class OpenProjectRequest(
    val path: String,
)

@Serializable
data class AssistantStartRequest(
    @SerialName("project_id") val projectId: String,
    val assistant: String,
)

@Serializable
data class AssistantStartResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("project_id") val projectId: String,
    val assistant: String,
)

@Serializable
data class AssistantStopRequest(
    @SerialName("project_id") val projectId: String,
    val assistant: String,
)

// ── WebSocket message envelopes ───────────────────────────────────────────────

@Serializable
data class WSTypeEnvelope(
    val type: String,
)

@Serializable
data class WSAuthMessage(
    val type: String,
    val token: String,
)

@Serializable
data class WSInputMessage(
    val type: String,
    val data: String,
)

@Serializable
data class WSResizeMessage(
    val type: String,
    val cols: Int,
    val rows: Int,
)

@Serializable
data class WSPingMessage(
    val type: String,
    val ts: Long,
)

@Serializable
data class WSPongMessage(
    val type: String,
    val ts: Long,
    @SerialName("server_ts") val serverTs: Long,
)

@Serializable
data class WSRateLimitedMessage(
    val type: String,
    val message: String,
    @SerialName("retry_after_ms") val retryAfterMs: Int,
)

@Serializable
data class WSErrorMessage(
    val type: String,
    val code: String,
    val message: String,
    val fatal: Boolean,
)
