package studio.vibe.shared.core.common

interface PersistenceStore {
    suspend fun readFile(path: FilePath): ByteArray?
    suspend fun writeFile(path: FilePath, data: ByteArray)
    suspend fun fileExists(path: FilePath): Boolean
    suspend fun isDirectory(path: FilePath): Boolean
    suspend fun createDirectory(path: FilePath)
    suspend fun listDirectory(path: FilePath): List<FilePath>
    suspend fun deleteFile(path: FilePath)
    fun appSupportDirectory(): FilePath
}
