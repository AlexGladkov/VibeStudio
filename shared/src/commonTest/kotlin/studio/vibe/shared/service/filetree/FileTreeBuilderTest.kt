package studio.vibe.shared.service.filetree

import kotlinx.coroutines.test.runTest
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.FileTreeNode
import studio.vibe.shared.model.GitFile
import studio.vibe.shared.model.GitFileStatus
import studio.vibe.shared.model.GitStatus
import studio.vibe.shared.testutil.FakePersistenceStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [FileTreeBuilder].
 *
 * Uses [FakePersistenceStore] to provide a deterministic, in-memory file system.
 * Tests verify:
 *  - Empty directory produces empty list.
 *  - Files at root level are listed as [FileTreeNode.File].
 *  - Sub-directories are listed as [FileTreeNode.Directory].
 *  - Directories come before files in the sorted output.
 *  - Excluded directories (.git, node_modules, build, etc.) are filtered out.
 *  - Git status annotations are applied correctly.
 *  - [maxDepth] limits recursion.
 *  - Alphabetical (case-insensitive) sort order is respected.
 */
class FileTreeBuilderTest {

    private lateinit var persistence: FakePersistenceStore
    private lateinit var builder: FileTreeBuilder

    private val root = FilePath("/project")

    @BeforeTest
    fun setup() {
        persistence = FakePersistenceStore(appSupportDir = FilePath("/app-support"))
        builder = FileTreeBuilder(persistence)
        // Root is a directory in every test.
        persistence.addDirectory(root)
    }

    // ── Empty directory ───────────────────────────────────────────────────────

    @Test
    fun buildTree_emptyDirectory_returnsEmptyList() = runTest {
        val result = builder.buildTree(root)
        assertTrue(result.isEmpty(), "Empty root directory must produce empty tree")
    }

    // ── Files at root ─────────────────────────────────────────────────────────

    @Test
    fun buildTree_singleFile_producesOneFileNode() = runTest {
        writeFile("/project/main.kt")

        val result = builder.buildTree(root)

        assertEquals(1, result.size)
        assertTrue(result[0] is FileTreeNode.File)
        assertEquals("main.kt", result[0].name)
    }

    @Test
    fun buildTree_multipleFiles_allAppear() = runTest {
        writeFile("/project/a.kt")
        writeFile("/project/b.kt")
        writeFile("/project/c.kt")

        val result = builder.buildTree(root)

        assertEquals(3, result.size)
        assertEquals(listOf("a.kt", "b.kt", "c.kt"), result.map { it.name })
    }

    // ── Directories ───────────────────────────────────────────────────────────

    @Test
    fun buildTree_subDirectory_producesDirectoryNode() = runTest {
        persistence.addDirectory(FilePath("/project/src"))

        val result = builder.buildTree(root)

        assertEquals(1, result.size)
        assertTrue(result[0] is FileTreeNode.Directory)
        assertEquals("src", result[0].name)
    }

    @Test
    fun buildTree_directoriesBeforeFiles_inOutput() = runTest {
        writeFile("/project/README.md")
        persistence.addDirectory(FilePath("/project/src"))

        val result = builder.buildTree(root)

        assertEquals(2, result.size)
        assertTrue(result[0] is FileTreeNode.Directory, "Directory must come first")
        assertTrue(result[1] is FileTreeNode.File, "File must come second")
    }

    // ── Nested files ──────────────────────────────────────────────────────────

    @Test
    fun buildTree_nestedFile_appearsUnderDirectory() = runTest {
        persistence.addDirectory(FilePath("/project/src"))
        writeFile("/project/src/Main.kt")

        val result = builder.buildTree(root)

        assertEquals(1, result.size)
        val dir = result[0] as FileTreeNode.Directory
        assertEquals(1, dir.entry.children.size)
        assertEquals("Main.kt", dir.entry.children[0].name)
    }

    // ── Excluded directories ──────────────────────────────────────────────────

    @Test
    fun buildTree_excludes_dotGit() = runTest {
        persistence.addDirectory(FilePath("/project/.git"))
        writeFile("/project/README.md")

        val result = builder.buildTree(root)

        assertTrue(result.none { it.name == ".git" }, ".git must be excluded")
        assertEquals(1, result.size)
    }

    @Test
    fun buildTree_excludes_nodeModules() = runTest {
        persistence.addDirectory(FilePath("/project/node_modules"))
        writeFile("/project/package.json")

        val result = builder.buildTree(root)

        assertTrue(result.none { it.name == "node_modules" }, "node_modules must be excluded")
    }

    @Test
    fun buildTree_excludes_build() = runTest {
        persistence.addDirectory(FilePath("/project/build"))
        writeFile("/project/settings.gradle.kts")

        val result = builder.buildTree(root)

        assertTrue(result.none { it.name == "build" }, "build directory must be excluded")
    }

    @Test
    fun buildTree_excludes_dotIdea() = runTest {
        persistence.addDirectory(FilePath("/project/.idea"))
        writeFile("/project/main.kt")

        val result = builder.buildTree(root)

        assertTrue(result.none { it.name == ".idea" }, ".idea must be excluded")
    }

    // ── maxDepth ──────────────────────────────────────────────────────────────

    @Test
    fun buildTree_maxDepthZero_returnsEmptyList() = runTest {
        writeFile("/project/main.kt")
        persistence.addDirectory(FilePath("/project/src"))

        val result = builder.buildTree(root, maxDepth = 0)

        assertTrue(result.isEmpty(), "maxDepth=0 must return empty list")
    }

    @Test
    fun buildTree_maxDepthOne_includesOnlyRootChildren() = runTest {
        persistence.addDirectory(FilePath("/project/src"))
        writeFile("/project/src/Main.kt")

        val result = builder.buildTree(root, maxDepth = 1)

        // The src directory should appear but have no children (depth limit reached).
        assertEquals(1, result.size)
        val dir = result[0] as FileTreeNode.Directory
        assertTrue(dir.entry.children.isEmpty(), "Children must be empty when maxDepth=1")
    }

    // ── Git status annotation ─────────────────────────────────────────────────

    @Test
    fun buildTree_gitStatusAnnotatesFile() = runTest {
        writeFile("/project/src/Modified.kt")
        persistence.addDirectory(FilePath("/project/src"))

        val gitStatus = GitStatus(
            branch = "main",
            aheadCount = 0,
            behindCount = 0,
            stagedFiles = emptyList(),
            unstagedFiles = listOf(GitFile("src/Modified.kt", GitFileStatus.MODIFIED)),
            untrackedFiles = emptyList(),
        )

        val result = builder.buildTree(root, gitStatus = gitStatus)

        val srcDir = result.filterIsInstance<FileTreeNode.Directory>()
            .firstOrNull { it.name == "src" }
        assertNotNull(srcDir, "src directory must be present")

        val file = srcDir.entry.children.filterIsInstance<FileTreeNode.File>()
            .firstOrNull { it.name == "Modified.kt" }
        assertNotNull(file, "Modified.kt must be present under src")
        assertEquals(GitFileStatus.MODIFIED, file.entry.gitStatus)
    }

    @Test
    fun buildTree_noGitStatus_fileHasNullGitStatus() = runTest {
        writeFile("/project/main.kt")

        val result = builder.buildTree(root, gitStatus = null)

        val file = result.filterIsInstance<FileTreeNode.File>().first()
        assertEquals(null, file.entry.gitStatus)
    }

    // ── Sort order ────────────────────────────────────────────────────────────

    @Test
    fun buildTree_filesAreSortedCaseInsensitively() = runTest {
        writeFile("/project/Zebra.kt")
        writeFile("/project/apple.kt")
        writeFile("/project/Mango.kt")

        val result = builder.buildTree(root)

        val names = result.map { it.name }
        assertEquals(listOf("apple.kt", "Mango.kt", "Zebra.kt"), names)
    }

    @Test
    fun buildTree_directoriesAreSortedCaseInsensitively() = runTest {
        persistence.addDirectory(FilePath("/project/Zeta"))
        persistence.addDirectory(FilePath("/project/alpha"))
        persistence.addDirectory(FilePath("/project/Beta"))

        val result = builder.buildTree(root)

        val names = result.map { it.name }
        assertEquals(listOf("alpha", "Beta", "Zeta"), names)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Writes an empty file at the given absolute path. */
    private suspend fun writeFile(absolutePath: String) {
        persistence.writeFile(FilePath(absolutePath), ByteArray(0))
    }
}
