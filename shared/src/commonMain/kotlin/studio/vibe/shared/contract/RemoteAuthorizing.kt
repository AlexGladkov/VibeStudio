package studio.vibe.shared.contract

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.model.RemoteAuthResult
import studio.vibe.shared.model.RemoteAuthorizationToken
import studio.vibe.shared.model.RemoteDevice
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * PIN-based authorization for the Remote Control HTTP/WebSocket server.
 *
 * Implementations must be safe to call from arbitrary coroutine dispatchers
 * concurrently (typical caller is a Ktor route running on `Dispatchers.IO`).
 */
@OptIn(ExperimentalUuidApi::class)
interface RemoteAuthorizing {

    /** Current 6-digit PIN displayed to the user. */
    val currentPin: StateFlow<String>

    /** Currently authenticated remote devices. */
    val connectedDevices: StateFlow<List<RemoteDevice>>

    /** `true` after [GLOBAL_LOCKOUT_THRESHOLD] failed PIN attempts. */
    val isLocked: StateFlow<Boolean>

    /** Maximum number of simultaneously authenticated devices. */
    val maxDevices: Int

    /** Optional callback invoked when the global lockout triggers. */
    var onSecurityLockout: (() -> Unit)?

    /** Optional callback invoked whenever [connectedDevices] changes. */
    var onDevicesChanged: ((Int) -> Unit)?

    suspend fun validatePin(
        pin: String,
        clientIP: String,
        userAgent: String,
    ): RemoteAuthResult<RemoteAuthorizationToken>

    suspend fun validateToken(
        token: String,
        clientIP: String,
    ): RemoteAuthResult<RemoteDevice>

    suspend fun revokeDevice(deviceId: Uuid)
    suspend fun revokeAllDevices()
    suspend fun resetLockout()
    fun regeneratePin()
}
