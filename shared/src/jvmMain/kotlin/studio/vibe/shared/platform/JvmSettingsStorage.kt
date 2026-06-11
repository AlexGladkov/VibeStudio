package studio.vibe.shared.platform

import studio.vibe.shared.core.common.SettingsStorage
import java.util.prefs.Preferences

class JvmSettingsStorage : SettingsStorage {

    private val prefs: Preferences =
        Preferences.userNodeForPackage(JvmSettingsStorage::class.java)

    override fun getString(key: String): String? {
        val value = prefs.get(key, null)
        return value
    }

    override fun setString(key: String, value: String) {
        prefs.put(key, value)
    }

    override fun getBool(key: String): Boolean {
        return prefs.getBoolean(key, false)
    }

    override fun setBool(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }

    override fun getInt(key: String): Int? {
        // Preferences has no "optional int" — use a sentinel to distinguish "not set" from 0
        val sentinel = Int.MIN_VALUE
        val value = prefs.getInt(key, sentinel)
        return if (value == sentinel) null else value
    }

    override fun setInt(key: String, value: Int) {
        prefs.putInt(key, value)
    }

    override fun remove(key: String) {
        prefs.remove(key)
    }
}
