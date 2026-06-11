package studio.vibe.desktop.platform

import studio.vibe.shared.core.common.APIKeyResolving

/**
 * Resolves API keys from process environment variables.
 * Replaces NSProcessInfo.processInfo.environment on JVM.
 */
internal class EnvironmentAPIKeyResolver : APIKeyResolving {
    override fun resolve(envVar: String): String? = System.getenv(envVar)
}
