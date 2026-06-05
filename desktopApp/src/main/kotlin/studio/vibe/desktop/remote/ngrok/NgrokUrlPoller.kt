package studio.vibe.desktop.remote.ngrok

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Logger

/**
 * Polls the ngrok local API at `http://localhost:4040/api/tunnels` to obtain
 * the public tunnel URL after the subprocess starts.
 *
 * Polling is done by the caller (NgrokTunnelService) in a coroutine loop;
 * this class provides only the single-attempt HTTP fetch, keeping it testable.
 */
internal class NgrokUrlPoller {

    companion object {
        private val log = Logger.getLogger("NgrokUrlPoller")

        /** Default local ngrok API endpoint. */
        const val DEFAULT_API_URL = "http://localhost:4040/api/tunnels"
        private const val CONNECT_TIMEOUT_MS = 1_500
        private const val READ_TIMEOUT_MS = 1_500
    }

    /**
     * Attempt a single poll of the ngrok local API.
     *
     * @param apiUrl Override for the default API endpoint (useful in tests).
     * @return The first tunnel's `public_url` value, or null if not yet available
     *   or on any network/parse error.
     */
    fun fetchTunnelUrl(apiUrl: String = DEFAULT_API_URL): String? {
        val conn = URL(apiUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        return try {
            val body = conn.inputStream.bufferedReader().readText()
            val root = JSONObject(body)
            val tunnels = root.optJSONArray("tunnels") ?: return null
            if (tunnels.length() == 0) return null
            val first = tunnels.getJSONObject(0)
            first.optString("public_url").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
