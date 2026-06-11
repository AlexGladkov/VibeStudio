package studio.vibe.shared.feature.session.domain.contract

import kotlin.coroutines.cancellation.CancellationException
import studio.vibe.shared.feature.session.domain.model.AppSessionSnapshot
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.session.domain.model.SessionPersistenceError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface SessionPersisting {
    // Snapshot

    /** @throws SessionPersistenceError.EncodingFailed on serialisation failure. */
    @Throws(SessionPersistenceError::class, CancellationException::class)
    suspend fun save(snapshot: AppSessionSnapshot)

    /**
     * @throws SessionPersistenceError.DecodingFailed on deserialisation failure.
     * @throws SessionPersistenceError.IncompatibleVersion if the stored format is too old.
     */
    @Throws(SessionPersistenceError::class, CancellationException::class)
    suspend fun restore(): AppSessionSnapshot?

    /** Silently swallows I/O errors; does not throw. */
    suspend fun clear()

    // Scrollback

    /** @throws SessionPersistenceError.ScrollbackWriteFailed on I/O failure. */
    @Throws(SessionPersistenceError::class, CancellationException::class)
    suspend fun saveScrollback(content: String, sessionId: Uuid)

    /** Returns null if not found; does not throw. */
    suspend fun loadScrollback(sessionId: Uuid): String?

    /** Silently swallows I/O errors; does not throw. */
    suspend fun deleteScrollback(sessionId: Uuid)

    /** @return number of orphaned scrollback files deleted. */
    suspend fun pruneOrphanedScrollbacks(activeSessionIds: Set<Uuid>): Int

    // Meta
    val storageDirectory: FilePath
    val currentSnapshotVersion: Int
}
