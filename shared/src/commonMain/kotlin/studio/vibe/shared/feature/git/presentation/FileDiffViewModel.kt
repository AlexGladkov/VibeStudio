package studio.vibe.shared.feature.git.presentation

import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.core.common.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.feature.git.domain.contract.GitStatusQuerying
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitDiffHunk
import studio.vibe.shared.core.common.git.GitFile

sealed interface FileDiffState {
    data object Loading : FileDiffState
    data class Loaded(
        val hunks: List<GitDiffHunk>,
        val sizeWarning: String? = null,
    ) : FileDiffState
    data class BinaryFile(val extension: String) : FileDiffState
    data class Error(val message: String) : FileDiffState
}

class FileDiffViewModel(
    private val gitQuerying: GitStatusQuerying,
    parentScope: CoroutineScope,
) : BaseViewModel(parentScope) {

    private val _state = MutableStateFlow<FileDiffState>(FileDiffState.Loading)
    val state: StateFlow<FileDiffState> = _state.asStateFlow()

    fun load(file: GitFile, projectPath: String, staged: Boolean) {
        val ext = file.path.substringAfterLast('.', "").lowercase()
        if (ext in BINARY_EXTENSIONS) {
            _state.value = FileDiffState.BinaryFile(ext)
            return
        }

        _state.value = FileDiffState.Loading

        scope.launch {
            runCatching {
                val hunks = gitQuerying.diff(
                    file = file.path,
                    staged = staged,
                    at = FilePath(projectPath),
                )
                val totalBytes = hunks.sumOf { hunk ->
                    hunk.header.encodeToByteArray().size +
                        hunk.lines.sumOf { it.content.encodeToByteArray().size }
                }
                val warning = if (totalBytes > MAX_DIFF_BYTES) {
                    "Diff truncated — file too large (${totalBytes / 1024} KB shown)"
                } else null
                _state.value = FileDiffState.Loaded(hunks = hunks, sizeWarning = warning)
            }.onFailure { e ->
                _state.value = FileDiffState.Error(e.message ?: "Failed to load diff")
            }
        }
    }

    fun clearError() {
        _state.update { if (it is FileDiffState.Error) FileDiffState.Loading else it }
    }

    companion object {
        private const val MAX_DIFF_BYTES = 512 * 1024

        val BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "ico", "icns", "pdf",
            "zip", "tar", "gz", "bz2", "xz", "dmg", "exe",
            "dylib", "so", "a", "o", "class", "jar", "war",
            "ear", "bin", "dat", "db", "sqlite",
        )
    }
}
