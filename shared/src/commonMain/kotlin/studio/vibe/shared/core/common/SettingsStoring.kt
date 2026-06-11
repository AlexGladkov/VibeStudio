package studio.vibe.shared.core.common

interface SettingsStorage {
    fun getString(key: String): String?
    fun setString(key: String, value: String)
    fun getBool(key: String): Boolean
    fun setBool(key: String, value: Boolean)
    fun getInt(key: String): Int?
    fun setInt(key: String, value: Int)
    fun remove(key: String)
}
