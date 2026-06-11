package studio.vibe.shared.feature.settings.domain.model

/**
 * Abstracts Remote Control server preferences.
 *
 * Consumers (RemoteControlServer, tests) depend on this interface
 * rather than the concrete [RemoteControlPreferences] class.
 */
interface RemoteControlPreferencesReading {
    val remoteControlEnabled: Boolean
    val remoteControlPort: Int
    val bindToLocalhost: Boolean
    val bonjourEnabled: Boolean
    val ngrokEnabled: Boolean
    val idleTimeoutMinutes: Int
    suspend fun loadNgrokAuthtoken(): String
}

interface RemoteControlPreferencesWriting : RemoteControlPreferencesReading {
    override var remoteControlEnabled: Boolean
    override var remoteControlPort: Int
    override var bindToLocalhost: Boolean
    override var bonjourEnabled: Boolean
    override var ngrokEnabled: Boolean
    override var idleTimeoutMinutes: Int
    suspend fun saveNgrokAuthtoken(token: String)
}
