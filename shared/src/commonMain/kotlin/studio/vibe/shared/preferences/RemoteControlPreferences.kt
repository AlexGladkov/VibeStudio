package studio.vibe.shared.preferences

import studio.vibe.shared.contract.CredentialStorage
import studio.vibe.shared.contract.SettingsStorage

/**
 * Key-value backed preferences for the embedded Remote Control server.
 *
 * Keys are prefixed with `vs_remote_` to avoid collisions with other services.
 * Backed by [SettingsStorage] for regular preferences and [CredentialStorage]
 * for the ngrok authtoken (secure storage — Keychain on macOS/iOS, Android Keystore, etc.).
 */
class RemoteControlPreferences(
    private val storage: SettingsStorage,
    private val credentialStorage: CredentialStorage,
) : RemoteControlPreferencesWriting {
    private object Keys {
        const val ENABLED = "vs_remote_control_enabled"
        const val PORT = "vs_remote_control_port"
        const val BIND_TO_LOCALHOST = "vs_remote_bind_localhost"
        const val BONJOUR_ENABLED = "vs_remote_bonjour_enabled"
        const val IDLE_TIMEOUT_MINUTES = "vs_remote_idle_timeout"
        const val NGROK_ENABLED = "vs_remote_ngrok_enabled"
    }

    private object CredentialKeys {
        const val NGROK_AUTHTOKEN = "vs_ngrok_authtoken"
    }

    /**
     * Whether the Remote Control HTTP/WS server is enabled. Default: `false`.
     *
     * SECURITY: Opt-in only — disabled on first launch.
     */
    override var remoteControlEnabled: Boolean
        get() = storage.getString(Keys.ENABLED)?.toBooleanStrictOrNull() ?: false
        set(value) { storage.setBool(Keys.ENABLED, value) }

    /**
     * TCP port for the HTTP/WS server. Default: `7842`. Range: 1024..65535.
     */
    override var remoteControlPort: Int
        get() = storage.getInt(Keys.PORT) ?: 7842
        set(value) {
            val clamped = value.coerceIn(1024, 65535)
            storage.setInt(Keys.PORT, clamped)
        }

    /**
     * Bind the server to localhost only (127.0.0.1). Default: `false`.
     *
     * SECURITY: Default `false` — LAN access enabled for phone control use case.
     * When `true`, only local connections and SSH tunnels can reach the server.
     */
    override var bindToLocalhost: Boolean
        get() = storage.getString(Keys.BIND_TO_LOCALHOST)?.toBooleanStrictOrNull() ?: false
        set(value) { storage.setBool(Keys.BIND_TO_LOCALHOST, value) }

    /**
     * Whether Bonjour (`_vibestudio._tcp`) service advertisement is enabled. Default: `false`.
     *
     * SECURITY: Off by default. Bonjour broadcasts the server's presence on the local
     * network. Only enable if [bindToLocalhost] is also `false`.
     */
    override var bonjourEnabled: Boolean
        get() = storage.getString(Keys.BONJOUR_ENABLED)?.toBooleanStrictOrNull() ?: false
        set(value) { storage.setBool(Keys.BONJOUR_ENABLED, value) }

    /**
     * Whether ngrok tunnel is enabled for remote access outside the LAN. Default: `false`.
     *
     * SECURITY: Off by default. When enabled, the local server is exposed to the internet
     * via ngrok. PIN authentication is still required.
     */
    override var ngrokEnabled: Boolean
        get() = storage.getString(Keys.NGROK_ENABLED)?.toBooleanStrictOrNull() ?: false
        set(value) { storage.setBool(Keys.NGROK_ENABLED, value) }

    /**
     * Idle timeout in minutes before disconnecting inactive remote clients. Default: `30`.
     */
    override var idleTimeoutMinutes: Int
        get() = storage.getInt(Keys.IDLE_TIMEOUT_MINUTES) ?: 30
        set(value) {
            val clamped = maxOf(value, 1)
            storage.setInt(Keys.IDLE_TIMEOUT_MINUTES, clamped)
        }

    /**
     * Load the ngrok authtoken from secure storage. Returns empty string if not set.
     *
     * This is a suspend function because secure storage access may be asynchronous.
     */
    override suspend fun loadNgrokAuthtoken(): String =
        credentialStorage.load(CredentialKeys.NGROK_AUTHTOKEN) ?: ""

    /**
     * Persist the ngrok authtoken to secure storage.
     */
    override suspend fun saveNgrokAuthtoken(token: String) {
        credentialStorage.save(CredentialKeys.NGROK_AUTHTOKEN, token)
    }
}
