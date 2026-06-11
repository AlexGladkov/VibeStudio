@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.testutil.CodeSpeakModeView
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.core.common.FilePath
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

/**
 * Integration tests for [CodeSpeakModeView].
 */
class CodeSpeakModeViewTest {

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
    fun codeSpeakModeView_rendersWithoutCrash() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakModeView(container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun codeSpeakModeView_specsColumnHeader_isVisible() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakModeView(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SPECS").assertExists()
    }

    @Test
    fun codeSpeakModeView_specsColumn_showsNoSpecsFound_whenEmpty() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakModeView(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No specs found").assertExists()
    }

    @Test
    fun codeSpeakModeView_specsColumn_newSpecButton_isVisible() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakModeView(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New Spec").assertExists()
    }

    @Test
    fun codeSpeakModeView_specsColumn_refreshButton_isVisible() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakModeView(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Refresh specs").assertExists()
    }

    @Test
    fun codeSpeakModeView_editorColumn_showsSelectASpec_whenNoneSelected() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakModeView(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Select a spec to edit").assertExists()
    }

    @Test
    fun codeSpeakModeView_buildOutputColumn_showsEmptyState() {
        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakModeView(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Run CodeSpeak to see output").assertExists()
    }

    @Test
    fun codeSpeakModeView_allThreeColumns_visibleWithActiveProject() {
        val tempDir = Files.createTempDirectory("vs-cs-proj").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
            VibeStudioTheme {
                CodeSpeakModeView(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SPECS").assertExists()
        composeTestRule.onNodeWithText("Select a spec to edit").assertExists()
        composeTestRule.onNodeWithText("Run CodeSpeak to see output").assertExists()
    }
}
