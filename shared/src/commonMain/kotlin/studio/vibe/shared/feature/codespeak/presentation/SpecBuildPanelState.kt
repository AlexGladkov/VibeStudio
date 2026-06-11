package studio.vibe.shared.feature.codespeak.presentation

import studio.vibe.shared.feature.codespeak.domain.model.CodeSpeakCommand
import studio.vibe.shared.feature.codespeak.domain.model.SpecStats
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class SpecBuildPanelState(
    val output: String = "",
    val isRunning: Boolean = false,
    val stopRequested: Boolean = false,
    val lastStats: SpecStats? = null,
    val errorMessage: String? = null,
    val selectedCommand: CodeSpeakCommand = CodeSpeakCommand.BUILD,
    val taskName: String = "",
    val changeMessage: String = "",
)
