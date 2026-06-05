package studio.vibe.shared.service.persistence

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.ProjectManagerError
import studio.vibe.shared.testutil.FakePersistenceStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Unit tests for [ProjectStoreImpl].
 *
 * Uses [FakePersistenceStore] for all file I/O and a [TestScope] to drive
 * background persistence writes deterministically.
 */
@OptIn(ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProjectStoreTest {

    private lateinit var persistence: FakePersistenceStore
    private lateinit var testScope: TestScope
    private lateinit var store: ProjectStoreImpl

    @BeforeTest
    fun setup() {
        persistence = FakePersistenceStore(appSupportDir = FilePath("/app-support"))
        testScope = TestScope(UnconfinedTestDispatcher())
        store = ProjectStoreImpl(persistence = persistence, scope = testScope)
    }

    @AfterTest
    fun teardown() {
        persistence.reset()
    }

    private fun registerDir(path: String): FilePath {
        val fp = FilePath(path)
        persistence.addDirectory(fp)
        return fp
    }

    // ── addProject ────────────────────────────────────────────────────────────

    @Test
    fun addProject_validPath_returnsProject() = runTest {
        val path = registerDir("/projects/my-app")
        val project = store.addProject(path)
        assertEquals("my-app", project.name)
        assertEquals(path.path, project.path.path)
    }

    @Test
    fun addProject_validPath_appearsInProjectsFlow() = runTest {
        val path = registerDir("/projects/alpha")
        store.addProject(path)
        assertEquals(1, store.projects.value.size)
        assertEquals("alpha", store.projects.value[0].name)
    }

    @Test
    fun addProject_invalidPath_throwsInvalidPath() = runTest {
        val path = FilePath("/nonexistent/path")
        assertFailsWith<ProjectManagerError.InvalidPath> {
            store.addProject(path)
        }
    }

    @Test
    fun addProject_duplicatePath_throwsDuplicate() = runTest {
        val path = registerDir("/projects/dup")
        store.addProject(path)
        assertFailsWith<ProjectManagerError.Duplicate> {
            store.addProject(path)
        }
    }

    @Test
    fun addProject_atLimit_throwsProjectLimitReached() = runTest {
        repeat(32) { i ->
            val p = registerDir("/projects/p$i")
            store.addProject(p)
        }
        val overflow = registerDir("/projects/p_overflow")
        assertFailsWith<ProjectManagerError.ProjectLimitReached> {
            store.addProject(overflow)
        }
    }

    // ── removeProject ─────────────────────────────────────────────────────────

    @Test
    fun removeProject_existingId_removesFromFlow() = runTest {
        val path = registerDir("/projects/remove-me")
        val project = store.addProject(path)
        store.removeProject(project.id)
        assertTrue(store.projects.value.isEmpty())
    }

    @Test
    fun removeProject_nonExistentId_throwsNotFound() = runTest {
        val bogusId = Uuid.random()
        assertFailsWith<ProjectManagerError.NotFound> {
            store.removeProject(bogusId)
        }
    }

    @Test
    fun removeProject_activeProject_setsActiveToNextProject() = runTest {
        val p1 = store.addProject(registerDir("/projects/p1"))
        val p2 = store.addProject(registerDir("/projects/p2"))
        store.setActiveProjectId(p1.id)
        store.removeProject(p1.id)
        assertEquals(p2.id, store.activeProjectId.value)
    }

    @Test
    fun removeProject_activeProjectNoRemaining_setsActiveToNull() = runTest {
        val p = store.addProject(registerDir("/projects/solo"))
        store.setActiveProjectId(p.id)
        store.removeProject(p.id)
        assertNull(store.activeProjectId.value)
    }

    // ── updateProject ─────────────────────────────────────────────────────────

    @Test
    fun updateProject_existingId_appliesMutation() = runTest {
        val path = registerDir("/projects/updatable")
        val original = store.addProject(path)
        store.updateProject(original.id) { it.copy(shellPath = "/bin/bash") }
        val updated = store.project(original.id)
        assertEquals("/bin/bash", updated?.shellPath)
    }

    @Test
    fun updateProject_nonExistentId_throwsNotFound() = runTest {
        val bogusId = Uuid.random()
        assertFailsWith<ProjectManagerError.NotFound> {
            store.updateProject(bogusId) { it }
        }
    }

    // ── project(id) / project(path) lookups ───────────────────────────────────

    @Test
    fun projectById_existingId_returnsProject() = runTest {
        val path = registerDir("/projects/lookup")
        val added = store.addProject(path)
        assertNotNull(store.project(added.id))
        assertEquals(added.id, store.project(added.id)?.id)
    }

    @Test
    fun projectById_unknownId_returnsNull() {
        assertNull(store.project(Uuid.random()))
    }

    @Test
    fun projectByPath_existingPath_returnsProject() = runTest {
        val path = registerDir("/projects/by-path")
        store.addProject(path)
        assertNotNull(store.project(path))
    }

    @Test
    fun projectByPath_unknownPath_returnsNull() {
        assertNull(store.project(FilePath("/unknown")))
    }

    // ── setActiveProjectId ────────────────────────────────────────────────────

    @Test
    fun setActiveProjectId_validId_updatesFlow() = runTest {
        val project = store.addProject(registerDir("/projects/active"))
        store.setActiveProjectId(project.id)
        assertEquals(project.id, store.activeProjectId.value)
    }

    @Test
    fun setActiveProjectId_null_clearsActive() = runTest {
        val project = store.addProject(registerDir("/projects/p"))
        store.setActiveProjectId(project.id)
        store.setActiveProjectId(null)
        assertNull(store.activeProjectId.value)
    }

    @Test
    fun setActiveProjectId_validProject_appearsInRecentHistory() = runTest {
        val p1 = store.addProject(registerDir("/projects/h1"))
        val p2 = store.addProject(registerDir("/projects/h2"))
        store.setActiveProjectId(p1.id)
        store.setActiveProjectId(p2.id)
        assertEquals(p2.id, store.recentHistory.value[0].id)
        assertEquals(p1.id, store.recentHistory.value[1].id)
    }

    @Test
    fun recentHistory_cappedAtTenEntries() = runTest {
        val projects = (0..10).map { i ->
            store.addProject(registerDir("/projects/r$i"))
        }
        projects.forEach { store.setActiveProjectId(it.id) }
        assertEquals(10, store.recentHistory.value.size)
    }

    // ── moveProjects ──────────────────────────────────────────────────────────

    @Test
    fun moveProjects_singleItem_movedToCorrectPosition() = runTest {
        val p0 = store.addProject(registerDir("/projects/m0"))
        val p1 = store.addProject(registerDir("/projects/m1"))
        val p2 = store.addProject(registerDir("/projects/m2"))

        store.moveProjects(setOf(0), toDestination = 3)

        val ids = store.projects.value.map { it.id }
        assertEquals(listOf(p1.id, p2.id, p0.id), ids)
    }

    @Test
    fun moveProjects_emptySet_noChange() = runTest {
        val p0 = store.addProject(registerDir("/projects/nm0"))
        val p1 = store.addProject(registerDir("/projects/nm1"))

        store.moveProjects(emptySet(), toDestination = 0)

        assertEquals(listOf(p0.id, p1.id), store.projects.value.map { it.id })
    }

    // ── save / load round-trip ────────────────────────────────────────────────

    @Test
    fun saveAndLoad_roundTrip_preservesProjects() = runTest {
        val path1 = registerDir("/projects/rt1")
        val path2 = registerDir("/projects/rt2")
        store.addProject(path1)
        store.addProject(path2)

        store.save()

        val newStore = ProjectStoreImpl(persistence = persistence, scope = testScope)
        newStore.load()

        assertEquals(2, newStore.projects.value.size)
        val paths = newStore.projects.value.map { it.path.path }
        assertContains(paths, path1.path)
        assertContains(paths, path2.path)
    }

    @Test
    fun load_missingFile_startsWithEmptyList() = runTest {
        store.load()
        assertTrue(store.projects.value.isEmpty())
    }

    // ── P1: StateFlow replay on new subscriber ────────────────────────────────

    @Test
    fun projectsFlow_newSubscriber_receivesCurrentValueImmediately() = runTest {
        // Arrange — add a project so the flow has non-empty state
        val path = registerDir("/projects/replay-test")
        store.addProject(path)
        assertEquals(1, store.projects.value.size) // sanity

        // Act — collect the first emission (StateFlow has replay=1 by contract)
        val received = mutableListOf<Int>()
        val job = this.launch(start = CoroutineStart.UNDISPATCHED) {
            store.projects.collect { projects -> received.add(projects.size) }
        }
        // UnconfinedTestDispatcher runs the collect inline so received is already populated
        job.cancel()

        // Assert — the subscriber must receive the current value immediately (replay=1)
        assertTrue(received.isNotEmpty(), "New subscriber must receive at least one emission")
        assertEquals(1, received.first(), "First emission must be the current state (1 project)")
    }

    @Test
    fun activeProjectIdFlow_newSubscriber_receivesCurrentValueImmediately() = runTest {
        // Arrange
        val project = store.addProject(registerDir("/projects/active-replay"))
        store.setActiveProjectId(project.id)

        // Act — new subscriber should immediately get the current value
        val received = mutableListOf<kotlin.uuid.Uuid?>()
        val job = this.launch(start = CoroutineStart.UNDISPATCHED) {
            store.activeProjectId.collect { id -> received.add(id) }
        }
        job.cancel()

        // Assert
        assertTrue(received.isNotEmpty())
        assertEquals(project.id, received.first())
    }
}
