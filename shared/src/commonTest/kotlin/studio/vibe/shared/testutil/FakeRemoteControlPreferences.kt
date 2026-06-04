package studio.vibe.shared.testutil

import studio.vibe.shared.preferences.RemoteControlPreferencesWriting

class FakeRemoteControlPreferences(
    override var remoteControlEnabled: Boolean = false,
    override var remoteControlPort: Int = 7842,
    override var bindToLocalhost: Boolean = false,
    override var bonjourEnabled: Boolean = false,
    override var ngrokEnabled: Boolean = false,
    override var idleTimeoutMinutes: Int = 30,
    private var ngrokAuthtoken: String = "",
) : RemoteControlPreferencesWriting {

    override suspend fun loadNgrokAuthtoken(): String = ngrokAuthtoken

    override suspend fun saveNgrokAuthtoken(token: String) {
        ngrokAuthtoken = token
    }
}
