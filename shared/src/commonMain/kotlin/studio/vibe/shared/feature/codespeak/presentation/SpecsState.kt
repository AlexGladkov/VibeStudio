package studio.vibe.shared.feature.codespeak.presentation

import studio.vibe.shared.feature.codespeak.domain.model.SpecFile
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class SpecsState(
    val specs: List<SpecFile> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
