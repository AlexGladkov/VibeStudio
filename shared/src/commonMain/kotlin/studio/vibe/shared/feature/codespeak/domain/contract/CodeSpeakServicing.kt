@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.codespeak.domain.contract

import kotlinx.coroutines.flow.StateFlow
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.feature.codespeak.domain.model.SpecStats
import kotlin.uuid.Uuid

/**
 * Domain interface for the CodeSpeak configuration and stats service.
 *
 * Extracted so that [AppLifecycleCoordinator] and other call sites depend on an
 * abstraction rather than the concrete [CodeSpeakServiceImpl], enabling tests and
 * alternative implementations without pulling in the full service graph.
 */
interface CodeSpeakServicing {

    /** Live map of project-id → whether that project has a `codespeak.json`. */
    val projectHasConfig: StateFlow<Map<Uuid, Boolean>>

    /** Latest [SpecStats] per project, keyed by project id. */
    val projectStats: StateFlow<Map<Uuid, SpecStats>>

    /**
     * Checks whether [project] has a `codespeak.json` at its root and updates
     * [projectHasConfig] accordingly.
     */
    suspend fun checkConfig(project: Project)

    /**
     * Stores the latest [SpecStats] for the given project.
     */
    fun updateStats(stats: SpecStats, projectId: Uuid)

    /**
     * Returns `true` if [projectId] has been confirmed to have a `codespeak.json`.
     * Returns `false` for unknown / not-yet-checked projects.
     */
    fun isCodeSpeakProject(projectId: Uuid): Boolean

    /**
     * Removes cached config and stats entries for [projectId].
     */
    fun clearCache(projectId: Uuid)
}
