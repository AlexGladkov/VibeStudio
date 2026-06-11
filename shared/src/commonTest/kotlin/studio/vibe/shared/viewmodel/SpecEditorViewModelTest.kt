@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package studio.vibe.shared.feature.codespeak.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.PersistenceStore
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.testutil.FakePersistenceStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ── Test helpers (throwing stubs via interface, not subclassing) ──────────────

/**
 * [PersistenceStore] that throws on [readFile]. All other operations delegate
 * to an in-memory [FakePersistenceStore].
 */
private class ThrowOnReadPersistenceStore : PersistenceStore {
    private val delegate = FakePersistenceStore()
    override suspend fun readFile(path: FilePath): ByteArray? =
        throw RuntimeException("disk I/O failure (ThrowOnReadPersistenceStore)")
    override suspend fun writeFile(path: FilePath, data: ByteArray) = delegate.writeFile(path, data)
    override suspend fun fileExists(path: FilePath): Boolean = delegate.fileExists(path)
    override suspend fun isDirectory(path: FilePath): Boolean = delegate.isDirectory(path)
    override suspend fun createDirectory(path: FilePath) = delegate.createDirectory(path)
    override suspend fun listDirectory(path: FilePath): List<FilePath> = delegate.listDirectory(path)
    override suspend fun deleteFile(path: FilePath) = delegate.deleteFile(path)
    override fun appSupportDirectory(): FilePath = delegate.appSupportDirectory()
}

/**
 * [PersistenceStore] that throws on [writeFile]. All other operations delegate
 * to an in-memory [FakePersistenceStore].
 */
private class ThrowOnWritePersistenceStore : PersistenceStore {
    private val delegate = FakePersistenceStore()
    override suspend fun readFile(path: FilePath): ByteArray? = delegate.readFile(path)
    override suspend fun writeFile(path: FilePath, data: ByteArray) =
        throw RuntimeException("write failed: disk full (ThrowOnWritePersistenceStore)")
    override suspend fun fileExists(path: FilePath): Boolean = delegate.fileExists(path)
    override suspend fun isDirectory(path: FilePath): Boolean = delegate.isDirectory(path)
    override suspend fun createDirectory(path: FilePath) = delegate.createDirectory(path)
    override suspend fun listDirectory(path: FilePath): List<FilePath> = delegate.listDirectory(path)
    override suspend fun deleteFile(path: FilePath) = delegate.deleteFile(path)
    override fun appSupportDirectory(): FilePath = delegate.appSupportDirectory()
}

/**
 * Unit tests for [SpecEditorViewModel].
 *
 * Covers:
 * - loadSpec with existing / missing file
 * - updateContent marks dirty
 * - saveSpec(path) explicit path variant
 * - saveSpec() inferred path variant
 * - clearContent resets state
 * - clearError
 */
class SpecEditorViewModelTest {

    private lateinit var persistence: FakePersistenceStore
    private lateinit var vm: SpecEditorViewModel

    @BeforeTest
    fun setup() {
        persistence = FakePersistenceStore()
        vm = SpecEditorViewModel(
            persistenceStore = persistence,
            parentScope = CoroutineScope(UnconfinedTestDispatcher()),
        )
    }

    @AfterTest
    fun teardown() {
        vm.dispose()
        persistence.reset()
    }

    // ── loadSpec ──────────────────────────────────────────────────────────────

    @Test
    fun loadSpec_existingFile_populatesContent() = runTest {
        // Arrange
        val specPath = FilePath("/specs/feature.cs.md")
        val content = "# Feature spec\n\n- [ ] step 1"
        persistence.writeFile(specPath, content.encodeToByteArray())

        // Act
        vm.loadSpec(specPath)

        // Assert
        assertEquals(content, vm.state.value.content)
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isDirty)
        assertEquals(specPath, vm.state.value.currentPath)
    }

    @Test
    fun loadSpec_missingFile_setsEmptyContent() = runTest {
        // Arrange — file does not exist in persistence
        val specPath = FilePath("/specs/nonexistent.cs.md")

        // Act
        vm.loadSpec(specPath)

        // Assert
        assertEquals("", vm.state.value.content)
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isDirty)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun loadSpec_setsCurrentPath() = runTest {
        // Arrange
        val specPath = FilePath("/specs/overview.md")

        // Act
        vm.loadSpec(specPath)

        // Assert
        assertEquals(specPath, vm.state.value.currentPath)
    }

    @Test
    fun loadSpec_persistenceThrows_setsErrorMessage() = runTest {
        // Arrange — use a dedicated FakePersistenceStore whose readFile is configured to throw
        val throwingPersistence = ThrowOnReadPersistenceStore()
        val throwingVm = SpecEditorViewModel(
            persistenceStore = throwingPersistence,
            parentScope = CoroutineScope(UnconfinedTestDispatcher()),
        )

        // Act
        throwingVm.loadSpec(FilePath("/specs/bad.md"))

        // Assert
        assertNotNull(throwingVm.state.value.errorMessage)
        assertFalse(throwingVm.state.value.isLoading)
        throwingVm.dispose()
    }

    // ── updateContent ─────────────────────────────────────────────────────────

    @Test
    fun updateContent_marksDirty() {
        // Arrange — initial state is not dirty
        assertFalse(vm.state.value.isDirty)

        // Act
        vm.updateContent("new content")

        // Assert
        assertTrue(vm.state.value.isDirty)
        assertEquals("new content", vm.state.value.content)
    }

    @Test
    fun updateContent_multipleEdits_allReflected() {
        vm.updateContent("first")
        assertEquals("first", vm.state.value.content)
        vm.updateContent("second")
        assertEquals("second", vm.state.value.content)
        assertTrue(vm.state.value.isDirty)
    }

    // ── saveSpec(path) ────────────────────────────────────────────────────────

    @Test
    fun saveSpecWithPath_writesContentToFile() = runTest {
        // Arrange
        val specPath = FilePath("/specs/new.cs.md")
        vm.updateContent("# New spec")

        // Act
        vm.saveSpec(specPath)

        // Assert
        val written = persistence.readFile(specPath)
        assertNotNull(written)
        assertEquals("# New spec", written.decodeToString())
    }

    @Test
    fun saveSpecWithPath_clearsDirtyFlagAndSetsCurrentPath() = runTest {
        // Arrange
        val specPath = FilePath("/specs/feature.cs.md")
        vm.updateContent("some content")
        assertTrue(vm.state.value.isDirty)

        // Act
        vm.saveSpec(specPath)

        // Assert
        assertFalse(vm.state.value.isDirty)
        assertFalse(vm.state.value.isSaving)
        assertEquals(specPath, vm.state.value.currentPath)
    }

    @Test
    fun saveSpecWithPath_persistenceThrows_setsErrorMessage() = runTest {
        // Arrange
        val throwingPersistence = ThrowOnWritePersistenceStore()
        val throwingVm = SpecEditorViewModel(
            persistenceStore = throwingPersistence,
            parentScope = CoroutineScope(UnconfinedTestDispatcher()),
        )
        throwingVm.updateContent("content")

        // Act
        throwingVm.saveSpec(FilePath("/specs/fail.md"))

        // Assert
        assertNotNull(throwingVm.state.value.errorMessage)
        assertFalse(throwingVm.state.value.isSaving)
        throwingVm.dispose()
    }

    // ── saveSpec() — inferred path ────────────────────────────────────────────

    @Test
    fun saveSpecInferred_withCurrentPath_writesFile() = runTest {
        // Arrange — load a spec first so currentPath is set
        val specPath = FilePath("/specs/existing.cs.md")
        persistence.writeFile(specPath, "original".encodeToByteArray())
        vm.loadSpec(specPath)
        vm.updateContent("updated content")

        // Act
        vm.saveSpec()

        // Assert
        val written = persistence.readFile(specPath)
        assertNotNull(written)
        assertEquals("updated content", written.decodeToString())
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun saveSpecInferred_withoutCurrentPath_isNoOp() = runTest {
        // Arrange — currentPath is null (no loadSpec called)
        assertNull(vm.state.value.currentPath)
        vm.updateContent("some content")

        // Act
        vm.saveSpec()

        // Assert — nothing written (persistence has no files)
        // isSaving must not get stuck
        assertFalse(vm.state.value.isSaving)
    }

    // ── clearContent ──────────────────────────────────────────────────────────

    @Test
    fun clearContent_resetsToInitialState() = runTest {
        // Arrange
        val specPath = FilePath("/specs/feature.cs.md")
        persistence.writeFile(specPath, "original content".encodeToByteArray())
        vm.loadSpec(specPath)
        vm.updateContent("modified")

        // Act
        vm.clearContent()

        // Assert
        assertEquals(SpecEditorState(), vm.state.value)
    }

    // ── clearError ────────────────────────────────────────────────────────────

    @Test
    fun clearError_nullifiesErrorMessage() = runTest {
        // Arrange — trigger load error via a throwing store
        val errorVm = SpecEditorViewModel(
            persistenceStore = ThrowOnReadPersistenceStore(),
            parentScope = CoroutineScope(UnconfinedTestDispatcher()),
        )
        errorVm.loadSpec(FilePath("/specs/fail.md"))
        assertNotNull(errorVm.state.value.errorMessage)

        // Act
        errorVm.clearError()

        // Assert
        assertNull(errorVm.state.value.errorMessage)
        errorVm.dispose()
    }

    @Test
    fun clearError_whenNoError_isNoOp() {
        assertNull(vm.state.value.errorMessage)
        vm.clearError()
        assertNull(vm.state.value.errorMessage)
    }
}
