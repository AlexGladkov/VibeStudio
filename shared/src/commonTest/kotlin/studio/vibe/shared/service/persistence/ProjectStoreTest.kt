package studio.vibe.shared.service.persistence

import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.Project
import studio.vibe.shared.model.ProjectManagerError
import studio.vibe.shared.testutil.FakePersistenceStore
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Unit tests for [ProjectStoreImpl].
 *
 * Uses [FakePersistenceStore] for all file I/O. Each test starts with a
 * fresh store instance to guarantee isolation.
 *
 * NOTE: [ProjectStoreImpl.addProject] calls [runBlocking] internally on
 * [PersistenceStore.isDirectory], so tests do not require explicit coroutine
 * scope management.
 */
@OptIn(ExperimentalUuidApi::class)
class ProjectStoreTest {

    private lateinit var persistence: FakePersistenceStore
    private lateinit var store: ProjectStoreImpl

    @BeforeTest
    fun setup() {
        persistence = FakePersistenceStore(appSupportDir = FilePath("/app-support"))
        store = ProjectStoreImpl(persistence)
    }

    @AfterTest
    fun teardown() {
        persistence.reset()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun registerDir(path: String): FilePath {
        val fp = FilePath(path)
        persistence.addDirectory(fp)
        return fp
    }

    // ── addProject ────────────────────────────────────────────────────────────

    @Test
    fun addProject_validPath_returnsProject() {
        // Arrange
        val path = registerDir("/projects/my-app")

        // Act
        val project = store.addProject(path)

        // Assert
        assertEquals("my-app", project.name)
        assertEquals(path.path, project.path.path)
    }

    @Test
    fun addProject_validPath_appearsInProjectsFlow() {
        // Arrange
        val path = registerDir("/projects/alpha")

        // Act
        store.addProject(path)

        // Assert
        assertEquals(1, store.projects.value.size)
        assertEquals("alpha", store.projects.value[0].name)
    }

    @Test
    fun addProject_invalidPath_throwsInvalidPath() {
        // Arrange — path is NOT registered as a directory
        val path = FilePath("/nonexistent/path")

        // Act + Assert
        assertFailsWith<ProjectManagerError.InvalidPath> {
            store.addProject(path)
        }
    }

    @Test
    fun addProject_duplicatePath_throwsDuplicate() {
        // Arrange
        val path = registerDir("/projects/dup")
        store.addProject(path)

        // Act + Assert
        assertFailsWith<ProjectManagerError.Duplicate> {
            store.addProject(path)
        }
    }

    @Test
    fun addProject_atLimit_throwsProjectLimitReached() {
        // Arrange — add 32 projects (MAX_PROJECTS)
        repeat(32) { i ->
            val p = registerDir("/projects/p$i")
            store.addProject(p)
        }
        val overflow = registerDir("/projects/p_overflow")

        // Act + Assert
        assertFailsWith<ProjectManagerError.ProjectLimitReached> {
            store.addProject(overflow)
        }
    }

    // ── removeProject ─────────────────────────────────────────────────────────

    @Test
    fun removeProject_existingId_removesFromFlow() {
        // Arrange
        val path = registerDir("/projects/remove-me")
        val project = store.addProject(path)

        // Act
        store.removeProject(project.id)

        // Assert
        assertTrue(store.projects.value.isEmpty())
    }

    @Test
    fun removeProject_nonExistentId_throwsNotFound() {
        // Arrange
        val bogusId = Uuid.random()

        // Act + Assert
        assertFailsWith<ProjectManagerError.NotFound> {
            store.removeProject(bogusId)
        }
    }

    @Test
    fun removeProject_activeProject_setsActiveToNextProject() {
        // Arrange
        val p1 = store.addProject(registerDir("/projects/p1"))
        val p2 = store.addProject(registerDir("/projects/p2"))
        store.setActiveProjectId(p1.id)

        // Act
        store.removeProject(p1.id)

        // Assert — active should now be p2 (the remaining project)
        assertEquals(p2.id, store.activeProjectId.value)
    }

    @Test
    fun removeProject_activeProjectNoRemaining_setsActiveToNull() {
        // Arrange
        val p = store.addProject(registerDir("/projects/solo"))
        store.setActiveProjectId(p.id)

        // Act
        store.removeProject(p.id)

        // Assert
        assertNull(store.activeProjectId.value)
    }

    // ── updateProject ─────────────────────────────────────────────────────────

    @Test
    fun updateProject_existingId_appliesMutation() {
        // Arrange
        val path = registerDir("/projects/updatable")
        val original = store.addProject(path)

        // Act
        store.updateProject(original.id) { it.copy(shellPath = "/bin/bash") }

        // Assert
        val updated = store.project(original.id)
        assertEquals("/bin/bash", updated?.shellPath)
    }

    @Test
    fun updateProject_nonExistentId_throwsNotFound() {
        // Arrange
        val bogusId = Uuid.random()

        // Act + Assert
        assertFailsWith<ProjectManagerError.NotFound> {
            store.updateProject(bogusId) { it }
        }
    }

    // ── project(id) / project(path) lookups ───────────────────────────────────

    @Test
    fun projectById_existingId_returnsProject() {
        // Arrange
        val path = registerDir("/projects/lookup")
        val added = store.addProject(path)

        // Act + Assert
        assertNotNull(store.project(added.id))
        assertEquals(added.id, store.project(added.id)?.id)
    }

    @Test
    fun projectById_unknownId_returnsNull() {
        assertNull(store.project(Uuid.random()))
    }

    @Test
    fun projectByPath_existingPath_returnsProject() {
        // Arrange
        val path = registerDir("/projects/by-path")
        store.addProject(path)

        // Act + Assert
        assertNotNull(store.project(path))
    }

    @Test
    fun projectByPath_unknownPath_returnsNull() {
        assertNull(store.project(FilePath("/unknown")))
    }

    // ── setActiveProjectId ────────────────────────────────────────────────────

    @Test
    fun setActiveProjectId_validId_updatesFlow() {
        // Arrange
        val project = store.addProject(registerDir("/projects/active"))

        // Act
        store.setActiveProjectId(project.id)

        // Assert
        assertEquals(project.id, store.activeProjectId.value)
    }

    @Test
    fun setActiveProjectId_null_clearsActive() {
        // Arrange
        val project = store.addProject(registerDir("/projects/p"))
        store.setActiveProjectId(project.id)

        // Act
        store.setActiveProjectId(null)

        // Assert
        assertNull(store.activeProjectId.value)
    }

    @Test
    fun setActiveProjectId_validProject_appearsInRecentHistory() {
        // Arrange
        val p1 = store.addProject(registerDir("/projects/h1"))
        val p2 = store.addProject(registerDir("/projects/h2"))

        // Act
        store.setActiveProjectId(p1.id)
        store.setActiveProjectId(p2.id)

        // Assert — most-recently opened is first
        assertEquals(p2.id, store.recentHistory.value[0].id)
        assertEquals(p1.id, store.recentHistory.value[1].id)
    }

    @Test
    fun recentHistory_cappedAtTenEntries() {
        // Arrange — add 11 projects and activate each
        val projects = (0..10).map { i ->
            store.addProject(registerDir("/projects/r$i"))
        }
        projects.forEach { store.setActiveProjectId(it.id) }

        // Assert
        assertEquals(10, store.recentHistory.value.size)
    }

    // ── moveProjects ──────────────────────────────────────────────────────────

    @Test
    fun moveProjects_singleItem_movedToCorrectPosition() {
        // Arrange — [p0, p1, p2]
        val p0 = store.addProject(registerDir("/projects/m0"))
        val p1 = store.addProject(registerDir("/projects/m1"))
        val p2 = store.addProject(registerDir("/projects/m2"))

        // Act — move index 0 to destination 3 (end)
        store.moveProjects(setOf(0), toDestination = 3)

        // Assert — new order: [p1, p2, p0]
        val ids = store.projects.value.map { it.id }
        assertEquals(listOf(p1.id, p2.id, p0.id), ids)
    }

    @Test
    fun moveProjects_emptySet_noChange() {
        // Arrange
        val p0 = store.addProject(registerDir("/projects/nm0"))
        val p1 = store.addProject(registerDir("/projects/nm1"))

        // Act
        store.moveProjects(emptySet(), toDestination = 0)

        // Assert
        assertEquals(listOf(p0.id, p1.id), store.projects.value.map { it.id })
    }

    // ── save / load round-trip ────────────────────────────────────────────────

    @Test
    fun saveAndLoad_roundTrip_preservesProjects() {
        // Arrange
        val path1 = registerDir("/projects/rt1")
        val path2 = registerDir("/projects/rt2")
        store.addProject(path1)
        store.addProject(path2)

        // Act — save state and load into a new store instance
        store.save()
        val newStore = ProjectStoreImpl(persistence)
        newStore.load()

        // Assert
        assertEquals(2, newStore.projects.value.size)
        val paths = newStore.projects.value.map { it.path.path }
        assertContains(paths, path1.path)
        assertContains(paths, path2.path)
    }

    @Test
    fun load_missingFile_startsWithEmptyList() {
        // Arrange — no files in FakePersistenceStore
        // Act
        store.load()

        // Assert
        assertTrue(store.projects.value.isEmpty())
    }
}
