package studio.vibe.shared.feature.codespeak.domain.model

public data class CodeSpeakRunBarState(
    val command: CodeSpeakCommand = CodeSpeakCommand.BUILD,
    val taskName: String = "",
    val changeMessage: String = "",
    val isRunning: Boolean = false,
    val stopRequested: Boolean = false,
    val currentSpecName: String = "",
    val isEditorDirty: Boolean = false,
)
