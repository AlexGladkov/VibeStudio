package studio.vibe.shared.core.common

sealed class AICommitServiceError(override val message: String) : Exception(message) {
    data object MissingAPIKey : AICommitServiceError("ANTHROPIC_API_KEY not set in environment")
    data class ApiError(val statusCode: Int) : AICommitServiceError("Anthropic API returned status $statusCode")
    data object InvalidResponseFormat : AICommitServiceError("Invalid API response format")
    data object InvalidConfiguration : AICommitServiceError("Invalid AI service configuration")
}
