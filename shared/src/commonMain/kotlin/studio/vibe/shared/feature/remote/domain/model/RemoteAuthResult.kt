package studio.vibe.shared.feature.remote.domain.model

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
