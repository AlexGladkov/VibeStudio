package studio.vibe.shared.feature.codespeak.presentation

import studio.vibe.shared.core.common.FilePath

data class SpecEditorState(
    val content: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null,
    val currentPath: FilePath? = null,
)
