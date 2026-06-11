package studio.vibe.shared.feature.tabbar.presentation

import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class TabItemState(
    val isCloseConfirmationVisible: Boolean = false,
    val errorMessage: String? = null,
)
