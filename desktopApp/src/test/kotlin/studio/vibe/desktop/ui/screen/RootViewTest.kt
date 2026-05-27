@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.ui.RootView
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.model.FilePath
import java.io.File
import java.nio.file.Files

/**
 * Integration tests for [RootView].
 *
 * Key bug scenario covered: when no project is active the terminal area must
 * show [TerminalPlaceholder], NOT the real TerminalView. Bug #1 caused the app
 * to show the placeholder even after addProject was called, because addProject
 * did not automatically call setActiveProjectId.
 */
class RootViewTest {

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

    // ── Smoke ─────────────────────────────────────────────────────────────────

    @Test
    fun rootView_rendersWithoutCrash_whenNoActiveProject() {
        composeTestRule.setContent {
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

        composeTestRule.waitForIdle()
    }

    // ── No active project → placeholder ──────────────────────────────────────

    /**
     * BUG CATCHER #1: When activeProjectId is null, RootView must show
     * TerminalPlaceholder.
     */
    @Test
    fun rootView_showsTerminalPlaceholder_whenNoActiveProject() {
        kotlin.test.assertNull(
            container.projectStore.activeProjectId.value,
            "Precondition: no active project",
        )

        composeTestRule.setContent {
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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Terminal area").assertExists()
    }

    // ── Active project → no placeholder ──────────────────────────────────────

    /**
     * BUG CATCHER #2: When activeProjectId is set, TerminalPlaceholder must NOT
     * be shown.
     *
     * IGNORED: TerminalView uses SwingPanel (JediTerm Swing widget) which
     * requires LocalInteropContainer — provided only inside a real
     * ApplicationWindow context. The createComposeRule() headless host does
     * not set it up. Fix: wrap TerminalView in a try/catch or provide a
     * fake SwingPanel in test configuration.
     *
     * Product-level impact: confirms TerminalView renders when activeProject
     * is set, but the Skiko interop prevents this test from running headlessly.
     */
    @Ignore("LocalInteropContainer not provided in headless test host — SwingPanel requires real Window")
    @Test
    fun rootView_hidesTerminalPlaceholder_whenActiveProjectIsSet() {
        val tempDir = Files.createTempDirectory("vs-rootview-active").toFile().also { it.deleteOnExit() }

        val project = container.projectStore.addProject(FilePath(tempDir.absolutePath))
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Terminal area").assertDoesNotExist()
    }

    // ── Sidebar toggle ────────────────────────────────────────────────────────

    @Test
    fun rootView_addProjectButtonVisible_whenSidebarShown() {
        composeTestRule.setContent {
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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Add project").assertExists()
    }

    @Test
    fun rootView_addProjectButtonHidden_whenSidebarCollapsed() {
        composeTestRule.setContent {
            VibeStudioTheme {
                RootView(
                    container = container,
                    onOpenProject = {},
                    showGitPanel = false,
                    showSidebar = false,
                    onToggleGitPanel = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Add project").assertDoesNotExist()
    }

    // ── Git panel toggle ──────────────────────────────────────────────────────

    @Ignore("LocalInteropContainer not provided in headless test host — active project triggers SwingPanel")
    @Test
    fun rootView_gitPanelHeaderHidden_whenShowGitPanelFalse() {
        val tempDir = Files.createTempDirectory("vs-rootview-git1").toFile().also { it.deleteOnExit() }
        val project = container.projectStore.addProject(FilePath(tempDir.absolutePath))
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CHANGES").assertDoesNotExist()
    }

    @Ignore("LocalInteropContainer not provided in headless test host — active project triggers SwingPanel")
    @Test
    fun rootView_gitPanelHeaderVisible_whenShowGitPanelTrueAndProjectActive() {
        val tempDir = Files.createTempDirectory("vs-rootview-git2").toFile().also { it.deleteOnExit() }
        val project = container.projectStore.addProject(FilePath(tempDir.absolutePath))
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
            VibeStudioTheme {
                RootView(
                    container = container,
                    onOpenProject = {},
                    showGitPanel = true,
                    showSidebar = true,
                    onToggleGitPanel = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CHANGES").assertExists()
    }
}
