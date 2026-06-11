@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.codespeak.data

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.feature.codespeak.domain.model.SpecStats
import studio.vibe.shared.testutil.FakePersistenceStore
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeSpeakServiceImplTest {

    private val persistence = FakePersistenceStore(appSupportDir = FilePath("/fake/support"))
    private fun service() = CodeSpeakServiceImpl(persistence)
    private fun makeProject(path: String = "/project") = Project(name = "p", path = FilePath(path))

    // ── checkConfig() ─────────────────────────────────────────────────────────────

    @Test
    fun checkConfig_configFileExists_setsProjectHasConfigTrue() = runTest {
        val project = makeProject("/repo")
        // Place codespeak.json in the project root
        persistence.writeFile(FilePath("/repo/codespeak.json"), ByteArray(0))

        service().also {
            it.checkConfig(project)
            assertTrue(it.projectHasConfig.value[project.id] == true)
        }
    }

    @Test
    fun checkConfig_configFileMissing_setsProjectHasConfigFalse() = runTest {
        val project = makeProject("/repo2")

        service().also {
            it.checkConfig(project)
            assertTrue(it.projectHasConfig.value[project.id] == false)
        }
    }

    // ── isCodeSpeakProject() ─────────────────────────────────────────────────────

    @Test
    fun isCodeSpeakProject_unknownProject_returnsFalse() {
        val svc = service()

        assertFalse(svc.isCodeSpeakProject(Uuid.random()))
    }

    @Test
    fun isCodeSpeakProject_afterClearCache_returnsFalse() = runTest {
        val project = makeProject("/repo")
        persistence.writeFile(FilePath("/repo/codespeak.json"), ByteArray(0))
        val svc = service()
        svc.checkConfig(project)
        assertTrue(svc.isCodeSpeakProject(project.id))

        svc.clearCache(project.id)

        assertFalse(svc.isCodeSpeakProject(project.id))
    }

    // ── updateStats() idempotency ─────────────────────────────────────────────────

    @Test
    fun updateStats_calledTwice_storesLatestValue() {
        val svc = service()
        val projectId = Uuid.random()
        val stats1 = SpecStats(passing = 5, total = 10, buildDate = Clock.System.now())
        val stats2 = SpecStats(passing = 10, total = 10, buildDate = Clock.System.now())

        svc.updateStats(stats1, projectId)
        svc.updateStats(stats2, projectId)

        val stored = svc.projectStats.value[projectId]
        assertTrue(stored?.passing == 10)
    }

    @Test
    fun updateStats_differentProjects_doNotOverwriteEachOther() {
        val svc = service()
        val id1 = Uuid.random()
        val id2 = Uuid.random()
        val stats1 = SpecStats(passing = 1, total = 2, buildDate = Clock.System.now())
        val stats2 = SpecStats(passing = 3, total = 4, buildDate = Clock.System.now())

        svc.updateStats(stats1, id1)
        svc.updateStats(stats2, id2)

        assertTrue(svc.projectStats.value[id1]?.passing == 1)
        assertTrue(svc.projectStats.value[id2]?.passing == 3)
    }

    // ── clearCache() ─────────────────────────────────────────────────────────────

    @Test
    fun clearCache_removesStatsEntry() = runTest {
        val svc = service()
        val project = makeProject("/repo")
        svc.updateStats(SpecStats(1, 2, Clock.System.now()), project.id)
        svc.clearCache(project.id)

        assertNull(svc.projectStats.value[project.id])
    }

    @Test
    fun clearCache_nonExistentProject_doesNotThrow() {
        val svc = service()
        // Should not throw even for an unknown ID
        svc.clearCache(Uuid.random())
    }
}
