package studio.vibe.shared.feature.tabbar.presentation

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.feature.tabbar.presentation.FreeTab
import studio.vibe.shared.core.common.project.Project
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface FreeTabManaging {
    val freeTabsFlow: StateFlow<List<FreeTab>>
    val freeTabs: List<FreeTab>
    fun createFreeTab(): FreeTab
    fun removeFreeTab(id: Uuid)
    fun isFreeTab(id: Uuid): Boolean
    fun nextActiveId(afterRemovedId: Uuid, projects: List<Project>): Uuid?
    fun moveFreeTabs(fromIndices: Set<Int>, toDestination: Int)
}
