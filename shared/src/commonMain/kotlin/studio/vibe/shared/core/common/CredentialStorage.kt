package studio.vibe.shared.core.common

interface CredentialStorage {
    suspend fun save(account: String, value: String)
    suspend fun load(account: String): String?
    suspend fun delete(account: String)
}
