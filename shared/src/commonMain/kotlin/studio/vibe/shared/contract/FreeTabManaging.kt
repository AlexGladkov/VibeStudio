package studio.vibe.shared.contract

import studio.vibe.shared.model.FreeTab
import studio.vibe.shared.model.Project
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface FreeTabManaging {
    val freeTabs: List<FreeTab>
    fun createFreeTab(): FreeTab
    fun removeFreeTab(id: Uuid)
    fun isFreeTab(id: Uuid): Boolean
    fun nextActiveId(afterRemovedId: Uuid, projects: List<Project>): Uuid?
    fun moveFreeTabs(fromIndices: Set<Int>, toDestination: Int)
}
