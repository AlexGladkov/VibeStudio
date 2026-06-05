@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.contract.GitRemoteOperating
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitServiceError
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [GitRemoteSetupViewModel].
 *
 * A minimal [GitRemoteOperating] test double is used inline — no real I/O occurs.
 */
class GitRemoteSetupViewModelTest {

    private class FakeRemoteOperating(
        var remoteURLResult: String? = null,
        var throwOnRemoteURL: Throwable? = null,
        var throwOnAddRemote: Throwable? = null,
        var addRemoteCallCount: Int = 0,
    ) : GitRemoteOperating {
        override suspend fun remoteURL(name: String, at: FilePath): String? {
            throwOnRemoteURL?.let { throw it }
            return remoteURLResult
        }

        override suspend fun addRemote(name: String, url: String, at: FilePath) {
            addRemoteCallCount++
            throwOnAddRemote?.let { throw it }
        }

        override suspend fun push(remote: String, at: FilePath) {}
        override suspend fun pull(remote: String, at: FilePath) {}
        override suspend fun fetch(remote: String, at: FilePath) {}
        override suspend fun pushBranch(branch: String, remote: String, at: FilePath) {}
        override suspend fun pullBranch(branch: String, isCurrent: Boolean, remote: String, at: FilePath) {}
        override suspend fun defaultRemote(forBranch: String?, at: FilePath): String = "origin"
    }

    private lateinit var fakeGit: FakeRemoteOperating
    private lateinit var vm: GitRemoteSetupViewModel
    private val path = FilePath("/repo")

    @BeforeTest
    fun setup() {
        fakeGit = FakeRemoteOperating()
        vm = GitRemoteSetupViewModel(
            gitService = fakeGit,
            parentScope = CoroutineScope(UnconfinedTestDispatcher()),
        )
    }

    @AfterTest
    fun teardown() {
        vm.dispose()
    }

    // ── loadExistingRemote ────────────────────────────────────────────────────

    @Test
    fun loadExistingRemote_noRemote_existingRemoteURLIsNull() = runTest {
        // Arrange
        fakeGit.remoteURLResult = null

        // Act
        vm.loadExistingRemote(path)

        // Assert
        assertNull(vm.state.value.existingRemoteURL)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun loadExistingRemote_withRemote_populatesExistingAndInput() = runTest {
        // Arrange
        fakeGit.remoteURLResult = "https://github.com/user/repo.git"

        // Act
        vm.loadExistingRemote(path)

        // Assert
        assertEquals("https://github.com/user/repo.git", vm.state.value.existingRemoteURL)
        assertEquals("https://github.com/user/repo.git", vm.state.value.remoteURL)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun loadExistingRemote_serviceThrows_setsErrorMessage() = runTest {
        // Arrange
        fakeGit.throwOnRemoteURL = GitServiceError.CommandFailed(
            command = "remote get-url",
            exitCode = 1,
            stderr = "No such remote 'origin'",
        )

        // Act
        vm.loadExistingRemote(path)

        // Assert
        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun loadExistingRemote_serviceThrows_isLoadingResetsToFalse() = runTest {
        // Arrange
        fakeGit.throwOnRemoteURL = RuntimeException("network error")

        // Act
        vm.loadExistingRemote(path)

        // Assert
        assertFalse(vm.state.value.isLoading)
    }

    // ── updateRemoteName / updateRemoteURL ────────────────────────────────────

    @Test
    fun updateRemoteName_setsNameAndClearsError() {
        // Arrange — put an error in state first
        vm.addRemote(path) // triggers blank URL error
        assertNotNull(vm.state.value.errorMessage)

        // Act
        vm.updateRemoteName("upstream")

        // Assert
        assertEquals("upstream", vm.state.value.remoteName)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun updateRemoteURL_setsURLAndClearsError() {
        // Arrange — put an error in state first via addRemote with blank URL
        vm.addRemote(path)
        assertNotNull(vm.state.value.errorMessage)

        // Act
        vm.updateRemoteURL("https://github.com/user/repo.git")

        // Assert
        assertEquals("https://github.com/user/repo.git", vm.state.value.remoteURL)
        assertNull(vm.state.value.errorMessage)
    }

    // ── addRemote — validation ────────────────────────────────────────────────

    @Test
    fun addRemote_blankURL_setsErrorDoesNotCallService() = runTest {
        // Arrange — leave remoteURL as default empty string
        // Act
        vm.addRemote(path)

        // Assert
        assertNotNull(vm.state.value.errorMessage)
        assertEquals(0, fakeGit.addRemoteCallCount)
    }

    @Test
    fun addRemote_blankName_setsErrorDoesNotCallService() = runTest {
        // Arrange — set valid URL but blank name
        vm.updateRemoteName("   ")
        vm.updateRemoteURL("https://github.com/user/repo.git")

        // Act
        vm.addRemote(path)

        // Assert
        assertNotNull(vm.state.value.errorMessage)
        assertEquals(0, fakeGit.addRemoteCallCount)
    }

    @Test
    fun addRemote_validInput_callsServiceAndSetsSaved() = runTest {
        // Arrange
        vm.updateRemoteURL("https://github.com/user/repo.git")

        // Act
        vm.addRemote(path)

        // Assert
        assertEquals(1, fakeGit.addRemoteCallCount)
        assertTrue(vm.state.value.isSaved)
        assertEquals("https://github.com/user/repo.git", vm.state.value.existingRemoteURL)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun addRemote_serviceThrows_setsErrorAndClearsLoading() = runTest {
        // Arrange
        vm.updateRemoteURL("https://github.com/user/repo.git")
        fakeGit.throwOnAddRemote = GitServiceError.CommandFailed(
            command = "remote add",
            exitCode = 1,
            stderr = "remote origin already exists",
        )

        // Act
        vm.addRemote(path)

        // Assert
        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isSaved)
        assertFalse(vm.state.value.isLoading)
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    fun reset_restoresInitialState() = runTest {
        // Arrange — dirty up state
        vm.updateRemoteURL("https://github.com/user/repo.git")
        vm.addRemote(path)

        // Act
        vm.reset()

        // Assert — back to defaults
        assertEquals(GitRemoteSetupState(), vm.state.value)
    }

    // ── clearError ────────────────────────────────────────────────────────────

    @Test
    fun clearError_nullifiesErrorMessage() {
        // Arrange — trigger an error
        vm.addRemote(path)
        assertNotNull(vm.state.value.errorMessage)

        // Act
        vm.clearError()

        // Assert
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun clearError_whenNoError_isNoOp() {
        // Arrange — no error set
        assertNull(vm.state.value.errorMessage)

        // Act + Assert — must not throw
        vm.clearError()
        assertNull(vm.state.value.errorMessage)
    }
}
