@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.ui.GitPanel
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.model.FilePath
import java.io.File
import java.nio.file.Files

/**
 * Integration tests for [GitPanel].
 */
class GitPanelTest {

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
    fun gitPanel_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GitPanel(container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun gitPanel_showsChangesHeader() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GitPanel(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CHANGES").assertExists()
    }

    @Test
    fun gitPanel_showsWorkingTreeClean_whenNoChanges() {
        composeTestRule.setContent {
            VibeStudioTheme {
                GitPanel(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Working tree clean").assertExists()
    }

    @Test
    fun gitPanel_rendersWithoutCrash_whenActiveProjectSet() {
        val tempDir = Files.createTempDirectory("vs-gitpanel").toFile().also { it.deleteOnExit() }
        val project = container.projectStore.addProject(FilePath(tempDir.absolutePath))
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
            VibeStudioTheme {
                GitPanel(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CHANGES").assertExists()
    }
}
