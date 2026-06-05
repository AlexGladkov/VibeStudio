package studio.vibe.shared.service.git.parser

import studio.vibe.shared.model.DiffLineType
import studio.vibe.shared.model.GitFileStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [GitOutputParser].
 *
 * Covers:
 * - parseDiff: empty input, no-hunk preamble, binary-file markers, multiple hunks
 * - parseNumstat: binary lines (`- -`) are skipped, valid lines are parsed
 * - parseFileStatus: 'C' (COPIED) and 'R' (RENAMED) characters
 */
class GitOutputParserTest {

    // -------------------------------------------------------------------------
    // parseDiff
    // -------------------------------------------------------------------------

    @Test
    fun parseDiff_emptyInput_returnsEmptyList() {
        // Arrange
        val input = ""

        // Act
        val result = GitOutputParser.parseDiff(input)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseDiff_blankLines_returnsEmptyList() {
        // Arrange
        val input = "\n\n\n"

        // Act
        val result = GitOutputParser.parseDiff(input)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseDiff_preambleWithoutHunkHeader_returnsEmptyList() {
        // Arrange: diff header lines without any @@ hunk marker
        val input = """
            diff --git a/Foo.kt b/Foo.kt
            index 1234567..abcdef0 100644
            --- a/Foo.kt
            +++ b/Foo.kt
        """.trimIndent()

        // Act
        val result = GitOutputParser.parseDiff(input)

        // Assert: no @@ lines → no hunks
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseDiff_singleHunk_parsesHeaderAndLines() {
        // Arrange
        val input = """
            @@ -1,3 +1,4 @@
             context line
            -removed line
            +added line
             another context
        """.trimIndent()

        // Act
        val result = GitOutputParser.parseDiff(input)

        // Assert
        assertEquals(1, result.size)
        val hunk = result[0]
        assertEquals("@@ -1,3 +1,4 @@", hunk.header)
        assertEquals(4, hunk.lines.size)
        assertEquals(DiffLineType.CONTEXT, hunk.lines[0].type)
        assertEquals(DiffLineType.DELETION, hunk.lines[1].type)
        assertEquals(DiffLineType.ADDITION, hunk.lines[2].type)
        assertEquals(DiffLineType.CONTEXT, hunk.lines[3].type)
    }

    @Test
    fun parseDiff_binaryFileMarker_noBinaryHunk_returnsEmptyList() {
        // Arrange: binary diff output — no @@ lines, just metadata
        val input = """
            diff --git a/logo.png b/logo.png
            index 0000000..1234567 100644
            Binary files a/logo.png and b/logo.png differ
        """.trimIndent()

        // Act
        val result = GitOutputParser.parseDiff(input)

        // Assert: binary diffs have no @@ hunks → empty
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseDiff_multipleHunks_parsesAllHunks() {
        // Arrange
        val input = """
            @@ -1,2 +1,2 @@
             context A
            -old A
            +new A
            @@ -10,2 +10,2 @@
             context B
            -old B
            +new B
        """.trimIndent()

        // Act
        val result = GitOutputParser.parseDiff(input)

        // Assert
        assertEquals(2, result.size)
        assertEquals("@@ -1,2 +1,2 @@", result[0].header)
        assertEquals("@@ -10,2 +10,2 @@", result[1].header)
    }

    @Test
    fun parseDiff_lineNumbers_areTrackedCorrectly() {
        // Arrange: single hunk starting at old=5, new=5
        val input = "@@ -5,3 +5,3 @@\n context\n-removed\n+added\n"

        // Act
        val result = GitOutputParser.parseDiff(input)

        // Assert
        assertEquals(1, result.size)
        val lines = result[0].lines
        // context line — both numbers present
        assertEquals(5, lines[0].oldLineNumber)
        assertEquals(5, lines[0].newLineNumber)
        // deletion — old number present, new is null
        assertEquals(6, lines[1].oldLineNumber)
        assertNull(lines[1].newLineNumber)
        // addition — new number present, old is null
        assertNull(lines[2].oldLineNumber)
        assertEquals(6, lines[2].newLineNumber)
    }

    // -------------------------------------------------------------------------
    // parseNumstat
    // -------------------------------------------------------------------------

    @Test
    fun parseNumstat_emptyInput_returnsEmptyMap() {
        // Arrange
        val input = ""

        // Act
        val result = GitOutputParser.parseNumstat(input)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseNumstat_validLine_parsesAddedAndDeleted() {
        // Arrange
        val input = "10\t3\tsrc/Foo.kt"

        // Act
        val result = GitOutputParser.parseNumstat(input)

        // Assert
        assertEquals(1, result.size)
        val stat = result["src/Foo.kt"]!!
        assertEquals(10, stat.added)
        assertEquals(3, stat.deleted)
    }

    @Test
    fun parseNumstat_binaryLine_isDashDash_isSkipped() {
        // Arrange: binary file shows "- -" for both counters
        val input = "-\t-\tresources/image.png"

        // Act
        val result = GitOutputParser.parseNumstat(input)

        // Assert: binary line skipped — toIntOrNull() on "-" returns null → continue
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseNumstat_mixedBinaryAndTextLines_onlyTextLinesParsed() {
        // Arrange
        val input = """
            5	2	src/Main.kt
            -	-	assets/logo.png
            1	0	src/Util.kt
        """.trimIndent()

        // Act
        val result = GitOutputParser.parseNumstat(input)

        // Assert
        assertEquals(2, result.size)
        assertEquals(5, result["src/Main.kt"]!!.added)
        assertEquals(1, result["src/Util.kt"]!!.added)
        assertNull(result["assets/logo.png"])
    }

    @Test
    fun parseNumstat_malformedLine_isSkipped() {
        // Arrange: line with only 2 tab-separated parts — no path
        val input = "10\t3"

        // Act
        val result = GitOutputParser.parseNumstat(input)

        // Assert
        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // parseFileStatus
    // -------------------------------------------------------------------------

    @Test
    fun parseFileStatus_charC_returnsCopied() {
        // Act
        val result = GitOutputParser.parseFileStatus('C')

        // Assert
        assertEquals(GitFileStatus.COPIED, result)
    }

    @Test
    fun parseFileStatus_charR_returnsRenamed() {
        // Act
        val result = GitOutputParser.parseFileStatus('R')

        // Assert
        assertEquals(GitFileStatus.RENAMED, result)
    }

    @Test
    fun parseFileStatus_charM_returnsModified() {
        assertEquals(GitFileStatus.MODIFIED, GitOutputParser.parseFileStatus('M'))
    }

    @Test
    fun parseFileStatus_charA_returnsAdded() {
        assertEquals(GitFileStatus.ADDED, GitOutputParser.parseFileStatus('A'))
    }

    @Test
    fun parseFileStatus_charD_returnsDeleted() {
        assertEquals(GitFileStatus.DELETED, GitOutputParser.parseFileStatus('D'))
    }

    @Test
    fun parseFileStatus_unknownChar_returnsNull() {
        // Assert
        assertNull(GitOutputParser.parseFileStatus('X'))
        assertNull(GitOutputParser.parseFileStatus('?'))
        assertNull(GitOutputParser.parseFileStatus(' '))
    }
}
