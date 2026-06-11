package studio.vibe.shared.feature.tabbar.data

import studio.vibe.shared.feature.tabbar.presentation.FreeTab
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.feature.tabbar.data.FreeTabStoreImpl
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Unit tests for [FreeTabStoreImpl].
 *
 * All tests are synchronous — no coroutine scope required.
 * Each test creates a fresh [FreeTabStoreImpl] for isolation.
 */
@OptIn(ExperimentalUuidApi::class)
class FreeTabStoreTest {

    private fun freshStore(): FreeTabStoreImpl = FreeTabStoreImpl()

    private fun makeProject(name: String = "Test"): Project = Project(
        name = name,
        path = FilePath("/projects/$name"),
    )

    // ── createFreeTab ─────────────────────────────────────────────────────────

    @Test
    fun createFreeTab_firstTab_titledTerminal() {
        // Arrange
        val store = freshStore()

        // Act
        val tab = store.createFreeTab()

        // Assert
        assertEquals("Terminal", tab.title)
    }

    @Test
    fun createFreeTab_secondTab_titledTerminal2() {
        // Arrange
        val store = freshStore()
        store.createFreeTab() // Terminal

        // Act
        val second = store.createFreeTab()

        // Assert
        assertEquals("Terminal 2", second.title)
    }

    @Test
    fun createFreeTab_thirdTab_titledTerminal3() {
        // Arrange
        val store = freshStore()
        store.createFreeTab()
        store.createFreeTab()

        // Act
        val third = store.createFreeTab()

        // Assert
        assertEquals("Terminal 3", third.title)
    }

    @Test
    fun createFreeTab_addedToFreeTabsList() {
        // Arrange
        val store = freshStore()

        // Act
        val tab = store.createFreeTab()

        // Assert
        assertEquals(1, store.freeTabs.size)
        assertEquals(tab.id, store.freeTabs[0].id)
    }

    @Test
    fun createFreeTab_multipleCreations_allAddedToList() {
        // Arrange
        val store = freshStore()

        // Act
        repeat(3) { store.createFreeTab() }

        // Assert
        assertEquals(3, store.freeTabs.size)
    }

    @Test
    fun createFreeTab_afterRemovingMiddleTab_reusesMissingNumber() {
        // Arrange — create Terminal, Terminal 2, Terminal 3
        val store = freshStore()
        val t1 = store.createFreeTab() // Terminal
        val t2 = store.createFreeTab() // Terminal 2
        store.createFreeTab()          // Terminal 3

        // Remove Terminal 2
        store.removeFreeTab(t2.id)

        // Act — next tab should fill gap at "Terminal 2"
        val next = store.createFreeTab()

        // Assert
        assertEquals("Terminal 2", next.title)
    }

    @Test
    fun createFreeTab_eachTabHasUniqueId() {
        // Arrange
        val store = freshStore()

        // Act
        val tabs = List(5) { store.createFreeTab() }

        // Assert
        val ids = tabs.map { it.id }.toSet()
        assertEquals(5, ids.size, "All tab IDs must be unique")
    }

    // ── removeFreeTab ─────────────────────────────────────────────────────────

    @Test
    fun removeFreeTab_existingId_removedFromList() {
        // Arrange
        val store = freshStore()
        val tab = store.createFreeTab()

        // Act
        store.removeFreeTab(tab.id)

        // Assert
        assertTrue(store.freeTabs.isEmpty())
    }

    @Test
    fun removeFreeTab_nonExistentId_noChange() {
        // Arrange
        val store = freshStore()
        store.createFreeTab()

        // Act — remove a bogus ID
        store.removeFreeTab(Uuid.random())

        // Assert — original tab still present
        assertEquals(1, store.freeTabs.size)
    }

    @Test
    fun removeFreeTab_oneOfMany_onlyTargetRemoved() {
        // Arrange
        val store = freshStore()
        val t1 = store.createFreeTab()
        val t2 = store.createFreeTab()
        val t3 = store.createFreeTab()

        // Act
        store.removeFreeTab(t2.id)

        // Assert
        assertEquals(2, store.freeTabs.size)
        val ids = store.freeTabs.map { it.id }
        assertContains(ids, t1.id)
        assertFalse(ids.contains(t2.id))
        assertContains(ids, t3.id)
    }

    // ── isFreeTab ─────────────────────────────────────────────────────────────

    @Test
    fun isFreeTab_existingId_returnsTrue() {
        // Arrange
        val store = freshStore()
        val tab = store.createFreeTab()

        // Act + Assert
        assertTrue(store.isFreeTab(tab.id))
    }

    @Test
    fun isFreeTab_unknownId_returnsFalse() {
        // Arrange
        val store = freshStore()

        // Act + Assert
        assertFalse(store.isFreeTab(Uuid.random()))
    }

    @Test
    fun isFreeTab_afterRemoval_returnsFalse() {
        // Arrange
        val store = freshStore()
        val tab = store.createFreeTab()
        store.removeFreeTab(tab.id)

        // Act + Assert
        assertFalse(store.isFreeTab(tab.id))
    }

    // ── nextActiveId ──────────────────────────────────────────────────────────

    @Test
    fun nextActiveId_remainingTabsExist_returnsLastRemainingTabId() {
        // Arrange
        val store = freshStore()
        val t1 = store.createFreeTab()
        val t2 = store.createFreeTab()
        val t3 = store.createFreeTab()

        // Act — remove t2; remaining are t1, t3
        val nextId = store.nextActiveId(afterRemovedId = t2.id, projects = emptyList())

        // Assert — last() of remaining [t1, t3] is t3
        assertEquals(t3.id, nextId)
    }

    @Test
    fun nextActiveId_noRemainingTabs_fallsBackToFirstProject() {
        // Arrange
        val store = freshStore()
        val tab = store.createFreeTab()
        val project = makeProject("SomeProject")

        // Act
        val nextId = store.nextActiveId(afterRemovedId = tab.id, projects = listOf(project))

        // Assert
        assertEquals(project.id, nextId)
    }

    @Test
    fun nextActiveId_noRemainingTabsAndNoProjects_returnsNull() {
        // Arrange
        val store = freshStore()
        val tab = store.createFreeTab()

        // Act
        val nextId = store.nextActiveId(afterRemovedId = tab.id, projects = emptyList())

        // Assert
        assertNull(nextId)
    }

    // ── moveFreeTabs ──────────────────────────────────────────────────────────

    @Test
    fun moveFreeTabs_moveFirstToEnd_correctOrder() {
        // Arrange — [t0, t1, t2]
        val store = freshStore()
        val t0 = store.createFreeTab()
        val t1 = store.createFreeTab()
        val t2 = store.createFreeTab()

        // Act — move index 0 to destination 3 (end)
        store.moveFreeTabs(setOf(0), toDestination = 3)

        // Assert — new order: [t1, t2, t0]
        val ids = store.freeTabs.map { it.id }
        assertEquals(listOf(t1.id, t2.id, t0.id), ids)
    }

    @Test
    fun moveFreeTabs_moveLastToFront_correctOrder() {
        // Arrange — [t0, t1, t2]
        val store = freshStore()
        val t0 = store.createFreeTab()
        val t1 = store.createFreeTab()
        val t2 = store.createFreeTab()

        // Act — move index 2 to destination 0 (front)
        store.moveFreeTabs(setOf(2), toDestination = 0)

        // Assert — new order: [t2, t0, t1]
        val ids = store.freeTabs.map { it.id }
        assertEquals(listOf(t2.id, t0.id, t1.id), ids)
    }

    @Test
    fun moveFreeTabs_emptySet_noChange() {
        // Arrange
        val store = freshStore()
        val t0 = store.createFreeTab()
        val t1 = store.createFreeTab()

        // Act
        store.moveFreeTabs(emptySet(), toDestination = 0)

        // Assert
        assertEquals(listOf(t0.id, t1.id), store.freeTabs.map { it.id })
    }

    // ── title sequencing after gaps ───────────────────────────────────────────

    @Test
    fun createFreeTab_afterRemovingAllThenRecreating_startsFromTerminal() {
        // Arrange
        val store = freshStore()
        val t1 = store.createFreeTab()
        store.removeFreeTab(t1.id)

        // Act
        val fresh = store.createFreeTab()

        // Assert — numbering restarts from 1 (i.e. "Terminal")
        assertEquals("Terminal", fresh.title)
    }
}
