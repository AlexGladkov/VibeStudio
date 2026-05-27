@file:OptIn(ExperimentalForeignApi::class)

package studio.vibe.shared.platform

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.getenv
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.model.FilePath

class MacosPersistenceStore : PersistenceStore {

    private val fileManager = NSFileManager.defaultManager

    override suspend fun readFile(path: FilePath): ByteArray? = withContext(Dispatchers.Default) {
        val fp = fopen(path.path, "rb") ?: return@withContext null
        try {
            fseek(fp, 0, SEEK_END)
            val size = ftell(fp).toInt()
            fseek(fp, 0, SEEK_SET)
            if (size <= 0) return@withContext ByteArray(0)

            val buf = ByteArray(size)
            buf.usePinned { pinned ->
                fread(pinned.addressOf(0), 1u, size.toULong(), fp)
            }
            buf
        } finally {
            fclose(fp)
        }
    }

    override suspend fun writeFile(path: FilePath, data: ByteArray): Unit = withContext(Dispatchers.Default) {
        val fp = fopen(path.path, "wb")
            ?: throw RuntimeException("Cannot open file for writing: ${path.path}")
        try {
            if (data.isNotEmpty()) {
                data.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, data.size.toULong(), fp)
                }
            }
        } finally {
            fclose(fp)
        }
    }

    override suspend fun fileExists(path: FilePath): Boolean = withContext(Dispatchers.Default) {
        fileManager.fileExistsAtPath(path.path)
    }

    override suspend fun isDirectory(path: FilePath): Boolean = withContext(Dispatchers.Default) {
        memScoped {
            val isDirVar = alloc<BooleanVar>()
            val exists = fileManager.fileExistsAtPath(path.path, isDirectory = isDirVar.ptr)
            exists && isDirVar.value
        }
    }

    override suspend fun createDirectory(path: FilePath): Unit = withContext(Dispatchers.Default) {
        fileManager.createDirectoryAtPath(
            path = path.path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        Unit
    }

    override suspend fun listDirectory(path: FilePath): List<FilePath> = withContext(Dispatchers.Default) {
        @Suppress("UNCHECKED_CAST")
        val contents = fileManager.contentsOfDirectoryAtPath(
            path = path.path,
            error = null,
        ) as? List<String> ?: return@withContext emptyList()

        contents.map { name -> path.child(name) }
    }

    override suspend fun deleteFile(path: FilePath): Unit = withContext(Dispatchers.Default) {
        fileManager.removeItemAtPath(path = path.path, error = null)
        Unit
    }

    override fun appSupportDirectory(): FilePath {
        @Suppress("UNCHECKED_CAST")
        val paths = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ) as List<String>

        val appSupportPath = paths.firstOrNull()
            ?: run {
                val home = getenv("HOME")?.toKString() ?: "/tmp"
                "$home/Library/Application Support"
            }

        return FilePath("$appSupportPath/VibeStudio")
    }
}
