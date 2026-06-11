package studio.vibe.shared.core.common

import kotlin.coroutines.cancellation.CancellationException
import studio.vibe.shared.core.common.AICommitServiceError

interface AICommitServicing {
    /**
     * Generates a commit message from [diff] using an LLM.
     *
     * @throws AICommitServiceError if the API key is missing, the request fails,
     *   or the response cannot be parsed.
     * @throws CancellationException if the coroutine is cancelled while awaiting
     *   the network response.
     */
    @Throws(AICommitServiceError::class, CancellationException::class)
    suspend fun generateCommitMessage(diff: String): String
}
