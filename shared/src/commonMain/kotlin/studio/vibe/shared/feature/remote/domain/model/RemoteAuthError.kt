package studio.vibe.shared.feature.remote.domain.model

sealed class RemoteAuthError {
    data object InvalidPin : RemoteAuthError()
    data class RateLimited(val retryAfterSeconds: Int) : RemoteAuthError()
    data object GlobalLockout : RemoteAuthError()
    data object InvalidToken : RemoteAuthError()
    data object TokenExpired : RemoteAuthError()
    data object IpMismatch : RemoteAuthError()
    data object MaxDevicesReached : RemoteAuthError()
}
