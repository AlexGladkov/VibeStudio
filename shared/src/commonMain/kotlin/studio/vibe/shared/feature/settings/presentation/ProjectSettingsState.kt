package studio.vibe.shared.feature.settings.presentation

import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class ProjectSettingsState(
    val productionURL: String = "",
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)
