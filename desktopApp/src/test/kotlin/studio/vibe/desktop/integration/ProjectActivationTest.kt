@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.integration

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.ProjectManagerError
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

/**
 * KEY integration tests — these would have caught Bug #1 and Bug #2:
 *
 * Bug #1: Project not auto-activated on startup.
 *   → Container.projectStore.load() does NOT call setActiveProjectId.
 *   → Test: fresh container must have null activeProjectId.
 *
 * Bug #2: addProject doesn't activate the project.
 *   → addProject returns the project but callers must call setActiveProjectId.
 *   → Test: after addProject, activeProjectId is still null.
 */
class ProjectActivationTest {

    private lateinit var container: DesktopServiceContainer
    private lateinit var tempHome: File

    @BeforeTest
    fun setup() {
        val (c, h) = createIsolatedContainer()
        container = c
        tempHome = h
    }

    @AfterTest
    fun teardown() {
        container.dispose()
        tempHome.deleteRecursively()
    }

    // ── addProject contract ───────────────────────────────────────────────────

    /**
     * BUG CATCHER #2: addProject must NOT automatically set activeProjectId.
     */
    @Test
    fun addProject_doesNotAutoActivate() {
        val tempDir = Files.createTempDirectory("vs-activation-add").toFile().also { it.deleteOnExit() }

        runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }

        assertNull(
            container.projectStore.activeProjectId.value,
            "addProject must not auto-activate. Caller must call setActiveProjectId.",
        )
    }

    @Test
    fun addProject_thenSetActiveProjectId_activatesProject() {
        val tempDir = Files.createTempDirectory("vs-activation-set").toFile().also { it.deleteOnExit() }

        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        assertEquals(project.id, container.projectStore.activeProjectId.value)
    }

    @Test
    fun addProject_projectAppearsInProjectsList() {
        val tempDir = Files.createTempDirectory("vs-activation-list").toFile().also { it.deleteOnExit() }

        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }

        val projects = container.projectStore.projects.value
        assertEquals(1, projects.size)
        assertEquals(project.id, projects.first().id)
    }

    @Test
    fun addProject_returnsProjectWithCorrectName() {
        val tempDir = Files.createTempDirectory("vs-activation-name").toFile().also { it.deleteOnExit() }

        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }

        assertEquals(tempDir.name, project.name)
    }

    // ── Duplicate project handling ────────────────────────────────────────────

    @Test
    fun addProject_duplicate_throwsDuplicateError() {
        val tempDir = Files.createTempDirectory("vs-activation-dup").toFile().also { it.deleteOnExit() }

        runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }

        assertFailsWith<ProjectManagerError.Duplicate> {
            runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        }
    }

    @Test
    fun addProject_duplicateError_containsExistingProjectId() {
        val tempDir = Files.createTempDirectory("vs-activation-dupid").toFile().also { it.deleteOnExit() }

        val original = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }

        val error = runCatching {
            runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        }.exceptionOrNull()

        assertNotNull(error)
        assert(error is ProjectManagerError.Duplicate) { "Expected Duplicate but got ${error::class}" }
        assertEquals(original.id, (error as ProjectManagerError.Duplicate).existingId)
    }

    @Test
    fun addProject_handleDuplicate_activatesExistingProject() {
        val tempDir = Files.createTempDirectory("vs-activation-dupact").toFile().also { it.deleteOnExit() }

        val original = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        try {
            runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        } catch (e: ProjectManagerError.Duplicate) {
            container.projectStore.setActiveProjectId(e.existingId)
        }

        assertEquals(original.id, container.projectStore.activeProjectId.value)
    }

    // ── Remove active project ─────────────────────────────────────────────────

    @Test
    fun removeProject_activeProject_switchesToNextProject() {
        val dir1 = Files.createTempDirectory("vs-activation-rem1").toFile().also { it.deleteOnExit() }
        val dir2 = Files.createTempDirectory("vs-activation-rem2").toFile().also { it.deleteOnExit() }

        val project1 = runBlocking { container.projectStore.addProject(FilePath(dir1.absolutePath)) }
        val project2 = runBlocking { container.projectStore.addProject(FilePath(dir2.absolutePath)) }
        container.projectStore.setActiveProjectId(project1.id)

        runBlocking { container.projectStore.removeProject(project1.id) }

        assertEquals(project2.id, container.projectStore.activeProjectId.value)
    }

    @Test
    fun removeProject_lastProject_activeBecomesNull() {
        val tempDir = Files.createTempDirectory("vs-activation-last").toFile().also { it.deleteOnExit() }

        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        runBlocking { container.projectStore.removeProject(project.id) }

        assertNull(container.projectStore.activeProjectId.value)
    }

    @Test
    fun removeProject_nonActive_doesNotChangeActiveProject() {
        val dir1 = Files.createTempDirectory("vs-activation-nonact1").toFile().also { it.deleteOnExit() }
        val dir2 = Files.createTempDirectory("vs-activation-nonact2").toFile().also { it.deleteOnExit() }

        val project1 = runBlocking { container.projectStore.addProject(FilePath(dir1.absolutePath)) }
        val project2 = runBlocking { container.projectStore.addProject(FilePath(dir2.absolutePath)) }
        container.projectStore.setActiveProjectId(project1.id)

        runBlocking { container.projectStore.removeProject(project2.id) }

        assertEquals(project1.id, container.projectStore.activeProjectId.value)
    }

    // ── Fresh container state ─────────────────────────────────────────────────

    /**
     * BUG CATCHER #1: Fresh container must start with null activeProjectId.
     */
    @Test
    fun freshContainer_activeProjectId_isNull() {
        assertNull(
            container.projectStore.activeProjectId.value,
            "A fresh container must start with null activeProjectId — " +
                "the caller is responsible for activation.",
        )
    }

    @Test
    fun freshContainer_projectsList_isEmpty_inIsolatedEnv() {
        assertEquals(
            0,
            container.projectStore.projects.value.size,
            "Isolated container must start with zero projects",
        )
    }

    // ── setActiveProjectId ────────────────────────────────────────────────────

    @Test
    fun setActiveProjectId_updatesStateFlow() {
        val tempDir = Files.createTempDirectory("vs-activation-flow").toFile().also { it.deleteOnExit() }

        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        assertNull(container.projectStore.activeProjectId.value)

        container.projectStore.setActiveProjectId(project.id)

        assertEquals(project.id, container.projectStore.activeProjectId.value)
    }

    @Test
    fun setActiveProjectId_null_clearsActiveProject() {
        val tempDir = Files.createTempDirectory("vs-activation-clear").toFile().also { it.deleteOnExit() }

        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        container.projectStore.setActiveProjectId(null)

        assertNull(container.projectStore.activeProjectId.value)
    }

    // ── Multiple addProject calls ─────────────────────────────────────────────

    @Test
    fun addMultipleProjects_allAppearInProjectsList() {
        val dirs = (1..3).map {
            Files.createTempDirectory("vs-activation-multi$it").toFile().also { d -> d.deleteOnExit() }
        }

        dirs.forEach { runBlocking { container.projectStore.addProject(FilePath(it.absolutePath)) } }

        assertEquals(3, container.projectStore.projects.value.size)
    }
}
