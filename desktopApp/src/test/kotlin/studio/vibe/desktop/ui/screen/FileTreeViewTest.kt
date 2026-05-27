package studio.vibe.desktop.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import studio.vibe.desktop.ui.FileTreeView
import studio.vibe.desktop.ui.theme.VibeStudioTheme
import studio.vibe.shared.model.DirectoryEntry
import studio.vibe.shared.model.FileEntry
import studio.vibe.shared.model.FileTreeNode
import studio.vibe.shared.model.FilePath

/**
 * UI tests for [FileTreeView].
 */
class FileTreeViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fileTreeView_rendersWithoutCrash_emptyTree() {
        composeTestRule.setContent {
            VibeStudioTheme {
                FileTreeView(
                    nodes = emptyList(),
                    expandedDirs = emptySet(),
                    onToggleDir = {},
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun fileTreeView_rendersDirectoryNode() {
        val nodes = listOf(
            FileTreeNode.Directory(
                entry = DirectoryEntry(
                    path = FilePath("/project/src"),
                    children = emptyList(),
                    isExpanded = false,
                ),
            ),
        )

        composeTestRule.setContent {
            VibeStudioTheme {
                FileTreeView(
                    nodes = nodes,
                    expandedDirs = emptySet(),
                    onToggleDir = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("src").assertExists()
    }

    @Test
    fun fileTreeView_rendersFileNode() {
        val nodes = listOf(
            FileTreeNode.File(
                entry = FileEntry(
                    path = FilePath("/project/Main.kt"),
                ),
            ),
        )

        composeTestRule.setContent {
            VibeStudioTheme {
                FileTreeView(
                    nodes = nodes,
                    expandedDirs = emptySet(),
                    onToggleDir = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Main.kt").assertExists()
    }

    @Test
    fun fileTreeView_rendersMultipleNodes() {
        val nodes = listOf(
            FileTreeNode.Directory(
                entry = DirectoryEntry(
                    path = FilePath("/project/src"),
                    children = emptyList(),
                    isExpanded = false,
                ),
            ),
            FileTreeNode.File(
                entry = FileEntry(
                    path = FilePath("/project/build.gradle.kts"),
                ),
            ),
        )

        composeTestRule.setContent {
            VibeStudioTheme {
                FileTreeView(
                    nodes = nodes,
                    expandedDirs = emptySet(),
                    onToggleDir = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("src").assertExists()
        composeTestRule.onNodeWithText("build.gradle.kts").assertExists()
    }

    @Test
    fun fileTreeView_expandedDirectory_showsChildren() {
        val childFile = FileTreeNode.File(
            entry = FileEntry(path = FilePath("/project/src/Main.kt")),
        )
        val srcDir = DirectoryEntry(
            path = FilePath("/project/src"),
            children = listOf(childFile),
            isExpanded = true,
        )
        val nodes = listOf(FileTreeNode.Directory(entry = srcDir))

        composeTestRule.setContent {
            VibeStudioTheme {
                FileTreeView(
                    nodes = nodes,
                    expandedDirs = setOf("/project/src"),
                    onToggleDir = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("src").assertExists()
        composeTestRule.onNodeWithText("Main.kt").assertExists()
    }
}
