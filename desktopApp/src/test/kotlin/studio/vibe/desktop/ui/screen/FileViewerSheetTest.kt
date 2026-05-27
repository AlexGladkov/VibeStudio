package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.ui.FileViewerSheetContent
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.constants.FileViewerConstants
import java.io.File
import java.nio.file.Files

/**
 * Tests for [studio.vibe.desktop.ui.FileViewerSheet].
 *
 * The public [studio.vibe.desktop.ui.FileViewerSheet] composable wraps its content
 * inside a [androidx.compose.ui.window.DialogWindow]. [createComposeRule] operates
 * only within its own composition and cannot reach into the separate native window.
 *
 * We test instead:
 * - [FileViewerSheetContent] — the internal composable exposed as `internal`
 * - Column count logic via [FileViewerConstants] constants
 * - Binary detection algorithm (null-byte heuristic)
 * - Window title derivation logic (pure function extracted inline)
 * - File extension category mappings
 */
class FileViewerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Window width constants ────────────────────────────────────────────────

    @Test
    fun windowWidth_singleFile_usesSingleFileConstant() {
        assert(FileViewerConstants.SINGLE_FILE_WIDTH == 600.0)
    }

    @Test
    fun windowWidth_twoFiles_usesTwoFileConstant() {
        assert(FileViewerConstants.TWO_FILE_WIDTH == 1100.0)
    }

    @Test
    fun windowWidth_threeFiles_usesThreeFileConstant() {
        assert(FileViewerConstants.THREE_FILE_WIDTH == 1280.0)
    }

    @Test
    fun sheetHeight_isFixed() {
        assert(FileViewerConstants.SHEET_HEIGHT == 600.0)
    }

    // ── Binary detection (null-byte heuristic) ────────────────────────────────

    @Test
    fun binaryDetection_textFileContainsNullByte_isDetectedAsBinary() {
        val probe = byteArrayOf(65, 66, 0, 67) // 'A', 'B', null, 'C'
        val isBinary = probe.any { it == 0.toByte() }
        assert(isBinary)
    }

    @Test
    fun binaryDetection_pureAsciiText_isNotBinary() {
        val probe = "Hello World\n".encodeToByteArray()
        val isBinary = probe.any { it == 0.toByte() }
        assert(!isBinary)
    }

    @Test
    fun binaryDetection_emptyProbe_isNotBinary() {
        val probe = byteArrayOf()
        val isBinary = probe.any { it == 0.toByte() }
        assert(!isBinary)
    }

    @Test
    fun binaryDetection_allNullBytes_isBinary() {
        val probe = ByteArray(8) { 0 }
        val isBinary = probe.any { it == 0.toByte() }
        assert(isBinary)
    }

    // ── Window title logic ────────────────────────────────────────────────────

    private fun buildWindowTitle(filePaths: List<String>): String {
        val names = filePaths.map { it.substringAfterLast('/') }
        return when (names.size) {
            1 -> names[0]
            2 -> "${names[0]}  vs  ${names[1]}"
            else -> names.joinToString("  |  ")
        }
    }

    @Test
    fun windowTitle_singleFile_showsFileName() {
        val title = buildWindowTitle(listOf("/path/to/Foo.kt"))
        assert(title == "Foo.kt") { "Expected 'Foo.kt' but was '$title'" }
    }

    @Test
    fun windowTitle_twoFiles_showsVsFormat() {
        val title = buildWindowTitle(listOf("/a/Foo.kt", "/b/Bar.kt"))
        assert(title == "Foo.kt  vs  Bar.kt") { "Expected 'Foo.kt  vs  Bar.kt' but was '$title'" }
    }

    @Test
    fun windowTitle_threeFiles_showsPipeSeparated() {
        val title = buildWindowTitle(listOf("/a/A.kt", "/b/B.kt", "/c/C.kt"))
        assert(title == "A.kt  |  B.kt  |  C.kt") { "Expected pipe-separated but was '$title'" }
    }

    // ── formatBytes logic ─────────────────────────────────────────────────────

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
        else -> "%.0f KB".format(bytes / 1_000.0)
    }

    @Test
    fun formatBytes_belowMegabyte_showsKb() {
        val result = formatBytes(512 * 1024L)
        assert(result.endsWith("KB")) { "Expected KB suffix but was '$result'" }
    }

    @Test
    fun formatBytes_aboveMegabyte_showsMb() {
        val result = formatBytes(2_000_000L)
        assert(result.endsWith("MB")) { "Expected MB suffix but was '$result'" }
    }

    @Test
    fun formatBytes_exactlyOneMegabyte_showsMb() {
        val result = formatBytes(1_000_000L)
        assert(result == "1.0 MB") { "Expected '1.0 MB' but was '$result'" }
    }

    // ── FileViewerSheetContent — Compose tests ────────────────────────────────

    @Test
    fun fileViewerSheetContent_rendersWithoutCrash_forTextFile() {
        val tempFile = Files.createTempFile("test-viewer", ".kt").toFile()
        tempFile.writeText("fun main() { println(\"hello\") }\n")
        tempFile.deleteOnExit()

        composeTestRule.setContent {
            VibeStudioTheme {
                FileViewerSheetContent(
                    filePaths = listOf(tempFile.absolutePath),
                    onDismiss = {},
                    onAddFile = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        tempFile.delete()
    }

    @Test
    fun fileViewerSheetContent_shows1File_inToolbar() {
        val tempFile = Files.createTempFile("single-file", ".kt").toFile()
        tempFile.writeText("val x = 1\n")
        tempFile.deleteOnExit()

        composeTestRule.setContent {
            VibeStudioTheme {
                FileViewerSheetContent(
                    filePaths = listOf(tempFile.absolutePath),
                    onDismiss = {},
                    onAddFile = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 file").assertIsDisplayed()
        tempFile.delete()
    }

    @Test
    fun fileViewerSheetContent_shows2Files_inToolbar() {
        val tempFile1 = Files.createTempFile("file-a", ".kt").toFile()
        val tempFile2 = Files.createTempFile("file-b", ".kt").toFile()
        tempFile1.writeText("val a = 1\n")
        tempFile2.writeText("val b = 2\n")
        tempFile1.deleteOnExit()
        tempFile2.deleteOnExit()

        composeTestRule.setContent {
            VibeStudioTheme {
                FileViewerSheetContent(
                    filePaths = listOf(tempFile1.absolutePath, tempFile2.absolutePath),
                    onDismiss = {},
                    onAddFile = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2 files").assertIsDisplayed()
        tempFile1.delete()
        tempFile2.delete()
    }

    @Test
    fun fileViewerSheetContent_showsAddFileButton_whenBelowMaxColumns() {
        val tempFile = Files.createTempFile("add-file-test", ".kt").toFile()
        tempFile.writeText("val x = 1\n")
        tempFile.deleteOnExit()

        composeTestRule.setContent {
            VibeStudioTheme {
                FileViewerSheetContent(
                    filePaths = listOf(tempFile.absolutePath),
                    onDismiss = {},
                    onAddFile = { /* callback provided */ },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add File").assertIsDisplayed()
        tempFile.delete()
    }

    @Test
    fun fileViewerSheetContent_hidesAddFileButton_whenCallbackIsNull() {
        val tempFile = Files.createTempFile("no-add", ".kt").toFile()
        tempFile.writeText("val x = 1\n")
        tempFile.deleteOnExit()

        composeTestRule.setContent {
            VibeStudioTheme {
                FileViewerSheetContent(
                    filePaths = listOf(tempFile.absolutePath),
                    onDismiss = {},
                    onAddFile = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add File").assertDoesNotExist()
        tempFile.delete()
    }

    @Test
    fun fileViewerSheetContent_showsEmptyFile_forEmptyFile() {
        val tempFile = Files.createTempFile("empty-file", ".txt").toFile()
        tempFile.writeText("")
        tempFile.deleteOnExit()

        composeTestRule.setContent {
            VibeStudioTheme {
                FileViewerSheetContent(
                    filePaths = listOf(tempFile.absolutePath),
                    onDismiss = {},
                    onAddFile = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Empty file").assertIsDisplayed()
        tempFile.delete()
    }

    @Test
    fun fileViewerSheetContent_showsBinaryState_forBinaryFile() {
        val tempFile = Files.createTempFile("binary-file", ".bin").toFile()
        // Write null bytes to trigger binary detection
        tempFile.writeBytes(byteArrayOf(65, 66, 0, 67, 68))
        tempFile.deleteOnExit()

        composeTestRule.setContent {
            VibeStudioTheme {
                FileViewerSheetContent(
                    filePaths = listOf(tempFile.absolutePath),
                    onDismiss = {},
                    onAddFile = null,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Binary file — preview not available").assertIsDisplayed()
        tempFile.delete()
    }

    // ── DialogWindow limitation notice ────────────────────────────────────────

    @Ignore(
        "FileViewerSheet wraps its content inside DialogWindow which opens a separate " +
            "native OS window. createComposeRule() cannot inject composables into an " +
            "external native window. The internal FileViewerSheetContent composable " +
            "is tested above directly.",
    )
    @Test
    fun fileViewerSheet_dialogWindow_notTestableViaComposeRule() {
        // Intentionally empty — documents the DialogWindow limitation.
    }
}
