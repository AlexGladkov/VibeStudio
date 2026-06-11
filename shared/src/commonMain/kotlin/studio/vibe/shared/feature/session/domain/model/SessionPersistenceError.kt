package studio.vibe.shared.feature.session.domain.model

import studio.vibe.shared.core.common.FilePath
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
sealed class SessionPersistenceError(override val message: String) : Exception(message) {
    data class EncodingFailed(val underlying: Throwable) : SessionPersistenceError("Session encoding failed: ${underlying.message}")
    data class DecodingFailed(val underlying: Throwable) : SessionPersistenceError("Session decoding failed: ${underlying.message}")
    data class SnapshotCorrupted(val path: FilePath) : SessionPersistenceError("Session snapshot corrupted: ${path.path}")
    data class IncompatibleVersion(val found: Int, val expected: Int) : SessionPersistenceError("Snapshot version $found is incompatible (expected $expected)")
    data class ScrollbackWriteFailed(val sessionId: Uuid, val underlying: Throwable) : SessionPersistenceError("Scrollback write failed for session $sessionId: ${underlying.message}")
}
