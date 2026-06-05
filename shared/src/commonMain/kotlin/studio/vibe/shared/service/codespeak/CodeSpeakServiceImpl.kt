package studio.vibe.shared.service.codespeak

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.vibe.shared.contract.CodeSpeakServicing
import studio.vibe.shared.contract.PersistenceStore
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.model.SpecStats
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val CODESPEAK_CONFIG_FILE = "codespeak.json"

@OptIn(ExperimentalUuidApi::class)
class CodeSpeakServiceImpl(
    private val persistence: PersistenceStore,
) : CodeSpeakServicing {

    // Whether each project has a codespeak.json at its root
    private val _projectHasConfig = MutableStateFlow<Map<Uuid, Boolean>>(emptyMap())
    override val projectHasConfig: StateFlow<Map<Uuid, Boolean>> = _projectHasConfig.asStateFlow()

    // Latest SpecStats per project
    private val _projectStats = MutableStateFlow<Map<Uuid, SpecStats>>(emptyMap())
    override val projectStats: StateFlow<Map<Uuid, SpecStats>> = _projectStats.asStateFlow()

    /**
     * Checks whether the given project contains a `codespeak.json` at its root
     * and updates [projectHasConfig] accordingly.
     */
    override suspend fun checkConfig(project: Project) {
        val configPath = FilePath("${project.path.path}/$CODESPEAK_CONFIG_FILE")
        val exists = persistence.fileExists(configPath)
        _projectHasConfig.update { current -> current + (project.id to exists) }
    }

    /**
     * Updates [SpecStats] for a given project.
     */
    override fun updateStats(stats: SpecStats, projectId: Uuid) {
        _projectStats.update { current -> current + (projectId to stats) }
    }

    /**
     * Returns true if the project has been confirmed to have a codespeak.json.
     * Returns false if the project is unknown (not yet checked).
     */
    override fun isCodeSpeakProject(projectId: Uuid): Boolean =
        _projectHasConfig.value[projectId] == true

    /**
     * Removes cached config and stats entries for the given project.
     */
    override fun clearCache(projectId: Uuid) {
        _projectHasConfig.update { current -> current - projectId }
        _projectStats.update { current -> current - projectId }
    }
}
