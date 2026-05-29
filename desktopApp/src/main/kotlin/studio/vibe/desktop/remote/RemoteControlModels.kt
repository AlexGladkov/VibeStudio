package studio.vibe.desktop.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

// ── Domain types ──────────────────────────────────────────────────────────────

/**
 * A remote device that has successfully authenticated via PIN.
 */
data class RemoteDevice(
    val id: UUID,
    val displayName: String,
    val ipAddress: String,
    val connectedAt: Long = System.currentTimeMillis(),
)

/**
 * Internal token storage entry — never exposed to clients.
 */
internal data class TokenEntry(
    val deviceId: UUID,
    val clientIP: String,
    val issuedAt: Long,
    val expiresAt: Long,
)

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
