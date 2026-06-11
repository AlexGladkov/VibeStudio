@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.integration

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.terminal.LocalTerminalRenderer
import studio.vibe.desktop.testutil.StubTerminalRenderer
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.testutil.RootView
import studio.vibe.desktop.testutil.SidebarView
import studio.vibe.desktop.testutil.TabBarView
import studio.vibe.desktop.ui.VibeStudioDesktopApp
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.core.common.FilePath
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/**
 * State-flow wiring integration tests.
 *
 * Each test mutates the store layer AFTER the composable is rendered and then
 * verifies the Compose tree reacts correctly.
 */
class StateFlowWiringTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var container: DesktopServiceContainer
    private lateinit var tempHome: File

    @Before
    fun setup() {
        val (c, h) = createIsolatedContainer()
        container = c
        tempHome = h
    }

    @After
    fun teardown() {
        container.dispose()
        tempHome.deleteRecursively()
    }

    // ── TabBarView reacts to projectStore.projects ─────────────────────────────

    @Test
    fun tabBar_showsNewTab_whenProjectAddedAfterRender() {
        val tempDir = Files.createTempDirectory("vs-flow-tab").toFile().also { it.deleteOnExit() }

        composeTestRule.setContent {
            VibeStudioTheme {
                TabBarView(
                    container = container,
                    onOpenProject = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(tempDir.name).assertDoesNotExist()

        runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(tempDir.name).assertExists()
    }

    @Test
    fun tabBar_removesTab_whenProjectRemovedAfterRender() {
        val tempDir = Files.createTempDirectory("vs-flow-tabrem").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }

        composeTestRule.setContent {
            VibeStudioTheme {
                TabBarView(
                    container = container,
                    onOpenProject = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(tempDir.name).assertExists()

        runBlocking { container.projectStore.removeProject(project.id) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(tempDir.name).assertDoesNotExist()
    }

    // ── RootView reacts to projectStore.activeProjectId ───────────────────────

    @Test
    fun rootView_switchesToTerminal_whenActiveProjectSet() {
        val tempDir = Files.createTempDirectory("vs-flow-root").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalTerminalRenderer provides StubTerminalRenderer) {
                VibeStudioTheme {
                    RootView(
                        container = container,
                        onOpenProject = {},
                        showGitPanel = false,
                        showSidebar = true,
                        onToggleGitPanel = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // No active project → placeholder visible.
        composeTestRule.onNodeWithText("Terminal area").assertExists()

        container.projectStore.setActiveProjectId(project.id)
        composeTestRule.waitForIdle()

        // Placeholder disappears once a project is active.
        // NOTE: May fail with Skiko interop error in headless env (expected —
        // documents that real TerminalView can't run headlessly).
        composeTestRule.onNodeWithText("Terminal area").assertDoesNotExist()
    }

    @Test
    fun rootView_switchesBackToPlaceholder_whenActiveProjectCleared() {
        val tempDir = Files.createTempDirectory("vs-flow-rootclear").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalTerminalRenderer provides StubTerminalRenderer) {
                VibeStudioTheme {
                    RootView(
                        container = container,
                        onOpenProject = {},
                        showGitPanel = false,
                        showSidebar = true,
                        onToggleGitPanel = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Active project set → no placeholder (may fail with Skiko error, noted below).

        container.projectStore.setActiveProjectId(null)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Terminal area").assertExists()
    }

    // ── VibeStudioDesktopApp — full lifecycle ──────────────────────────────────

    @Test
    fun app_switchesFromWelcomeToMain_whenProjectAddedAndActivated() {
        val tempDir = Files.createTempDirectory("vs-flow-appadd").toFile().also { it.deleteOnExit() }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalTerminalRenderer provides StubTerminalRenderer) {
                VibeStudioTheme {
                    VibeStudioDesktopApp(container = container)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("VibeStudio").assertExists()

        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Open a folder to get started").assertDoesNotExist()
    }

    @Test
    fun app_switchesBackToWelcome_whenAllProjectsRemoved() {
        val tempDir = Files.createTempDirectory("vs-flow-apprem").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalTerminalRenderer provides StubTerminalRenderer) {
                VibeStudioTheme {
                    VibeStudioDesktopApp(container = container)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Open a folder to get started").assertDoesNotExist()

        runBlocking { container.projectStore.removeProject(project.id) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("VibeStudio").assertExists()
    }

    // ── Sidebar reacts to project store ──────────────────────────────────────

    @Test
    fun sidebarView_showsProject_afterAddingItDynamically() {
        val tempDir = Files.createTempDirectory("vs-flow-sidebar").toFile().also { it.deleteOnExit() }

        composeTestRule.setContent {
            VibeStudioTheme {
                SidebarView(
                    container = container,
                    onToggleGitPanel = {},
                    onOpenProject = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(tempDir.name).assertDoesNotExist()

        runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(tempDir.name).assertExists()
    }

    // ── Store consistency ─────────────────────────────────────────────────────

    @Test
    fun activeProjectId_isNull_afterRemovingOnlyProject() {
        val tempDir = Files.createTempDirectory("vs-flow-consistency").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        assertEquals(project.id, container.projectStore.activeProjectId.value)

        runBlocking { container.projectStore.removeProject(project.id) }

        assertNull(container.projectStore.activeProjectId.value)
        assertEquals(0, container.projectStore.projects.value.size)
    }

    @Test
    fun projectsFlow_incrementsByOne_afterAddProject() {
        val initialCount = container.projectStore.projects.value.size
        val tempDir = Files.createTempDirectory("vs-flow-emit").toFile().also { it.deleteOnExit() }

        runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }

        assertEquals(initialCount + 1, container.projectStore.projects.value.size)
    }
}
