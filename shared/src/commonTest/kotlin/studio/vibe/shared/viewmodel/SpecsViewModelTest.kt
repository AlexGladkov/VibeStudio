@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.codespeak.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.PersistenceStore
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.testutil.FakePersistenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpecsViewModelTest {

    /** Persistence stub that throws on isDirectory to simulate errors */
    private class ThrowingIsDirectoryStore : PersistenceStore {
        override suspend fun readFile(path: FilePath): ByteArray? = null
        override suspend fun writeFile(path: FilePath, data: ByteArray) {}
        override suspend fun fileExists(path: FilePath): Boolean = false
        override suspend fun isDirectory(path: FilePath): Boolean = throw RuntimeException("disk error")
        override suspend fun createDirectory(path: FilePath) {}
        override suspend fun listDirectory(path: FilePath): List<FilePath> = emptyList()
        override suspend fun deleteFile(path: FilePath) {}
        override fun appSupportDirectory(): FilePath = FilePath("/support")
    }

    private fun buildVm(persistence: FakePersistenceStore = FakePersistenceStore()): Pair<SpecsViewModel, FakePersistenceStore> {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        return SpecsViewModel(persistenceStore = persistence, parentScope = scope) to persistence
    }

    // ── loadSpecs() ───────────────────────────────────────────────────────────────

    @Test
    fun loadSpecs_noSpecsDirectory_returnsEmptyList() = runTest {
        val (vm, _) = buildVm()

        vm.loadSpecs(FilePath("/project"))

        assertTrue(vm.state.value.specs.isEmpty())
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun loadSpecs_codepeakSpecsDir_loadsAllMdFiles() = runTest {
        val (vm, persistence) = buildVm()
        persistence.addDirectory(FilePath("/project/.codespeak/specs"))
        persistence.writeFile(FilePath("/project/.codespeak/specs/auth.cs.md"), ByteArray(0))
        persistence.writeFile(FilePath("/project/.codespeak/specs/profile.md"), ByteArray(0))
        persistence.writeFile(FilePath("/project/.codespeak/specs/ignored.txt"), ByteArray(0))

        vm.loadSpecs(FilePath("/project"))

        val specs = vm.state.value.specs
        assertEquals(2, specs.size)
        assertTrue(specs.all { it.path.path.endsWith(".md") || it.path.path.endsWith(".cs.md") })
    }

    @Test
    fun loadSpecs_rootSpecsDir_usedAsFallback() = runTest {
        val (vm, persistence) = buildVm()
        persistence.addDirectory(FilePath("/project/specs"))
        persistence.writeFile(FilePath("/project/specs/api.md"), ByteArray(0))

        vm.loadSpecs(FilePath("/project"))

        assertEquals(1, vm.state.value.specs.size)
    }

    @Test
    fun loadSpecs_mixedFiles_onlyIncludesMdAndCsMd() = runTest {
        val (vm, persistence) = buildVm()
        persistence.addDirectory(FilePath("/project/.codespeak/specs"))
        persistence.writeFile(FilePath("/project/.codespeak/specs/spec.cs.md"), ByteArray(0))
        persistence.writeFile(FilePath("/project/.codespeak/specs/doc.md"), ByteArray(0))
        persistence.writeFile(FilePath("/project/.codespeak/specs/script.sh"), ByteArray(0))
        persistence.writeFile(FilePath("/project/.codespeak/specs/image.png"), ByteArray(0))

        vm.loadSpecs(FilePath("/project"))

        assertEquals(2, vm.state.value.specs.size)
    }

    // ── refresh() ────────────────────────────────────────────────────────────────

    @Test
    fun refresh_afterFileDeleted_updatesSpecList() = runTest {
        val (vm, persistence) = buildVm()
        persistence.addDirectory(FilePath("/project/.codespeak/specs"))
        persistence.writeFile(FilePath("/project/.codespeak/specs/a.md"), ByteArray(0))
        persistence.writeFile(FilePath("/project/.codespeak/specs/b.md"), ByteArray(0))
        vm.loadSpecs(FilePath("/project"))
        assertEquals(2, vm.state.value.specs.size)

        persistence.deleteFile(FilePath("/project/.codespeak/specs/b.md"))
        vm.refresh(FilePath("/project"))

        assertEquals(1, vm.state.value.specs.size)
    }

    // ── clearError() ─────────────────────────────────────────────────────────────

    @Test
    fun clearError_removesErrorMessage() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val vm = SpecsViewModel(ThrowingIsDirectoryStore(), scope)
        vm.loadSpecs(FilePath("/project"))
        assertNotNull(vm.state.value.errorMessage)

        vm.clearError()

        assertNull(vm.state.value.errorMessage)
    }
}
