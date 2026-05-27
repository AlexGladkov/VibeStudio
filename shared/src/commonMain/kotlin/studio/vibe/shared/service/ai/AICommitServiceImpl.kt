package studio.vibe.shared.service.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import studio.vibe.shared.constants.AIConstants
import studio.vibe.shared.contract.AICommitServicing
import studio.vibe.shared.contract.APIKeyResolving
import studio.vibe.shared.model.AICommitServiceError

class AICommitServiceImpl(
    private val httpClient: HttpClient,
    private val apiKeyResolver: APIKeyResolving,
) : AICommitServicing {

    override suspend fun generateCommitMessage(diff: String): String {
        val apiKey = apiKeyResolver.resolve("ANTHROPIC_API_KEY")
            ?: throw AICommitServiceError.MissingAPIKey

        val truncatedDiff = if (diff.length > AIConstants.MAX_DIFF_LENGTH) {
            diff.take(AIConstants.MAX_DIFF_LENGTH)
        } else {
            diff
        }

        val request = AnthropicRequest(
            model = AIConstants.COMMIT_MODEL,
            maxTokens = AIConstants.COMMIT_MAX_TOKENS,
            messages = listOf(
                AnthropicMessage(
                    role = "user",
                    content = buildPrompt(truncatedDiff),
                )
            ),
        )

        val response = httpClient.post(AIConstants.ANTHROPIC_API_URL) {
            contentType(ContentType.Application.Json)
            header("x-api-key", apiKey)
            header("anthropic-version", AIConstants.ANTHROPIC_VERSION)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            throw AICommitServiceError.ApiError(response.status.value)
        }

        val body = try {
            response.body<AnthropicResponse>()
        } catch (e: Exception) {
            throw AICommitServiceError.InvalidResponseFormat
        }

        return body.content.firstOrNull()?.text
            ?: throw AICommitServiceError.InvalidResponseFormat
    }

    private fun buildPrompt(diff: String): String =
        """Write a concise git commit message for the following diff.
Use the conventional commits format: type(scope): description
Keep the first line under 72 characters.
Reply with only the commit message, no explanation.

Diff:
$diff""".trimIndent()
}

// ── Private DTOs ──────────────────────────────────────────────────────────────

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>,
)

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class AnthropicResponse(
    val content: List<ContentBlock>,
)

@Serializable
private data class ContentBlock(
    val text: String,
)
