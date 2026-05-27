package studio.vibe.shared.service.persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.contract.SessionPersisting
import studio.vibe.shared.model.AppSessionSnapshot
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.SessionPersistenceError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SessionStoreImpl(
    private val persistence: PersistenceStore,
    private val json: Json = Json { prettyPrint = true },
) : SessionPersisting {

    override val storageDirectory: FilePath
        get() = persistence.appSupportDirectory()

    override val currentSnapshotVersion: Int
        get() = SNAPSHOT_VERSION

    private val sessionFile: FilePath
        get() = FilePath("${storageDirectory.path}/session.json")

    private val scrollbackDirectory: FilePath
        get() = FilePath("${storageDirectory.path}/scrollback")

    override suspend fun save(snapshot: AppSessionSnapshot) {
        val text = try {
            json.encodeToString(snapshot)
        } catch (e: Exception) {
            throw SessionPersistenceError.EncodingFailed(e)
        }
        persistence.writeFile(sessionFile, text.encodeToByteArray())
    }

    override suspend fun restore(): AppSessionSnapshot? {
        val bytes = persistence.readFile(sessionFile) ?: return null
        val text = bytes.decodeToString()

        val snapshot = try {
            json.decodeFromString<AppSessionSnapshot>(text)
        } catch (e: Exception) {
            throw SessionPersistenceError.DecodingFailed(e)
        }

        if (snapshot.version != SNAPSHOT_VERSION) {
            throw SessionPersistenceError.IncompatibleVersion(
                found = snapshot.version,
                expected = SNAPSHOT_VERSION,
            )
        }

        return snapshot
    }

    override suspend fun clear() {
        if (persistence.fileExists(sessionFile)) {
            persistence.deleteFile(sessionFile)
        }
    }

    override suspend fun saveScrollback(content: String, sessionId: Uuid) {
        ensureScrollbackDirectory()
        val file = scrollbackFile(sessionId)
        try {
            persistence.writeFile(file, content.encodeToByteArray())
        } catch (e: Exception) {
            throw SessionPersistenceError.ScrollbackWriteFailed(sessionId, e)
        }
    }

    override suspend fun loadScrollback(sessionId: Uuid): String? {
        val file = scrollbackFile(sessionId)
        val bytes = persistence.readFile(file) ?: return null
        return bytes.decodeToString()
    }

    override suspend fun deleteScrollback(sessionId: Uuid) {
        val file = scrollbackFile(sessionId)
        if (persistence.fileExists(file)) {
            persistence.deleteFile(file)
        }
    }

    override suspend fun pruneOrphanedScrollbacks(activeSessionIds: Set<Uuid>): Int {
        if (!persistence.fileExists(scrollbackDirectory)) return 0

        val activeFileNames = activeSessionIds.map { "${it}.txt" }.toSet()
        val allFiles = persistence.listDirectory(scrollbackDirectory)

        var pruned = 0
        for (filePath in allFiles) {
            if (filePath.name !in activeFileNames) {
                persistence.deleteFile(filePath)
                pruned++
            }
        }
        return pruned
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun ensureScrollbackDirectory() {
        if (!persistence.fileExists(scrollbackDirectory)) {
            persistence.createDirectory(scrollbackDirectory)
        }
    }

    private fun scrollbackFile(sessionId: Uuid): FilePath =
        FilePath("${scrollbackDirectory.path}/${sessionId}.txt")

    companion object {
        private const val SNAPSHOT_VERSION = 1
    }
}
