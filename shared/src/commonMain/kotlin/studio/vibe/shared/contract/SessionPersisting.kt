package studio.vibe.shared.contract

import studio.vibe.shared.model.AppSessionSnapshot
import studio.vibe.shared.model.FilePath
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface SessionPersisting {
    // Snapshot
    suspend fun save(snapshot: AppSessionSnapshot)
    suspend fun restore(): AppSessionSnapshot?
    suspend fun clear()

    // Scrollback
    suspend fun saveScrollback(content: String, sessionId: Uuid)
    suspend fun loadScrollback(sessionId: Uuid): String?
    suspend fun deleteScrollback(sessionId: Uuid)
    suspend fun pruneOrphanedScrollbacks(activeSessionIds: Set<Uuid>): Int

    // Meta
    val storageDirectory: FilePath
    val currentSnapshotVersion: Int
}
