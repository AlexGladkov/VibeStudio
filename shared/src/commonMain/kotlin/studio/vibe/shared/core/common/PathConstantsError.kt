package studio.vibe.shared.core.common

sealed class PathConstantsError(override val message: String) : Exception(message) {
    data object AppSupportNotFound : PathConstantsError("Application Support directory not found on this system")
}
