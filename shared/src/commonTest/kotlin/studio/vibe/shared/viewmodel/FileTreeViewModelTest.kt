@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package studio.vibe.shared.feature.filetree.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.feature.filetree.domain.FileSystemWatchingService
import studio.vibe.shared.core.common.PersistenceStore
import studio.vibe.shared.feature.filetree.domain.WatchInfo
import studio.vibe.shared.feature.filetree.domain.WatchOptions
import studio.vibe.shared.feature.filetree.domain.WatchToken
import studio.vibe.shared.core.common.FileChangeEvent
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.testutil.FakePersistenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileTreeViewModelTest {

    private class FakeWatchService : FileSystemWatchingService {
        private val _events = MutableSharedFlow<FileChangeEvent>(extraBufferCapacity = 16)
        override val events: kotlinx.coroutines.flow.Flow<FileChangeEvent> = _events
        override val activeWatches: List<WatchInfo> = emptyList()
        var watchCallCount = 0
        var unwatchCallCount = 0

        override fun watch(directory: FilePath, options: WatchOptions): WatchToken {
            watchCallCount++
            return WatchToken()
        }

        override fun unwatch(token: WatchToken) { unwatchCallCount++ }
        override fun unwatchAll() {}
    }

    /** Persistence stub that throws on listDirectory */
    private class ThrowingPersistenceStore : PersistenceStore {
        override suspend fun readFile(path: FilePath): ByteArray? = null
        override suspend fun writeFile(path: FilePath, data: ByteArray) {}
        override suspend fun fileExists(path: FilePath): Boolean = false
        override suspend fun isDirectory(path: FilePath): Boolean = throw RuntimeException("permission denied")
        override suspend fun createDirectory(path: FilePath) {}
        override suspend fun listDirectory(path: FilePath): List<FilePath> = throw RuntimeException("permission denied")
        override suspend fun deleteFile(path: FilePath) {}
        override fun appSupportDirectory(): FilePath = FilePath("/support")
    }

    private fun buildVm(
        persistence: FakePersistenceStore = FakePersistenceStore(),
        watchService: FakeWatchService = FakeWatchService(),
    ): FileTreeViewModel {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        return FileTreeViewModel(persistence, watchService, scope)
    }

    // ── loadTree() ────────────────────────────────────────────────────────────────

    @Test
    fun loadTree_emptyDirectory_stateHasEmptyNodes() = runTest {
        val persistence = FakePersistenceStore()
        persistence.addDirectory(FilePath("/project"))
        val vm = buildVm(persistence)

        vm.loadTree(FilePath("/project"))

        assertEquals(emptyList(), vm.state.value.nodes)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun loadTree_persistenceThrows_gracefullyReturnsEmptyNodes() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val vm = FileTreeViewModel(ThrowingPersistenceStore(), FakeWatchService(), scope)

        vm.loadTree(FilePath("/forbidden"))

        // FileTreeViewModel.buildTree wraps listDirectory and isDirectory in runCatching internally,
        // so persistence errors are silently degraded to empty lists rather than propagated as errorMessage.
        assertTrue(vm.state.value.nodes.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }

    // ── toggleExpanded() idempotency ──────────────────────────────────────────────

    @Test
    fun toggleExpanded_expandsThenCollapses_idempotent() = runTest {
        val persistence = FakePersistenceStore()
        persistence.addDirectory(FilePath("/project"))
        val vm = buildVm(persistence)
        vm.loadTree(FilePath("/project"))

        vm.toggleExpanded("/project/subdir")
        assertTrue("/project/subdir" in vm.state.value.expandedPaths)

        vm.toggleExpanded("/project/subdir")
        assertFalse("/project/subdir" in vm.state.value.expandedPaths)
    }

    // ── startWatching() / stopWatching() ─────────────────────────────────────────

    @Test
    fun startWatching_callsWatchOnService() = runTest {
        val watchService = FakeWatchService()
        val vm = buildVm(watchService = watchService)

        vm.startWatching(FilePath("/project"))

        assertEquals(1, watchService.watchCallCount)
    }

    @Test
    fun stopWatching_afterStartWatching_callsUnwatch() = runTest {
        val watchService = FakeWatchService()
        val vm = buildVm(watchService = watchService)
        vm.startWatching(FilePath("/project"))

        vm.stopWatching()

        assertEquals(1, watchService.unwatchCallCount)
    }

    @Test
    fun startWatching_calledTwice_cancelsFirstToken() = runTest {
        val watchService = FakeWatchService()
        val vm = buildVm(watchService = watchService)

        vm.startWatching(FilePath("/project"))
        vm.startWatching(FilePath("/project"))

        assertEquals(1, watchService.unwatchCallCount)
        assertEquals(2, watchService.watchCallCount)
    }

    // ── refresh() ────────────────────────────────────────────────────────────────

    @Test
    fun refresh_withCurrentRootPath_reloadsTree() = runTest {
        val persistence = FakePersistenceStore()
        persistence.addDirectory(FilePath("/project"))
        val vm = buildVm(persistence)
        vm.loadTree(FilePath("/project"))

        vm.refresh()

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun refresh_withoutPriorLoad_doesNothing() = runTest {
        val vm = buildVm()

        vm.refresh()

        assertTrue(vm.state.value.nodes.isEmpty())
    }
}
