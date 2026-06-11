package studio.vibe.shared.platform

import platform.Foundation.NSUserDefaults
import studio.vibe.shared.core.common.SettingsStorage

class MacosSettingsStorage : SettingsStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? =
        defaults.stringForKey(key)

    override fun setString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override fun getBool(key: String): Boolean =
        defaults.boolForKey(key)

    override fun setBool(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }

    override fun getInt(key: String): Int? {
        // NSUserDefaults returns 0 for keys that don't exist;
        // check for key presence first to distinguish 0 from "not set".
        return if (defaults.objectForKey(key) != null) {
            defaults.integerForKey(key).toInt()
        } else {
            null
        }
    }

    override fun setInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), forKey = key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
