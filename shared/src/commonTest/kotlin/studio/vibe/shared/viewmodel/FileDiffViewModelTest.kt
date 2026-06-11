package studio.vibe.shared.feature.git.presentation

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.feature.git.domain.model.DiffLineType
import studio.vibe.shared.feature.git.domain.model.GitDiffHunk
import studio.vibe.shared.feature.git.domain.model.GitDiffLine
import studio.vibe.shared.core.common.git.GitFile
import studio.vibe.shared.core.common.git.GitFileStatus
import studio.vibe.shared.testutil.FakeGitService
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class FileDiffViewModelTest {

    private fun buildVm(): Pair<FileDiffViewModel, FakeGitService> {
        val dispatcher = UnconfinedTestDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher)
        val git = FakeGitService()
        val vm = FileDiffViewModel(gitQuerying = git, parentScope = scope)
        return Pair(vm, git)
    }

    @Test
    fun initialState_isLoading() {
        val (vm) = buildVm()
        assertIs<FileDiffState.Loading>(vm.state.value)
    }

    @Test
    fun load_binaryExtension_emitsBinaryFile() = runTest {
        val (vm) = buildVm()
        val file = GitFile(path = "icon.png", status = GitFileStatus.MODIFIED)
        vm.load(file, "/tmp/repo", staged = false)
        assertIs<FileDiffState.BinaryFile>(vm.state.value)
        assertEquals("png", (vm.state.value as FileDiffState.BinaryFile).extension)
    }

    @Test
    fun load_textFile_emitsLoadedWithHunks() = runTest {
        val (vm, git) = buildVm()
        val hunk = GitDiffHunk(
            header = "@@ -1,2 +1,3 @@",
            lines = listOf(
                GitDiffLine(type = DiffLineType.ADDITION, content = "new line", oldLineNumber = null, newLineNumber = 1),
            ),
        )
        git.diffResult = listOf(hunk)
        val file = GitFile(path = "Main.kt", status = GitFileStatus.MODIFIED)
        vm.load(file, "/tmp/repo", staged = false)
        val state = vm.state.value
        assertIs<FileDiffState.Loaded>(state)
        assertEquals(1, (state as FileDiffState.Loaded).hunks.size)
        assertEquals(hunk, state.hunks.first())
    }

    @Test
    fun load_onError_emitsError() = runTest {
        val (vm, git) = buildVm()
        git.throwOnDiff = RuntimeException("diff failed")
        val file = GitFile(path = "App.kt", status = GitFileStatus.MODIFIED)
        vm.load(file, "/tmp/repo", staged = false)
        val state = vm.state.value
        assertIs<FileDiffState.Error>(state)
        assertEquals("diff failed", (state as FileDiffState.Error).message)
    }
}
