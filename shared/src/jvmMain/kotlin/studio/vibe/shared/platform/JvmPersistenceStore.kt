package studio.vibe.shared.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.vibe.shared.core.common.PersistenceStore
import studio.vibe.shared.core.common.FilePath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

class JvmPersistenceStore : PersistenceStore {

    override suspend fun readFile(path: FilePath): ByteArray? = withContext(Dispatchers.IO) {
        val p = Path.of(path.path)
        if (Files.exists(p) && p.isRegularFile()) {
            Files.readAllBytes(p)
        } else {
            null
        }
    }

    override suspend fun writeFile(path: FilePath, data: ByteArray) = withContext(Dispatchers.IO) {
        val p = Path.of(path.path)
        // Ensure parent directories exist
        p.parent?.let { Files.createDirectories(it) }
        Files.write(p, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        Unit
    }

    override suspend fun fileExists(path: FilePath): Boolean = withContext(Dispatchers.IO) {
        Files.exists(Path.of(path.path))
    }

    override suspend fun isDirectory(path: FilePath): Boolean = withContext(Dispatchers.IO) {
        Path.of(path.path).isDirectory()
    }

    override suspend fun createDirectory(path: FilePath) = withContext(Dispatchers.IO) {
        Files.createDirectories(Path.of(path.path))
        Unit
    }

    override suspend fun listDirectory(path: FilePath): List<FilePath> = withContext(Dispatchers.IO) {
        val p = Path.of(path.path)
        if (!Files.isDirectory(p)) return@withContext emptyList()
        Files.list(p).use { stream ->
            stream.map { FilePath(it.toString()) }.toList()
        }
    }

    override suspend fun deleteFile(path: FilePath) = withContext(Dispatchers.IO) {
        Files.deleteIfExists(Path.of(path.path))
        Unit
    }

    override fun appSupportDirectory(): FilePath {
        val os = System.getProperty("os.name", "").lowercase()
        val appName = "VibeStudio"

        return when {
            os.contains("mac") -> {
                val home = System.getProperty("user.home")
                FilePath("$home/Library/Application Support/$appName")
            }
            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
                FilePath("$appData/$appName")
            }
            else -> {
                // Linux / other Unix — follow XDG Base Directory spec
                val xdgDataHome = System.getenv("XDG_DATA_HOME")
                if (!xdgDataHome.isNullOrBlank()) {
                    FilePath("$xdgDataHome/$appName")
                } else {
                    val home = System.getProperty("user.home")
                    FilePath("$home/.local/share/$appName")
                }
            }
        }
    }
}
