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
import studio.vibe.desktop.ui.VibeStudioDesktopApp
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.core.common.FilePath
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

/**
 * Integration tests for [VibeStudioDesktopApp].
 *
 * Critical rendering branches:
 * 1. No projects → WelcomeView shown.
 * 2. Projects + active → ToolbarView + RootView shown.
 * 3. Projects exist but NOT active → TerminalPlaceholder shown (key bug).
 * 4. CodeSpeak mode → CodeSpeakModeView shown.
 * 5. Settings toggle → no crash.
 */
class VibeStudioDesktopAppTest {

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
    fun app_rendersWithoutCrash_withNoProjects() {
        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(container = container)
            }
        }

        composeTestRule.waitForIdle()
    }

    // ── No projects → WelcomeView ──────────────────────────────────────────────

    @Test
    fun app_showsWelcomeTitle_whenNoProjectsExist() {
        kotlin.test.assertTrue(
            container.projectStore.projects.value.isEmpty(),
            "Precondition: isolated container must start with no projects",
        )

        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("VibeStudio").assertExists()
    }

    @Test
    fun app_showsOpenFolderButton_whenNoProjectsExist() {
        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Open Folder").assertExists()
    }

    @Test
    fun app_doesNotShowToolbar_whenNoProjectsExist() {
        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Settings").assertDoesNotExist()
    }

    // ── Projects + active → Toolbar + RootView ────────────────────────────────

    @Ignore("LocalInteropContainer not provided in headless test host — active project triggers SwingPanel in RootView")
    @Test
    fun app_showsSettingsButton_whenProjectsExistAndActive() {
        val tempDir = Files.createTempDirectory("vs-app-toolbar").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Settings").assertExists()
    }

    /**
     * BUG CATCHER: Project added but NOT activated → TerminalPlaceholder shown.
     */
    @Test
    fun app_showsTerminalPlaceholder_whenProjectExistsButNotActive() {
        val tempDir = Files.createTempDirectory("vs-app-noactive").toFile().also { it.deleteOnExit() }

        // Add project but do NOT activate it — simulates the startup bug.
        runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }

        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Terminal area").assertExists()
    }

    @Ignore("LocalInteropContainer not provided in headless test host — active project triggers SwingPanel in RootView")
    @Test
    fun app_hidesWelcomeView_whenProjectsExistAndActive() {
        val tempDir = Files.createTempDirectory("vs-app-hasproj").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(container = container)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Open a folder to get started").assertDoesNotExist()
    }

    // ── CodeSpeak mode ────────────────────────────────────────────────────────

    @Test
    fun app_showsCodeSpeakView_whenIsCodeSpeakModeTrue() {
        val tempDir = Files.createTempDirectory("vs-app-cs").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(
                    container = container,
                    isCodeSpeakMode = true,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SPECS").assertExists()
    }

    @Ignore("LocalInteropContainer not provided in headless test host — active project + isCodeSpeakMode=false triggers SwingPanel via RootView")
    @Test
    fun app_hidesCodeSpeakView_whenCodeSpeakModeIsFalse() {
        val tempDir = Files.createTempDirectory("vs-app-nocs").toFile().also { it.deleteOnExit() }
        val project = runBlocking { container.projectStore.addProject(FilePath(tempDir.absolutePath)) }
        container.projectStore.setActiveProjectId(project.id)

        composeTestRule.setContent {
            VibeStudioTheme {
                VibeStudioDesktopApp(
                    container = container,
                    isCodeSpeakMode = false,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SPECS").assertDoesNotExist()
    }

    // ── CodeSpeak toggle removed from toolbar ────────────────────────────────
    // Tests for the on-toolbar CodeSpeak toggle were dropped together with the
    // button — the macOS Swift toolbar doesn't expose it either, and the mode
    // is still reachable via ⌘⇧C / View menu.
}
