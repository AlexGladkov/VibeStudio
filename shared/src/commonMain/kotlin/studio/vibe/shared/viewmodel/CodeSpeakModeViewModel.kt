package studio.vibe.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.contract.ProjectManaging
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GeneratedFile
import studio.vibe.shared.model.GeneratedFileDetector
import studio.vibe.shared.model.SpecFile
import studio.vibe.shared.model.SpecStatus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class CodeSpeakModeState(
    val specs: List<SpecFile> = emptyList(),
    val selectedSpec: SpecFile? = null,
    val editorContent: String = "",
    val isEditorDirty: Boolean = false,
    val generatedFiles: List<GeneratedFile> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class CodeSpeakModeViewModel(
    private val persistenceStore: PersistenceStore,
    private val projectManaging: ProjectManaging,
    parentScope: CoroutineScope,
) : BaseViewModel(parentScope) {
    private val _state = MutableStateFlow(CodeSpeakModeState())
    val state: StateFlow<CodeSpeakModeState> = _state.asStateFlow()

    private var scanJob: Job? = null

    // Tracks which project's specs are currently displayed. When loadSpecs is
    // invoked for a different project (tab switch) we must clear the
    // per-spec state — otherwise `selectedSpec` and `editorContent` from the
    // previous project leak into the new one and the editor shows stale text.
    private var loadedProjectId: Uuid? = null

    fun loadSpecs(projectId: Uuid) {
        val project = projectManaging.project(projectId) ?: return
        val projectChanged = loadedProjectId != projectId
        scope.launch {
            if (projectChanged) {
                // Drop everything that was scoped to the previous project.
                _state.update {
                    CodeSpeakModeState(isLoading = true)
                }
            } else {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
            }
            runCatching {
                val specs = scanSpecFiles(project.path)
                _state.update { s ->
                    val updatedSelectedSpec = if (projectChanged) {
                        null
                    } else {
                        s.selectedSpec?.let { sel ->
                            specs.firstOrNull { it.path.path == sel.path.path } ?: sel
                        }
                    }
                    s.copy(specs = specs, selectedSpec = updatedSelectedSpec, isLoading = false)
                }
                loadedProjectId = projectId
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun selectSpec(spec: SpecFile) {
        scope.launch {
            _state.update { it.copy(selectedSpec = spec, isLoading = true) }
            runCatching {
                val bytes = persistenceStore.readFile(spec.path)
                val content = bytes?.decodeToString() ?: ""
                _state.update { it.copy(editorContent = content, isEditorDirty = false, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun updateEditorContent(content: String) {
        _state.update { it.copy(editorContent = content, isEditorDirty = true) }
    }

    fun saveCurrentSpec() {
        val spec = _state.value.selectedSpec ?: return
        scope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                persistenceStore.writeFile(spec.path, _state.value.editorContent.encodeToByteArray())
                _state.update { it.copy(isSaving = false, isEditorDirty = false) }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun createNewSpec(projectId: Uuid, name: String) {
        val project = projectManaging.project(projectId) ?: return
        scope.launch {
            runCatching {
                val specsDir = project.path.child(".codespeak").child("specs")
                if (!persistenceStore.isDirectory(specsDir)) {
                    persistenceStore.createDirectory(specsDir)
                }
                val sanitized = name.trim().replace(Regex("[^a-zA-Z0-9_\\-. ]"), "_")
                val fileName = if (sanitized.endsWith(".md")) sanitized else "$sanitized.md"
                val filePath = specsDir.child(fileName)
                persistenceStore.writeFile(filePath, "# $sanitized\n\n".encodeToByteArray())
                loadSpecs(projectId)
                val newSpec = SpecFile(path = filePath)
                selectSpec(newSpec)
            }.onFailure { e ->
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun scanGeneratedFiles(projectId: Uuid) {
        val project = projectManaging.project(projectId) ?: return
        scanJob?.cancel()
        scanJob = scope.launch {
            runCatching {
                val generated = scanForGeneratedFiles(project.path)
                _state.update { it.copy(generatedFiles = generated) }
            }
        }
    }

    fun loadGeneratedFileContent(file: GeneratedFile) {
        scope.launch {
            _state.update { it.copy(isLoading = true, selectedSpec = null) }
            runCatching {
                val bytes = persistenceStore.readFile(file.path)
                val content = bytes?.decodeToString() ?: ""
                _state.update { it.copy(editorContent = content, isEditorDirty = false, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private suspend fun scanSpecFiles(projectPath: FilePath): List<SpecFile> {
        val candidates = listOf(
            projectPath.child(".codespeak").child("specs"),
            projectPath.child("specs"),
        )
        for (dir in candidates) {
            if (runCatching { persistenceStore.isDirectory(dir) }.getOrElse { false }) {
                return collectSpecFiles(dir)
            }
        }
        return emptyList()
    }

    private suspend fun collectSpecFiles(dir: FilePath): List<SpecFile> {
        val entries = runCatching { persistenceStore.listDirectory(dir) }.getOrElse { emptyList() }
        val specs = mutableListOf<SpecFile>()
        for (entry in entries.sortedBy { it.name }) {
            val isDir = runCatching { persistenceStore.isDirectory(entry) }.getOrElse { false }
            if (isDir) {
                specs.addAll(collectSpecFiles(entry))
            } else if (entry.path.endsWith(".md") || entry.path.endsWith(".cs.md")) {
                specs.add(SpecFile(path = entry, status = SpecStatus.UNKNOWN))
            }
        }
        return specs
    }

    private suspend fun scanForGeneratedFiles(
        dir: FilePath,
        depth: Int = 0,
        maxDepth: Int = 6,
    ): List<GeneratedFile> {
        if (depth > maxDepth) return emptyList()
        val ignored = setOf("node_modules", ".git", ".build", "DerivedData", ".DS_Store")
        val entries = runCatching { persistenceStore.listDirectory(dir) }.getOrElse { emptyList() }
        val result = mutableListOf<GeneratedFile>()
        for (entry in entries) {
            if (entry.name in ignored) continue
            val isDir = runCatching { persistenceStore.isDirectory(entry) }.getOrElse { false }
            if (isDir) {
                result.addAll(scanForGeneratedFiles(entry, depth + 1, maxDepth))
            } else {
                val bytes = runCatching { persistenceStore.readFile(entry) }.getOrNull() ?: continue
                val preview = bytes.decodeToString().take(256)
                if (GeneratedFileDetector.isGenerated(preview)) {
                    val specName = GeneratedFileDetector.parseSpecName(preview)
                    result.add(GeneratedFile(path = entry, specName = specName))
                }
            }
        }
        return result
    }
}
