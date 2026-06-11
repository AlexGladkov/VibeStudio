@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.testutil.SidebarView
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.core.common.FilePath
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * Integration tests for [SidebarView].
 *
 * Verifies icon strip rendering (Bug #3: icons cut off when strip too narrow),
 * project listing, and active-project click handling.
 */
class SidebarViewTest {

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

    @Test
    fun sidebarView_rendersWithoutCrash() {
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
    }

    /**
     * BUG CATCHER #3: Icon strip too narrow → icons cut off.
     */
    @Test
    fun sidebarView_iconStrip_showsFilesTabIcon() {
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
        composeTestRule.onNodeWithContentDescription("Files").assertExists()
    }

    @Test
    fun sidebarView_iconStrip_showsGitTabIcon() {
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
        composeTestRule.onNodeWithContentDescription("Git").assertExists()
    }

    @Test
    fun sidebarView_iconStrip_showsSpecsTabIcon() {
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
        composeTestRule.onNodeWithContentDescription("Specs").assertExists()
    }

    @Test
    fun sidebarView_iconStrip_showsAddProjectButton() {
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
        composeTestRule.onNodeWithContentDescription("Add project").assertExists()
    }

    @Test
    fun sidebarView_showsProjectName_whenProjectExists() {
        val tempDir = Files.createTempDirectory("vs-sidebar-project").toFile().also { it.deleteOnExit() }
        runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }

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
        composeTestRule.onNodeWithText(tempDir.name).assertExists()
    }

    @Test
    fun sidebarView_showsMultipleProjects_whenMultipleProjectsExist() {
        val tempDir1 = Files.createTempDirectory("vs-sidebar-p1").toFile().also { it.deleteOnExit() }
        val tempDir2 = Files.createTempDirectory("vs-sidebar-p2").toFile().also { it.deleteOnExit() }

        runBlocking { container.projectStore.addProject(FilePath(tempDir1.absolutePath)) }
        runBlocking { container.projectStore.addProject(FilePath(tempDir2.absolutePath)) }

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
        composeTestRule.onNodeWithText(tempDir1.name).assertExists()
        composeTestRule.onNodeWithText(tempDir2.name).assertExists()
    }

    @Test
    fun sidebarView_clickingProjectHeader_updatesActiveProject() {
        val tempDir1 = Files.createTempDirectory("vs-sidebar-click1").toFile().also { it.deleteOnExit() }
        val tempDir2 = Files.createTempDirectory("vs-sidebar-click2").toFile().also { it.deleteOnExit() }

        val project1 = runBlocking { container.projectStore.addProject(FilePath(tempDir1.absolutePath)) }
        val project2 = runBlocking { container.projectStore.addProject(FilePath(tempDir2.absolutePath)) }

        container.projectStore.setActiveProjectId(project1.id)

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
        composeTestRule.onNodeWithText(tempDir2.name).performClick()
        composeTestRule.waitForIdle()

        assertEquals(project2.id, container.projectStore.activeProjectId.value)
    }
}
