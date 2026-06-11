@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.session.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import studio.vibe.shared.feature.session.domain.model.AppSessionSnapshot
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.session.domain.model.SessionPersistenceError
import studio.vibe.shared.testutil.FakePersistenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SessionStoreImplTest {

    private val persistence = FakePersistenceStore(appSupportDir = FilePath("/support"))
    private val store = SessionStoreImpl(persistence)

    private fun freshSnapshot(
        activeProjectId: Uuid? = null,
    ) = AppSessionSnapshot(
        version = store.currentSnapshotVersion,
        capturedAt = Clock.System.now(),
        activeProjectId = activeProjectId,
        projectSessions = emptyList(),
    )

    // ── save() / restore() ────────────────────────────────────────────────────────

    @Test
    fun save_thenRestore_returnsEquivalentSnapshot() = runTest {
        val snap = freshSnapshot()

        store.save(snap)
        val restored = store.restore()

        assertNotNull(restored)
        assertEquals(snap.version, restored.version)
        assertEquals(snap.activeProjectId, restored.activeProjectId)
    }

    @Test
    fun restore_noFilePresent_returnsNull() = runTest {
        val result = store.restore()

        assertNull(result)
    }

    @Test
    fun restore_corruptedJson_throwsDecodingFailed() = runTest {
        // Write garbage bytes at the expected session file path
        val sessionFile = FilePath("${persistence.appSupportDirectory().path}/session.json")
        persistence.writeFile(sessionFile, "not-json-at-all".encodeToByteArray())

        assertFailsWith<SessionPersistenceError.DecodingFailed> {
            store.restore()
        }
    }

    @Test
    fun restore_incompatibleVersion_throwsIncompatibleVersion() = runTest {
        // Manually write a snapshot with version = 999
        val badSnap = AppSessionSnapshot(
            version = 999,
            capturedAt = Clock.System.now(),
            projectSessions = emptyList(),
        )
        val json = Json { prettyPrint = true }
        val sessionFile = FilePath("${persistence.appSupportDirectory().path}/session.json")
        persistence.writeFile(sessionFile, json.encodeToString(AppSessionSnapshot.serializer(), badSnap).encodeToByteArray())

        assertFailsWith<SessionPersistenceError.IncompatibleVersion> {
            store.restore()
        }
    }

    // ── saveScrollback() / loadScrollback() / deleteScrollback() ────────────────

    @Test
    fun saveScrollback_thenLoad_returnsContent() = runTest {
        val id = Uuid.random()

        store.saveScrollback("terminal output line\n", id)
        val content = store.loadScrollback(id)

        assertEquals("terminal output line\n", content)
    }

    @Test
    fun loadScrollback_noFile_returnsNull() = runTest {
        val result = store.loadScrollback(Uuid.random())

        assertNull(result)
    }

    @Test
    fun deleteScrollback_existingFile_removesIt() = runTest {
        val id = Uuid.random()
        store.saveScrollback("data", id)

        store.deleteScrollback(id)

        assertNull(store.loadScrollback(id))
    }

    @Test
    fun deleteScrollback_nonExistentFile_doesNotThrow() = runTest {
        // Should be idempotent
        store.deleteScrollback(Uuid.random())
    }

    // ── pruneOrphanedScrollbacks() ────────────────────────────────────────────────

    @Test
    fun pruneOrphanedScrollbacks_prunesFilesNotInActiveSet() = runTest {
        val keepId = Uuid.random()
        val orphanId = Uuid.random()

        store.saveScrollback("keep", keepId)
        store.saveScrollback("orphan", orphanId)

        val pruned = store.pruneOrphanedScrollbacks(activeSessionIds = setOf(keepId))

        assertEquals(1, pruned)
        assertNotNull(store.loadScrollback(keepId))
        assertNull(store.loadScrollback(orphanId))
    }

    @Test
    fun pruneOrphanedScrollbacks_noScrollbackDirectory_returnsZero() = runTest {
        val result = store.pruneOrphanedScrollbacks(emptySet())

        assertEquals(0, result)
    }

    @Test
    fun pruneOrphanedScrollbacks_allActive_prunesNothing() = runTest {
        val id1 = Uuid.random()
        val id2 = Uuid.random()
        store.saveScrollback("a", id1)
        store.saveScrollback("b", id2)

        val pruned = store.pruneOrphanedScrollbacks(setOf(id1, id2))

        assertEquals(0, pruned)
    }
}
