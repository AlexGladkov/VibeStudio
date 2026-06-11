package studio.vibe.shared.feature.tabbar.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.feature.tabbar.presentation.FreeTabManaging
import studio.vibe.shared.feature.tabbar.presentation.FreeTab
import studio.vibe.shared.core.common.project.Project
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FreeTabStoreImpl : FreeTabManaging {

    private val _freeTabs = MutableStateFlow<List<FreeTab>>(emptyList())

    override val freeTabsFlow: StateFlow<List<FreeTab>> = _freeTabs.asStateFlow()

    override val freeTabs: List<FreeTab>
        get() = _freeTabs.value

    override fun createFreeTab(): FreeTab {
        val current = _freeTabs.value
        val title = nextTitle(current)
        val tab = FreeTab(title = title)
        _freeTabs.update { it + tab }
        return tab
    }

    override fun removeFreeTab(id: Uuid) {
        _freeTabs.update { tabs -> tabs.filter { it.id != id } }
    }

    override fun isFreeTab(id: Uuid): Boolean =
        _freeTabs.value.any { it.id == id }

    override fun nextActiveId(afterRemovedId: Uuid, projects: List<Project>): Uuid? {
        val remaining = _freeTabs.value.filter { it.id != afterRemovedId }
        if (remaining.isNotEmpty()) return remaining.last().id
        return projects.firstOrNull()?.id
    }

    override fun moveFreeTabs(fromIndices: Set<Int>, toDestination: Int) {
        _freeTabs.update { snapshot ->
            val current = snapshot.toMutableList()
            val moving = fromIndices.sortedDescending().mapNotNull { idx ->
                if (idx in current.indices) current.removeAt(idx) else null
            }.reversed()

            val insertAt = (toDestination - fromIndices.count { it < toDestination })
                .coerceIn(0, current.size)

            current.addAll(insertAt, moving)
            current
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun nextTitle(existing: List<FreeTab>): String {
        val usedNumbers = existing
            .mapNotNull { tab ->
                val suffix = tab.title.removePrefix("Terminal")
                if (suffix.isEmpty()) 1
                else suffix.trim().toIntOrNull()
            }
            .toSet()

        var candidate = 1
        while (candidate in usedNumbers) candidate++

        return if (candidate == 1) "Terminal" else "Terminal $candidate"
    }
}
