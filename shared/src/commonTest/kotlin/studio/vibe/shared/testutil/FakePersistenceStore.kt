package studio.vibe.shared.testutil

import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.model.FilePath

/**
 * In-memory [PersistenceStore] test double.
 *
 * Stores files in a [MutableMap<String, ByteArray>] keyed on [FilePath.path].
 * Directories are tracked in a separate [MutableSet].
 *
 * All methods are fully synchronous (no actual I/O), making tests deterministic.
 */
class FakePersistenceStore(
    private val appSupportDir: FilePath = FilePath("/fake/app-support"),
) : PersistenceStore {

    private val files = mutableMapOf<String, ByteArray>()
    private val directories = mutableSetOf(appSupportDir.path)

    override suspend fun readFile(path: FilePath): ByteArray? = files[path.path]

    override suspend fun writeFile(path: FilePath, data: ByteArray) {
        files[path.path] = data
    }

    override suspend fun fileExists(path: FilePath): Boolean = files.containsKey(path.path)

    override suspend fun isDirectory(path: FilePath): Boolean = directories.contains(path.path)

    override suspend fun createDirectory(path: FilePath) {
        directories.add(path.path)
    }

    override suspend fun listDirectory(path: FilePath): List<FilePath> {
        val prefix = path.path + "/"
        // Immediate file children: entries in the files map whose path starts with
        // the prefix and has no further '/' after the prefix.
        val fileChildren = files.keys
            .filter { it.startsWith(prefix) && !it.removePrefix(prefix).contains("/") }
            .map { FilePath(it) }
        // Immediate directory children: entries in the directories set whose path
        // starts with the prefix and has no further '/' after the prefix.
        val dirChildren = directories
            .filter { it.startsWith(prefix) && !it.removePrefix(prefix).contains("/") }
            .map { FilePath(it) }
        return (fileChildren + dirChildren).distinctBy { it.path }
    }

    override suspend fun deleteFile(path: FilePath) {
        files.remove(path.path)
    }

    override fun appSupportDirectory(): FilePath = appSupportDir

    // ── Test helpers ──────────────────────────────────────────────────────────

    /** Register an additional path as a known directory. */
    fun addDirectory(path: FilePath) {
        directories.add(path.path)
    }

    /** Clear all in-memory state for test isolation. */
    fun reset() {
        files.clear()
        directories.clear()
        directories.add(appSupportDir.path)
    }
}
