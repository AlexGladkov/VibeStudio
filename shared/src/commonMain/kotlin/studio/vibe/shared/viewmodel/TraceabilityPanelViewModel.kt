package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.model.FilePath

data class TraceabilityEntry(
    val specName: String,
    val linkedFiles: List<FilePath>,
)

data class TraceabilityPanelState(
    val entries: List<TraceabilityEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class TraceabilityPanelViewModel(
    private val persistenceStore: PersistenceStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(TraceabilityPanelState())
    val state: StateFlow<TraceabilityPanelState> = _state.asStateFlow()

    fun scanProject(projectPath: FilePath) {
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val entries = buildTraceabilityMap(projectPath)
                _state.update { it.copy(entries = entries, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun refresh(projectPath: FilePath) {
        scanProject(projectPath)
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private suspend fun buildTraceabilityMap(projectPath: FilePath): List<TraceabilityEntry> {
        // Map from spec name → set of file paths that reference it via @file marker
        val specToFiles = mutableMapOf<String, MutableList<FilePath>>()
        scanDirectory(projectPath, specToFiles)
        return specToFiles.entries
            .map { (spec, files) -> TraceabilityEntry(specName = spec, linkedFiles = files.sortedBy { it.path }) }
            .sortedBy { it.specName }
    }

    private suspend fun scanDirectory(
        dir: FilePath,
        result: MutableMap<String, MutableList<FilePath>>,
        depth: Int = 0,
        maxDepth: Int = 8,
    ) {
        if (depth > maxDepth) return
        val ignored = setOf("node_modules", ".git", ".build", "DerivedData", ".DS_Store")
        val entries = runCatching { persistenceStore.listDirectory(dir) }.getOrElse { return }
        for (entry in entries) {
            if (entry.name in ignored) continue
            val isDir = runCatching { persistenceStore.isDirectory(entry) }.getOrElse { false }
            if (isDir) {
                scanDirectory(entry, result, depth + 1, maxDepth)
            } else {
                parseFileMarkers(entry, result)
            }
        }
    }

    private suspend fun parseFileMarkers(
        filePath: FilePath,
        result: MutableMap<String, MutableList<FilePath>>,
    ) {
        // Only scan text-like files
        val ext = filePath.path.substringAfterLast('.', "")
        val textExtensions = setOf(
            "kt", "swift", "ts", "tsx", "js", "jsx", "py", "go", "rs", "java",
            "cs", "cpp", "c", "h", "html", "css", "scss", "vue", "md", "yaml", "yml", "json",
        )
        if (ext !in textExtensions) return

        val bytes = runCatching { persistenceStore.readFile(filePath) }.getOrNull() ?: return
        val content = bytes.decodeToString()

        // Match @file markers: patterns like "// @file: specName", "# @file specName", etc.
        val fileMarkerPattern = Regex("""@file[:\s]+([^\s,\n]+)""", RegexOption.IGNORE_CASE)
        val matches = fileMarkerPattern.findAll(content)
        for (match in matches) {
            val specName = match.groupValues[1].trim().removeSuffix(".md").removeSuffix(".cs")
            if (specName.isNotBlank()) {
                result.getOrPut(specName) { mutableListOf() }.add(filePath)
            }
        }
    }
}
