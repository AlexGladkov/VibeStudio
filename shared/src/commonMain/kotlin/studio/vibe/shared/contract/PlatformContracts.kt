package studio.vibe.shared.contract

import kotlinx.coroutines.flow.Flow
import studio.vibe.shared.model.FileChangeEvent
import studio.vibe.shared.model.FilePath
import kotlin.time.Duration

// Process execution result
data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

// Process Runner — wraps shell/CLI execution
interface ProcessRunner {
    suspend fun run(
        command: List<String>,
        workDir: FilePath,
        env: Map<String, String> = emptyMap(),
        timeout: Duration = Duration.INFINITE,
    ): ProcessResult

    fun stream(
        command: List<String>,
        workDir: FilePath,
        env: Map<String, String> = emptyMap(),
    ): Flow<String>
}

// File System Watcher
interface FileSystemWatcher {
    fun watch(directory: FilePath): Flow<FileChangeEvent>
    fun stop()
}

// Secure Credential Storage (Keychain / DPAPI / libsecret)
interface CredentialStorage {
    suspend fun save(account: String, value: String)
    suspend fun load(account: String): String?
    suspend fun delete(account: String)
}

// File I/O abstraction
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

// Binary resolution (which / where.exe)
interface BinaryResolver {
    fun findExecutable(name: String): FilePath?
}

// Settings/Preferences storage (UserDefaults / java.util.prefs)
interface SettingsStorage {
    fun getString(key: String): String?
    fun setString(key: String, value: String)
    fun getBool(key: String): Boolean
    fun setBool(key: String, value: Boolean)
    fun getInt(key: String): Int?
    fun setInt(key: String, value: Int)
    fun remove(key: String)
}
