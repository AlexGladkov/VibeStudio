package studio.vibe.desktop.platform

import studio.vibe.shared.core.common.EnvVarResolving

/** JVM singleton — reads environment variables from the process environment. */
internal object JvmEnvVarResolving : EnvVarResolving {
    override fun resolve(name: String): String? = System.getenv(name)
}
