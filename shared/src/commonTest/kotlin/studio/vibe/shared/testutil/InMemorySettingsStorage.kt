package studio.vibe.shared.testutil

import studio.vibe.shared.contract.SettingsStorage

class InMemorySettingsStorage : SettingsStorage {
    private val strings = mutableMapOf<String, String>()
    private val bools = mutableMapOf<String, Boolean>()
    private val ints = mutableMapOf<String, Int>()

    override fun getString(key: String): String? = strings[key]
    override fun setString(key: String, value: String) { strings[key] = value }

    override fun getBool(key: String): Boolean = bools[key] ?: false
    override fun setBool(key: String, value: Boolean) { bools[key] = value }

    override fun getInt(key: String): Int? = ints[key]
    override fun setInt(key: String, value: Int) { ints[key] = value }

    override fun remove(key: String) {
        strings.remove(key)
        bools.remove(key)
        ints.remove(key)
    }
}
