package studio.vibe.shared.core.common

sealed class AgentError(override val message: String) : Exception(message) {
    data class ExecutableNotFound(val agent: String) : AgentError("CLI executable not found for agent: $agent")
    data class MissingAPIKey(val agent: String, val envVar: String) : AgentError("API key $envVar not set for agent: $agent")
}
