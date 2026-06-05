package studio.vibe.desktop.remote.ngrok

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals

/**
 * Unit tests for [NgrokUrlPoller].
 *
 * Uses a lightweight [HttpServer] (bundled with the JDK) to serve mock ngrok
 * API responses on localhost — no real ngrok binary needed.
 */
class NgrokUrlPollerTest {

    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private val poller = NgrokUrlPoller()

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        serverPort = server.address.port
        server.start()
    }

    @AfterTest
    fun stopServer() {
        server.stop(0)
    }

    private fun apiUrl() = "http://localhost:$serverPort/api/tunnels"

    private fun registerHandler(path: String, responseBody: String, statusCode: Int = 200) {
        server.createContext(path) { exchange ->
            val bytes = responseBody.encodeToByteArray()
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    // ── valid JSON with one tunnel ────────────────────────────────────────────

    @Test
    fun fetchTunnelUrl_validJsonWithOneTunnel_returnsPublicUrl() {
        val json = """
            {
              "tunnels": [
                {
                  "name": "command_line",
                  "public_url": "https://abc123.ngrok-free.app",
                  "proto": "https"
                }
              ],
              "uri": "/api/tunnels"
            }
        """.trimIndent()
        registerHandler("/api/tunnels", json)

        val result = poller.fetchTunnelUrl(apiUrl())

        assertNotNull(result, "fetchTunnelUrl must return a URL when valid JSON is returned")
        assertEquals("https://abc123.ngrok-free.app", result)
    }

    // ── empty tunnels array → null ────────────────────────────────────────────

    @Test
    fun fetchTunnelUrl_emptyTunnelsArray_returnsNull() {
        val json = """{"tunnels": [], "uri": "/api/tunnels"}"""
        registerHandler("/api/tunnels", json)

        val result = poller.fetchTunnelUrl(apiUrl())

        assertNull(result, "fetchTunnelUrl must return null when tunnels array is empty")
    }

    // ── missing public_url key → null ─────────────────────────────────────────

    @Test
    fun fetchTunnelUrl_missingPublicUrlKey_returnsNull() {
        val json = """
            {
              "tunnels": [
                { "name": "command_line", "proto": "https" }
              ]
            }
        """.trimIndent()
        registerHandler("/api/tunnels", json)

        val result = poller.fetchTunnelUrl(apiUrl())

        assertNull(result, "fetchTunnelUrl must return null when public_url key is absent")
    }

    // ── network error (unreachable host) → null ───────────────────────────────

    @Test
    fun fetchTunnelUrl_networkError_returnsNull() {
        // Port 1 is typically unreachable and will fail immediately
        val result = poller.fetchTunnelUrl("http://localhost:1/api/tunnels")
        assertNull(result, "fetchTunnelUrl must return null on connection failure")
    }

    // ── malformed JSON → null ─────────────────────────────────────────────────

    @Test
    fun fetchTunnelUrl_malformedJson_returnsNull() {
        registerHandler("/api/tunnels", "NOT_JSON{{{{")

        val result = poller.fetchTunnelUrl(apiUrl())

        assertNull(result, "fetchTunnelUrl must return null when response is not valid JSON")
    }

    // ── blank public_url value → null ─────────────────────────────────────────

    @Test
    fun fetchTunnelUrl_blankPublicUrl_returnsNull() {
        val json = """
            {
              "tunnels": [
                { "public_url": "" }
              ]
            }
        """.trimIndent()
        registerHandler("/api/tunnels", json)

        val result = poller.fetchTunnelUrl(apiUrl())

        assertNull(result, "fetchTunnelUrl must return null when public_url is blank")
    }
}
