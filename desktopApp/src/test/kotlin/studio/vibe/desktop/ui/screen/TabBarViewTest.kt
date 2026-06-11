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
import studio.vibe.desktop.testutil.TabBarView
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.core.common.FilePath
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * Integration tests for [TabBarView].
 */
class TabBarViewTest {

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
    fun tabBarView_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                TabBarView(
                    container = container,
                    onOpenProject = {},
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun tabBarView_addButtonIsVisible() {
        composeTestRule.setContent {
            VibeStudioTheme {
                TabBarView(
                    container = container,
                    onOpenProject = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("New project").assertExists()
    }

    @Test
    fun tabBarView_addButtonOpensRecentsPopover_thenForwardsToOnOpenProject() {
        var callbackFired = false

        composeTestRule.setContent {
            VibeStudioTheme {
                TabBarView(
                    container = container,
                    onOpenProject = { callbackFired = true },
                )
            }
        }

        composeTestRule.waitForIdle()
        // 1. Click the + button — should expand AddProjectPopover, not fire the callback yet.
        composeTestRule.onNodeWithContentDescription("New project").performClick()
        composeTestRule.waitForIdle()
        kotlin.test.assertFalse(
            callbackFired,
            "Clicking + should open the popover, not bypass it directly",
        )

        // 2. Click "Open Folder..." inside the popover — this is what now triggers onOpenProject.
        composeTestRule.onNodeWithText("Open Folder...").performClick()
        composeTestRule.waitForIdle()
        kotlin.test.assertTrue(callbackFired)
    }

    @Test
    fun tabBarView_showsTabForEachProject() {
        val tempDir1 = Files.createTempDirectory("vs-tab-p1").toFile().also { it.deleteOnExit() }
        val tempDir2 = Files.createTempDirectory("vs-tab-p2").toFile().also { it.deleteOnExit() }

        runBlocking { container.projectStore.addProject(FilePath(tempDir1.absolutePath)) }
        runBlocking { container.projectStore.addProject(FilePath(tempDir2.absolutePath)) }

        composeTestRule.setContent {
            VibeStudioTheme {
                TabBarView(
                    container = container,
                    onOpenProject = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(tempDir1.name).assertExists()
        composeTestRule.onNodeWithText(tempDir2.name).assertExists()
    }

    @Test
    fun tabBarView_noCloseTabs_whenNoProjects() {
        composeTestRule.setContent {
            VibeStudioTheme {
                TabBarView(
                    container = container,
                    onOpenProject = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Close tab").assertDoesNotExist()
    }

    @Test
    fun tabBarView_closeButtonExists_forActiveTab() {
        val tempDir = Files.createTempDirectory("vs-tab-close").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
            VibeStudioTheme {
                TabBarView(
                    container = container,
                    onOpenProject = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Close tab").assertExists()
    }

    @Test
    fun tabBarView_clickingTabSetsActiveProject() {
        val tempDir1 = Files.createTempDirectory("vs-tab-click1").toFile().also { it.deleteOnExit() }
        val tempDir2 = Files.createTempDirectory("vs-tab-click2").toFile().also { it.deleteOnExit() }

        val project1 = runBlocking { container.projectStore.addProject(FilePath(tempDir1.absolutePath)) }
        val project2 = runBlocking { container.projectStore.addProject(FilePath(tempDir2.absolutePath)) }
        container.projectStore.setActiveProjectId(project1.id)

        composeTestRule.setContent {
            VibeStudioTheme {
                TabBarView(
                    container = container,
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
