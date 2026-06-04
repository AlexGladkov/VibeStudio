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
import studio.vibe.desktop.testutil.CommitPanel
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.model.FilePath
import java.io.File
import java.nio.file.Files
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking

/**
 * UI tests for [CommitPanel].
 */
class CommitPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var container: DesktopServiceContainer
    private lateinit var tempHome: File
    private lateinit var projectDir: File
    private lateinit var projectId: Uuid

    @Before
    fun setup() {
        val (c, h) = createIsolatedContainer()
        container = c
        tempHome = h
        projectDir = Files.createTempDirectory("vs-commit-test").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(projectDir.absolutePath)) }
        projectId = project.id
    }

    @After
    fun teardown() {
        container.dispose()
        tempHome.deleteRecursively()
        projectDir.deleteRecursively()
    }

    @Test
    fun commitPanel_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CommitPanel(
                    container = container,
                    projectId = projectId,
                    projectPath = FilePath(projectDir.absolutePath),
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun commitPanel_commitSummaryPlaceholderVisible() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CommitPanel(
                    container = container,
                    projectId = projectId,
                    projectPath = FilePath(projectDir.absolutePath),
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Commit summary").assertExists()
    }

    @Test
    fun commitPanel_descriptionPlaceholderVisible() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CommitPanel(
                    container = container,
                    projectId = projectId,
                    projectPath = FilePath(projectDir.absolutePath),
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Description (optional)").assertExists()
    }

    @Test
    fun commitPanel_commitButtonVisible() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CommitPanel(
                    container = container,
                    projectId = projectId,
                    projectPath = FilePath(projectDir.absolutePath),
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Commit All Changes").assertExists()
    }
}
