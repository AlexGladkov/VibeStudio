@file:OptIn(
    kotlin.uuid.ExperimentalUuidApi::class,
    kotlin.time.ExperimentalTime::class,
)

package studio.vibe.shared.feature.project.domain.model

import kotlinx.serialization.json.Json
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.HexColor
import studio.vibe.shared.feature.git.domain.model.GitBranch
import studio.vibe.shared.core.common.git.GitFile
import studio.vibe.shared.core.common.git.GitFileStatus
import studio.vibe.shared.core.common.git.GitStatus
import studio.vibe.shared.core.common.terminal.SplitDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlin.time.Clock

/**
 * Round-trip serialization/deserialization tests for key shared models.
 *
 * Each test verifies that encoding a model to JSON and decoding it back
 * produces a value equal to the original.  This guards against accidental
 * removal of `@Serializable` annotations, missing custom serializers, or
 * breaking changes to field names.
 *
 * Models tested:
 *  - [Project]
 *  - [GitFile] / [GitBranch]
 *  - [FilePath]
 *  - [HexColor]
 *  - [SplitDirection] (enum)
 */
class ModelSerializationTest {

    // Mirrors the Json instance used by ProjectStoreImpl.
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── FilePath ──────────────────────────────────────────────────────────────

    @Test
    fun filePath_roundTrip_preservesPath() {
        val original = FilePath("/Users/dev/my-project")
        val encoded = json.encodeToString(FilePath.serializer(), original)
        val decoded = json.decodeFromString(FilePath.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun filePath_roundTrip_nameHelper_isCorrect() {
        val fp = FilePath("/some/deep/path/filename.txt")
        val encoded = json.encodeToString(FilePath.serializer(), fp)
        val decoded = json.decodeFromString(FilePath.serializer(), encoded)
        assertEquals("filename.txt", decoded.name)
    }

    @Test
    fun filePath_child_roundTrip() {
        val parent = FilePath("/root")
        val child = parent.child("src")
        val encoded = json.encodeToString(FilePath.serializer(), child)
        val decoded = json.decodeFromString(FilePath.serializer(), encoded)
        assertEquals("/root/src", decoded.path)
    }

    // ── HexColor ──────────────────────────────────────────────────────────────

    @Test
    fun hexColor_roundTrip_preservesValue() {
        val original = HexColor.fromHex("#4A9EFF")
        assertNotNull(original)
        val encoded = json.encodeToString(HexColor.serializer(), original)
        val decoded = json.decodeFromString(HexColor.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun hexColor_fromHex_validSixDigit_returnsNonNull() {
        val color = HexColor.fromHex("FF0000")
        assertNotNull(color)
        assertEquals("#FF0000", color.value)
    }

    @Test
    fun hexColor_fromHex_invalidInput_returnsNull() {
        assertNull(HexColor.fromHex("ZZZZZZ"))
        assertNull(HexColor.fromHex("123"))
        assertNull(HexColor.fromHex(""))
    }

    // ── Project ───────────────────────────────────────────────────────────────

    @Test
    fun project_roundTrip_preservesFields() {
        val original = Project(
            id = Uuid.random(),
            name = "MyApp",
            path = FilePath("/workspace/my-app"),
            color = HexColor.fromHex("#3FB950"),
            lastOpened = Clock.System.now(),
            shellPath = "/bin/zsh",
            productionURL = "https://myapp.example.com",
        )
        val encoded = json.encodeToString(Project.serializer(), original)
        val decoded = json.decodeFromString(Project.serializer(), encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.path, decoded.path)
        assertEquals(original.color, decoded.color)
        assertEquals(original.shellPath, decoded.shellPath)
        assertEquals(original.productionURL, decoded.productionURL)
    }

    @Test
    fun project_roundTrip_withNullOptionals() {
        val original = Project(
            name = "Minimal",
            path = FilePath("/tmp/minimal"),
            color = null,
            productionURL = null,
        )
        val encoded = json.encodeToString(Project.serializer(), original)
        val decoded = json.decodeFromString(Project.serializer(), encoded)

        assertEquals(original.name, decoded.name)
        assertNull(decoded.color)
        assertNull(decoded.productionURL)
    }

    @Test
    fun project_list_roundTrip() {
        val projects = listOf(
            Project(name = "Alpha", path = FilePath("/alpha")),
            Project(name = "Beta", path = FilePath("/beta")),
        )
        val encoded = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(Project.serializer()),
            projects,
        )
        val decoded = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(Project.serializer()),
            encoded,
        )
        assertEquals(2, decoded.size)
        assertEquals("Alpha", decoded[0].name)
        assertEquals("Beta", decoded[1].name)
    }

    // ── SplitDirection ────────────────────────────────────────────────────────

    @Test
    fun splitDirection_horizontal_roundTrip() {
        val original = SplitDirection.horizontal
        val encoded = json.encodeToString(SplitDirection.serializer(), original)
        val decoded = json.decodeFromString(SplitDirection.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun splitDirection_vertical_roundTrip() {
        val original = SplitDirection.vertical
        val encoded = json.encodeToString(SplitDirection.serializer(), original)
        val decoded = json.decodeFromString(SplitDirection.serializer(), encoded)
        assertEquals(original, decoded)
    }

    // ── GitFile / GitBranch — data classes (no @Serializable, structure only) ─

    @Test
    fun gitFile_constructedCorrectly() {
        val file = GitFile(path = "src/Main.kt", status = GitFileStatus.MODIFIED)
        assertEquals("src/Main.kt", file.path)
        assertEquals(GitFileStatus.MODIFIED, file.status)
    }

    @Test
    fun gitBranch_constructedCorrectly() {
        val branch = GitBranch(name = "feature/new-ui", isRemote = false, isCurrent = true)
        assertEquals("feature/new-ui", branch.name)
        assertEquals(false, branch.isRemote)
        assertEquals(true, branch.isCurrent)
    }

    @Test
    fun gitFileStatus_fromCode_knownCodes_returnExpected() {
        assertEquals(GitFileStatus.MODIFIED, GitFileStatus.fromCode("M"))
        assertEquals(GitFileStatus.ADDED, GitFileStatus.fromCode("A"))
        assertEquals(GitFileStatus.DELETED, GitFileStatus.fromCode("D"))
        assertEquals(GitFileStatus.RENAMED, GitFileStatus.fromCode("R"))
        assertEquals(GitFileStatus.COPIED, GitFileStatus.fromCode("C"))
        assertEquals(GitFileStatus.UNTRACKED, GitFileStatus.fromCode("?"))
    }

    @Test
    fun gitFileStatus_fromCode_unknownCode_returnsNull() {
        assertNull(GitFileStatus.fromCode("X"))
        assertNull(GitFileStatus.fromCode(""))
    }

    // ── GitStatus helper ──────────────────────────────────────────────────────

    @Test
    fun gitStatus_isClean_whenAllListsEmpty() {
        val status = GitStatus.EMPTY
        assertEquals(true, status.isClean)
    }

    @Test
    fun gitStatus_isClean_falseWhenStagedFileExists() {
        val status = GitStatus.EMPTY.copy(
            stagedFiles = listOf(GitFile("README.md", GitFileStatus.MODIFIED)),
        )
        assertEquals(false, status.isClean)
    }
}
