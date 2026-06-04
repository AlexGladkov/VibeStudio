package studio.vibe.shared.testutil

import studio.vibe.shared.contract.APIKeyResolving

/** Map-backed [APIKeyResolving]. */
class FakeAPIKeyResolving(
    private val keys: MutableMap<String, String> = mutableMapOf(),
) : APIKeyResolving {

    override fun resolve(envVar: String): String? = keys[envVar]

    operator fun set(envVar: String, value: String) {
        keys[envVar] = value
    }
}
