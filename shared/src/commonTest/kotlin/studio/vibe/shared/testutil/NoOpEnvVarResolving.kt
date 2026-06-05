package studio.vibe.shared.testutil

import studio.vibe.shared.contract.EnvVarResolving

/**
 * Test double for [EnvVarResolving].
 *
 * Returns values from the [env] map; returns `null` for unknown variables.
 */
class NoOpEnvVarResolving(private val env: Map<String, String> = emptyMap()) : EnvVarResolving {
    override fun resolve(name: String): String? = env[name]
}
