package studio.vibe.shared.feature.codespeak.presentation

import studio.vibe.shared.feature.codespeak.domain.model.GeneratedFile
import studio.vibe.shared.feature.codespeak.domain.model.SpecFile
import kotlin.uuid.ExperimentalUuidApi

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
