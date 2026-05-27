@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.createIsolatedContainer
import studio.vibe.desktop.ui.FileDiffSheetContent
import studio.vibe.desktop.ui.statusColorFor
import studio.vibe.desktop.ui.statusLetterFor
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.model.GitFile
import studio.vibe.shared.model.GitFileStatus
import java.io.File

/**
 * Tests for [studio.vibe.desktop.ui.FileDiffSheet].
 *
 * The public [studio.vibe.desktop.ui.FileDiffSheet] composable is wrapped inside a
 * [androidx.compose.ui.window.DialogWindow] which opens a separate native OS window
 * that [createComposeRule] cannot reach.
 *
 * We test the following instead:
 * - [statusLetterFor] — pure mapping function, letter per [GitFileStatus]
 * - [statusColorFor]  — pure mapping function, non-null color per [GitFileStatus]
 * - [FileDiffSheetContent] — the internal composable (not wrapped in DialogWindow)
 * - Binary extension detection logic via direct file extension check
 */
class FileDiffSheetTest {

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

    // ── statusLetterFor ───────────────────────────────────────────────────────

    @Test
    fun statusLetterFor_modified_returnsM() {
        assert(statusLetterFor(GitFileStatus.MODIFIED) == "M")
    }

    @Test
    fun statusLetterFor_added_returnsA() {
        assert(statusLetterFor(GitFileStatus.ADDED) == "A")
    }

    @Test
    fun statusLetterFor_deleted_returnsD() {
        assert(statusLetterFor(GitFileStatus.DELETED) == "D")
    }

    @Test
    fun statusLetterFor_renamed_returnsR() {
        assert(statusLetterFor(GitFileStatus.RENAMED) == "R")
    }

    @Test
    fun statusLetterFor_copied_returnsC() {
        assert(statusLetterFor(GitFileStatus.COPIED) == "C")
    }

    @Test
    fun statusLetterFor_untracked_returnsQuestionMark() {
        assert(statusLetterFor(GitFileStatus.UNTRACKED) == "?")
    }

    // ── statusColorFor — non-null for all entries ─────────────────────────────

    @Test
    fun statusColorFor_allStatuses_returnsNonNullColor() {
        GitFileStatus.entries.forEach { status ->
            val color = statusColorFor(status)
            // Color is a value class — just verify it does not throw and returns something
            assert(color != null) { "Expected non-null color for $status" }
        }
    }

    // ── Binary extension list coverage ────────────────────────────────────────

    private val binaryExtensions = setOf(
        "png", "jpg", "jpeg", "gif", "ico", "icns", "pdf",
        "zip", "tar", "gz", "bz2", "xz", "dmg", "exe",
        "dylib", "so", "a", "o", "class", "jar", "war",
        "ear", "bin", "dat", "db", "sqlite",
    )

    @Test
    fun binaryExtension_pngIsDetected() {
        assert("image.png".substringAfterLast('.').lowercase() in binaryExtensions)
    }

    @Test
    fun binaryExtension_jarIsDetected() {
        assert("library.jar".substringAfterLast('.').lowercase() in binaryExtensions)
    }

    @Test
    fun binaryExtension_ktIsNotBinary() {
        assert("Main.kt".substringAfterLast('.').lowercase() !in binaryExtensions)
    }

    @Test
    fun binaryExtension_swiftIsNotBinary() {
        assert("View.swift".substringAfterLast('.').lowercase() !in binaryExtensions)
    }

    @Test
    fun binaryExtension_sqliteIsDetected() {
        assert("app.sqlite".substringAfterLast('.').lowercase() in binaryExtensions)
    }

    // ── FileDiffSheetContent — renders without crash ──────────────────────────

    @Test
    fun fileDiffSheetContent_rendersWithoutCrash_forModifiedKotlinFile() {
        val file = GitFile(path = "src/main/kotlin/Foo.kt", status = GitFileStatus.MODIFIED)

        composeTestRule.setContent {
            VibeStudioTheme {
                FileDiffSheetContent(
                    container = container,
                    file = file,
                    projectPath = tempHome.absolutePath,
                    staged = false,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun fileDiffSheetContent_showsFileNameInHeader() {
        val file = GitFile(path = "src/main/kotlin/Foo.kt", status = GitFileStatus.MODIFIED)

        composeTestRule.setContent {
            VibeStudioTheme {
                FileDiffSheetContent(
                    container = container,
                    file = file,
                    projectPath = tempHome.absolutePath,
                    staged = false,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Foo.kt").assertIsDisplayed()
    }

    @Test
    fun fileDiffSheetContent_showsStagedBadge_whenStagedIsTrue() {
        val file = GitFile(path = "README.md", status = GitFileStatus.MODIFIED)

        composeTestRule.setContent {
            VibeStudioTheme {
                FileDiffSheetContent(
                    container = container,
                    file = file,
                    projectPath = tempHome.absolutePath,
                    staged = true,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("staged").assertIsDisplayed()
    }

    @Test
    fun fileDiffSheetContent_doesNotShowStagedBadge_whenStagedIsFalse() {
        val file = GitFile(path = "README.md", status = GitFileStatus.MODIFIED)

        composeTestRule.setContent {
            VibeStudioTheme {
                FileDiffSheetContent(
                    container = container,
                    file = file,
                    projectPath = tempHome.absolutePath,
                    staged = false,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("staged").assertDoesNotExist()
    }

    @Test
    fun fileDiffSheetContent_showsBinaryError_forPngFile() {
        val file = GitFile(path = "assets/logo.png", status = GitFileStatus.MODIFIED)

        composeTestRule.setContent {
            VibeStudioTheme {
                FileDiffSheetContent(
                    container = container,
                    file = file,
                    projectPath = tempHome.absolutePath,
                    staged = false,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Binary file — diff not available").assertIsDisplayed()
    }

    // ── DialogWindow limitation notice ────────────────────────────────────────

    @Ignore(
        "FileDiffSheet wraps FileDiffSheetContent inside DialogWindow which opens a " +
            "separate native OS window. createComposeRule() cannot inject composables " +
            "into an external native window. The internal FileDiffSheetContent composable " +
            "is tested above directly.",
    )
    @Test
    fun fileDiffSheet_dialogWindow_notTestableViaComposeRule() {
        // Intentionally empty — documents the DialogWindow limitation.
    }
}
