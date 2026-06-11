package studio.vibe.shared.feature.git.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import studio.vibe.shared.core.common.AICommitServiceError
import studio.vibe.shared.core.common.AICommitServiceImpl
import studio.vibe.shared.testutil.FakeAPIKeyResolving
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for [AICommitServiceImpl].
 *
 * Uses ktor's [MockEngine] to simulate Anthropic API responses without real network I/O.
 * NOTE: ktor-client-mock must be on the test classpath. If the build fails with
 * "Unresolved reference: MockEngine", add:
 *   implementation("io.ktor:ktor-client-mock:<version>") to commonTest dependencies.
 *
 * If MockEngine is unavailable, the HTTP-dependent tests below are skipped — see
 * "AICommitServiceImpl — deferred HTTP tests" note in the report.
 */
class AICommitServiceImplTest {

    private val validResponseJson = """
        {
          "content": [{"text": "feat(auth): add OAuth login"}]
        }
    """.trimIndent()

    private fun buildClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun mockEngine(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = validResponseJson,
    ): MockEngine = MockEngine { _ ->
        respond(
            content = responseBody,
            status = status,
            headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString())),
        )
    }

    // ── missing API key ────────────────────────────────────────────────────────────

    @Test
    fun generateCommitMessage_missingApiKey_throwsMissingAPIKey() = runTest {
        val apiKeys = FakeAPIKeyResolving() // no key stored
        val service = AICommitServiceImpl(
            httpClient = buildClient(mockEngine()),
            apiKeyResolver = apiKeys,
        )

        assertFailsWith<AICommitServiceError.MissingAPIKey> {
            service.generateCommitMessage("some diff text")
        }
    }

    // ── empty diff ────────────────────────────────────────────────────────────────

    @Test
    fun generateCommitMessage_emptyDiff_sendsRequestAndReturnsMessage() = runTest {
        val apiKeys = FakeAPIKeyResolving()
        apiKeys["ANTHROPIC_API_KEY"] = "sk-test"
        val service = AICommitServiceImpl(
            httpClient = buildClient(mockEngine()),
            apiKeyResolver = apiKeys,
        )

        val result = service.generateCommitMessage("")

        assertEquals("feat(auth): add OAuth login", result)
    }

    // ── large diff truncation ─────────────────────────────────────────────────────

    @Test
    fun generateCommitMessage_largeDiff_truncatesBeforeSending() = runTest {
        val apiKeys = FakeAPIKeyResolving()
        apiKeys["ANTHROPIC_API_KEY"] = "sk-test"

        var capturedBodyLength = 0L
        val capturingEngine = MockEngine { request ->
            capturedBodyLength = request.body.contentLength ?: 0L
            respond(
                content = validResponseJson,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString())),
            )
        }

        val service = AICommitServiceImpl(
            httpClient = buildClient(capturingEngine),
            apiKeyResolver = apiKeys,
        )
        val largeDiff = "x".repeat(100_000)

        service.generateCommitMessage(largeDiff)

        // The serialized body must be finite and bounded — the raw diff was truncated
        // to MAX_DIFF_LENGTH (8000) before being embedded in the JSON request.
        assertTrue(capturedBodyLength < 50_000L, "Request body should be bounded, was $capturedBodyLength bytes")
    }

    // ── API error propagation ─────────────────────────────────────────────────────

    @Test
    fun generateCommitMessage_apiReturns401_throwsApiError() = runTest {
        val apiKeys = FakeAPIKeyResolving()
        apiKeys["ANTHROPIC_API_KEY"] = "bad-key"
        val service = AICommitServiceImpl(
            httpClient = buildClient(mockEngine(status = HttpStatusCode.Unauthorized, responseBody = "{}")),
            apiKeyResolver = apiKeys,
        )

        val err = assertFailsWith<AICommitServiceError.ApiError> {
            service.generateCommitMessage("diff text")
        }
        assertEquals(401, err.statusCode)
    }

    @Test
    fun generateCommitMessage_malformedResponse_throwsInvalidResponseFormat() = runTest {
        val apiKeys = FakeAPIKeyResolving()
        apiKeys["ANTHROPIC_API_KEY"] = "sk-test"
        val service = AICommitServiceImpl(
            httpClient = buildClient(mockEngine(responseBody = "not valid json")),
            apiKeyResolver = apiKeys,
        )

        assertFailsWith<AICommitServiceError.InvalidResponseFormat> {
            service.generateCommitMessage("diff")
        }
    }
}
