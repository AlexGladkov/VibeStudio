package studio.vibe.shared.contract

interface APIKeyResolving {
    fun resolve(envVar: String): String?
}
