package studio.vibe.shared.model

import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A remote device that has successfully authenticated via PIN.
 */
@OptIn(ExperimentalUuidApi::class)
data class RemoteDevice(
    val id: Uuid,
    val displayName: String,
    val ipAddress: String,
    val connectedAt: Instant,
)

/**
 * Authorization issued after a successful PIN validation.
 */
@OptIn(ExperimentalUuidApi::class)
data class RemoteAuthorizationToken(
    val token: String,
    val device: RemoteDevice,
    val expiresAt: Instant,
)

/**
 * Authentication errors with machine-readable context.
 */
sealed class RemoteAuthError {
    data object InvalidPin : RemoteAuthError()
    data class RateLimited(val retryAfterSeconds: Int) : RemoteAuthError()
    data object GlobalLockout : RemoteAuthError()
    data object InvalidToken : RemoteAuthError()
    data object TokenExpired : RemoteAuthError()
    data object IpMismatch : RemoteAuthError()
    data object MaxDevicesReached : RemoteAuthError()
}

/**
 * Two-channel result for remote-auth operations: success carries [T],
 * failure carries a sealed [RemoteAuthError] so callers can exhaustively
 * `when` over the error variants.
 */
sealed class RemoteAuthResult<out T> {
    data class Success<T>(val value: T) : RemoteAuthResult<T>()
    data class Failure(val error: RemoteAuthError) : RemoteAuthResult<Nothing>()

    companion object {
        fun <T> success(value: T): RemoteAuthResult<T> = Success(value)
        fun failure(error: RemoteAuthError): RemoteAuthResult<Nothing> = Failure(error)
    }

    val isSuccess: Boolean get() = this is Success
    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): RemoteAuthError? = (this as? Failure)?.error
}
