package studio.vibe.shared.core.common

interface APIKeyResolving {
    fun resolve(envVar: String): String?
}
