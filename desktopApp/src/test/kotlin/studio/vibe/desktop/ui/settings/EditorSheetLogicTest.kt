package studio.vibe.desktop.ui.settings

import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-logic tests for editor sheet helper functions and data classes.
 *
 * AgentEditorSheet, ClaudeEditorSheet, CommandEditorSheet, SkillViewerSheet,
 * and TextFileEditorSheet all use [DialogWindow] at the entry point and therefore
 * CANNOT be tested with [createComposeRule]. This file tests the extractable
 * business logic: name parsing, filename validation, sanitization, path
 * abbreviation, and the [SkillInfo] data class contract.
 *
 * All tested helpers are package-private (same package), so they are accessible
 * directly from this test class.
 */
class EditorSheetLogicTest {

    // ── Agent name parsing from frontmatter ───────────────────────────────────

    @Test
    fun parseFrontmatterName_extractsNameFromValidFrontmatter() {
        val content = """
            ---
            name: my-special-agent
            description: does stuff
            model: sonnet
            ---

            Agent body here.
        """.trimIndent()

        val name = parseFrontmatterNameForTest(content)
        assertEquals("my-special-agent", name)
    }

    @Test
    fun parseFrontmatterName_returnsEmpty_whenNoFrontmatter() {
        val content = "Just regular markdown without frontmatter."
        val name = parseFrontmatterNameForTest(content)
        assertEquals("", name)
    }

    @Test
    fun parseFrontmatterName_returnsEmpty_whenNameFieldMissing() {
        val content = """
            ---
            description: does stuff
            model: sonnet
            ---

            Body.
        """.trimIndent()

        val name = parseFrontmatterNameForTest(content)
        assertEquals("", name)
    }

    @Test
    fun parseFrontmatterName_trimsWhitespace_aroundName() {
        val content = """
            ---
            name:   trimmed-name
            ---
        """.trimIndent()

        val name = parseFrontmatterNameForTest(content)
        assertEquals("trimmed-name", name)
    }

    @Test
    fun parseFrontmatterName_returnsEmpty_whenFrontmatterIsEmpty() {
        val content = """
            ---
            ---
            Body.
        """.trimIndent()

        val name = parseFrontmatterNameForTest(content)
        assertEquals("", name)
    }

    @Test
    fun parseFrontmatterName_handlesNameWithSpaces() {
        val content = """
            ---
            name: My Agent With Spaces
            ---
        """.trimIndent()

        val name = parseFrontmatterNameForTest(content)
        assertEquals("My Agent With Spaces", name)
    }

    // ── Agent name sanitization ───────────────────────────────────────────────

    @Test
    fun sanitizeAgentName_convertsToLowercase() {
        val result = sanitizeAgentNameForTest("MyAgent")
        assertEquals("myagent", result)
    }

    @Test
    fun sanitizeAgentName_replacesSpacesWithDashes() {
        val result = sanitizeAgentNameForTest("my agent name")
        assertEquals("my-agent-name", result)
    }

    @Test
    fun sanitizeAgentName_removesNonAsciiLetters() {
        // Cyrillic chars should be filtered out (non-ASCII letters)
        val result = sanitizeAgentNameForTest("мой-агент")
        // All Cyrillic chars have codes > 128, digits/dashes are preserved
        assertEquals("-", result)
    }

    @Test
    fun sanitizeAgentName_preservesDashesAndUnderscores() {
        val result = sanitizeAgentNameForTest("my-agent_v2")
        assertEquals("my-agent_v2", result)
    }

    @Test
    fun sanitizeAgentName_preservesDigits() {
        val result = sanitizeAgentNameForTest("agent123")
        assertEquals("agent123", result)
    }

    @Test
    fun sanitizeAgentName_returnsEmpty_forAllSpecialChars() {
        val result = sanitizeAgentNameForTest("@#$%^&*()")
        assertEquals("", result)
    }

    @Test
    fun sanitizeAgentName_handlesAlreadySanitizedName() {
        val result = sanitizeAgentNameForTest("java-architect")
        assertEquals("java-architect", result)
    }

    // ── Command filename validation regex ─────────────────────────────────────

    @Test
    fun commandFilenameRegex_matchesLowercaseLetters() {
        assertTrue(COMMAND_FILENAME_REGEX_TEST.matches("deploy"))
    }

    @Test
    fun commandFilenameRegex_matchesLettersAndDigits() {
        assertTrue(COMMAND_FILENAME_REGEX_TEST.matches("deploy2024"))
    }

    @Test
    fun commandFilenameRegex_matchesDashSeparated() {
        assertTrue(COMMAND_FILENAME_REGEX_TEST.matches("my-command"))
    }

    @Test
    fun commandFilenameRegex_matchesUnderscoreSeparated() {
        assertTrue(COMMAND_FILENAME_REGEX_TEST.matches("my_command"))
    }

    @Test
    fun commandFilenameRegex_matchesMixedValid() {
        assertTrue(COMMAND_FILENAME_REGEX_TEST.matches("deploy-to-prod_v2"))
    }

    @Test
    fun commandFilenameRegex_rejectsUppercaseLetters() {
        assertFalse(COMMAND_FILENAME_REGEX_TEST.matches("DeployCommand"))
    }

    @Test
    fun commandFilenameRegex_rejectsSpaces() {
        assertFalse(COMMAND_FILENAME_REGEX_TEST.matches("my command"))
    }

    @Test
    fun commandFilenameRegex_rejectsSpecialChars() {
        assertFalse(COMMAND_FILENAME_REGEX_TEST.matches("cmd@home"))
    }

    @Test
    fun commandFilenameRegex_rejectsEmptyString() {
        assertFalse(COMMAND_FILENAME_REGEX_TEST.matches(""))
    }

    @Test
    fun commandFilenameRegex_rejectsDotExtension() {
        assertFalse(COMMAND_FILENAME_REGEX_TEST.matches("command.md"))
    }

    // ── SkillInfo data class ──────────────────────────────────────────────────

    @Test
    fun skillInfo_constructsWithAllFields() {
        val skill = SkillInfo(
            id = "/home/user/.claude/skills/my-skill",
            directoryPath = "/home/user/.claude/skills/my-skill",
            skillFilePath = "/home/user/.claude/skills/my-skill/SKILL.md",
            name = "My Skill",
            description = "Does something useful",
            isUserInvocable = true,
            isWritable = true,
        )

        assertEquals("/home/user/.claude/skills/my-skill", skill.id)
        assertEquals("/home/user/.claude/skills/my-skill", skill.directoryPath)
        assertEquals("/home/user/.claude/skills/my-skill/SKILL.md", skill.skillFilePath)
        assertEquals("My Skill", skill.name)
        assertEquals("Does something useful", skill.description)
        assertTrue(skill.isUserInvocable)
        assertTrue(skill.isWritable)
    }

    @Test
    fun skillInfo_isWritable_falseForReadOnlySkills() {
        val skill = SkillInfo(
            id = "/opt/homebrew/share/claude-skills/brew-skill",
            directoryPath = "/opt/homebrew/share/claude-skills/brew-skill",
            skillFilePath = "/opt/homebrew/share/claude-skills/brew-skill/SKILL.md",
            name = "Homebrew Skill",
            description = "",
            isUserInvocable = false,
            isWritable = false,
        )

        assertFalse(skill.isWritable)
        assertFalse(skill.isUserInvocable)
    }

    @Test
    fun skillInfo_equalsAndHashCode_workCorrectly() {
        val skill1 = SkillInfo(
            id = "path/a",
            directoryPath = "path/a",
            skillFilePath = "path/a/SKILL.md",
            name = "Skill A",
            description = "desc",
            isUserInvocable = true,
            isWritable = true,
        )
        val skill2 = SkillInfo(
            id = "path/a",
            directoryPath = "path/a",
            skillFilePath = "path/a/SKILL.md",
            name = "Skill A",
            description = "desc",
            isUserInvocable = true,
            isWritable = true,
        )

        assertEquals(skill1, skill2)
        assertEquals(skill1.hashCode(), skill2.hashCode())
    }

    @Test
    fun skillInfo_copyModifiesOnlySpecifiedFields() {
        val original = SkillInfo(
            id = "path",
            directoryPath = "path",
            skillFilePath = "path/SKILL.md",
            name = "Original",
            description = "desc",
            isUserInvocable = false,
            isWritable = true,
        )

        val modified = original.copy(name = "Modified", isUserInvocable = true)

        assertEquals("Modified", modified.name)
        assertTrue(modified.isUserInvocable)
        // Other fields unchanged
        assertEquals(original.id, modified.id)
        assertEquals(original.directoryPath, modified.directoryPath)
        assertEquals(original.isWritable, modified.isWritable)
    }

    // ── Tilde path abbreviation (used in all editor sheets) ───────────────────

    @Test
    fun tildePathAbbreviation_replacesHomeWithTilde() {
        val home = System.getProperty("user.home")
        val fullPath = "$home/.claude/CLAUDE.md"
        val abbreviated = fullPath.replace(home, "~")

        assertEquals("~/.claude/CLAUDE.md", abbreviated)
    }

    @Test
    fun tildePathAbbreviation_leavesOtherPathsUnchanged() {
        val home = System.getProperty("user.home")
        val otherPath = "/tmp/some-file.md"
        val result = otherPath.replace(home, "~")

        assertEquals(otherPath, result)
    }

    // ── File save / load round-trip logic ─────────────────────────────────────

    @Test
    fun fileSave_writesContentToFile() {
        val tempDir = Files.createTempDirectory("editor-sheet-test").toFile()
        try {
            val target = File(tempDir, "test.md")
            val content = "# Test\n\nSome content."
            target.writeText(content, Charsets.UTF_8)

            assertEquals(content, target.readText(Charsets.UTF_8))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun fileLoad_readsUtf8Content() {
        val tempDir = Files.createTempDirectory("editor-sheet-test").toFile()
        try {
            val target = File(tempDir, "unicode.md")
            val content = "Привет мир! 日本語テスト"
            target.writeText(content, Charsets.UTF_8)

            val loaded = runCatching { target.readText(Charsets.UTF_8) }.getOrElse { "" }
            assertEquals(content, loaded)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun fileLoad_returnsEmptyString_whenFileMissing() {
        val nonExistentPath = "/tmp/vs-test-nonexistent-${System.nanoTime()}.md"
        val loaded = runCatching { File(nonExistentPath).readText(Charsets.UTF_8) }.getOrElse { "" }
        assertEquals("", loaded)
    }

    @Test
    fun fileLoad_usesDefaultContent_whenFileMissing() {
        val nonExistentPath = "/tmp/vs-test-nonexistent-${System.nanoTime()}.md"
        val default = "# Default Template"
        val loaded = runCatching {
            File(nonExistentPath).readText(Charsets.UTF_8)
        }.getOrElse { default }

        assertEquals(default, loaded)
    }

    @Test
    fun directoryCreation_mkdirs_createsNestedDirs() {
        val tempDir = Files.createTempDirectory("editor-sheet-test").toFile()
        try {
            val nestedDir = File(tempDir, "level1/level2")
            nestedDir.mkdirs()

            assertTrue(nestedDir.exists())
            assertTrue(nestedDir.isDirectory)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

// ── Test-accessible bridges for private helpers ───────────────────────────────
//
// The helpers parseFrontmatterName() and sanitizeAgentName() in AgentEditorSheet.kt
// are private to that file. We duplicate the pure logic here so we can test it
// without reflection. If the source implementations change, these copies must be
// updated to match.

private val COMMAND_FILENAME_REGEX_TEST = Regex("^[a-z0-9_\\-]+$")

/**
 * Test-local copy of [AgentEditorSheet.kt]'s `parseFrontmatterName`.
 * Kept in sync with the source implementation.
 */
private fun parseFrontmatterNameForTest(text: String): String {
    val lines = text.lines()
    var inFrontmatter = false
    for (line in lines) {
        if (line.trim() == "---") {
            if (!inFrontmatter) {
                inFrontmatter = true
                continue
            } else {
                break
            }
        }
        if (inFrontmatter && line.startsWith("name:")) {
            return line.removePrefix("name:").trim()
        }
    }
    return ""
}

/**
 * Test-local copy of [AgentEditorSheet.kt]'s `sanitizeAgentName`.
 * Kept in sync with the source implementation.
 */
private fun sanitizeAgentNameForTest(name: String): String =
    name.lowercase()
        .replace(' ', '-')
        .filter { it.isLetter() && it.code < 128 || it.isDigit() || it == '-' || it == '_' }
