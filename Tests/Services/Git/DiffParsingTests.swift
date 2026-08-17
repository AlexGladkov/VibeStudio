import XCTest
@testable import VibeStudio

/// Focused tests for ``GitService.parseDiff`` edge cases that affect line-number
/// accuracy: the `\ No newline at end of file` metadata marker and malformed
/// hunk headers. These complement ``GitStatusParserTests`` (happy-path parsing).
final class DiffParsingTests: XCTestCase {

    private var sut: GitService!

    override func setUp() {
        super.setUp()
        sut = GitService()
    }

    override func tearDown() {
        sut = nil
        super.tearDown()
    }

    // MARK: - "\ No newline at end of file" marker

    /// The marker line must NOT be emitted as a diff line and must NOT advance
    /// the old/new counters. When it appears mid-hunk (old file lacked a
    /// trailing newline, new file adds lines after it), every following line's
    /// number must stay correct.
    func testNoNewlineMarkerDoesNotShiftLineNumbersWithinHunk() async {
        // Old file: "line1\nline2" (no trailing newline).
        // New file: "line1\nline2\nline3\n".
        let diffOutput = """
        diff --git a/file.swift b/file.swift
        @@ -1,2 +1,3 @@
         line1
        -line2
        \\ No newline at end of file
        +line2
        +line3
        """
        let hunks = sut.parseDiff(diffOutput)

        XCTAssertEqual(hunks.count, 1)
        let lines = hunks[0].lines

        // The marker line must be dropped: 1 context + 1 deletion + 2 additions.
        XCTAssertEqual(lines.count, 4)
        XCTAssertFalse(
            lines.contains { $0.content.contains("No newline") },
            "\\ No newline marker leaked into parsed diff lines"
        )

        // Context "line1".
        XCTAssertEqual(lines[0].type, .context)
        XCTAssertEqual(lines[0].oldLineNumber, 1)
        XCTAssertEqual(lines[0].newLineNumber, 1)

        // Deletion "line2".
        XCTAssertEqual(lines[1].type, .deletion)
        XCTAssertEqual(lines[1].oldLineNumber, 2)
        XCTAssertNil(lines[1].newLineNumber)

        // Additions must NOT be shifted by the marker: new lines 2 and 3.
        XCTAssertEqual(lines[2].type, .addition)
        XCTAssertEqual(lines[2].content, "line2")
        XCTAssertEqual(lines[2].newLineNumber, 2)

        XCTAssertEqual(lines[3].type, .addition)
        XCTAssertEqual(lines[3].content, "line3")
        XCTAssertEqual(lines[3].newLineNumber, 3)
    }

    /// Two hunks separated by a `\ No newline` marker: the marker at the end of
    /// hunk 1 must not appear as a line, and hunk 2's numbers stay correct.
    func testNoNewlineMarkerBetweenHunks() async {
        let diffOutput = """
        diff --git a/file.swift b/file.swift
        @@ -1,2 +1,2 @@
         alpha
        -beta
        +beta2
        \\ No newline at end of file
        @@ -10,2 +10,3 @@
         gamma
        +delta
         epsilon
        """
        let hunks = sut.parseDiff(diffOutput)

        XCTAssertEqual(hunks.count, 2)

        // Hunk 1: context + deletion + addition (marker dropped).
        let header1 = hunks[0].lines
        XCTAssertEqual(header1.count, 3)
        XCTAssertFalse(header1.contains { $0.content.contains("No newline") })
        XCTAssertEqual(header1[0].oldLineNumber, 1)   // alpha
        XCTAssertEqual(header1[1].oldLineNumber, 2)   // -beta
        XCTAssertEqual(header1[2].newLineNumber, 2)   // +beta2

        // Hunk 2 numbers derive from its own header — must be intact.
        let header2 = hunks[1].lines
        XCTAssertEqual(header2.count, 3)
        XCTAssertEqual(header2[0].type, .context)
        XCTAssertEqual(header2[0].content, "gamma")
        XCTAssertEqual(header2[0].oldLineNumber, 10)
        XCTAssertEqual(header2[0].newLineNumber, 10)

        XCTAssertEqual(header2[1].type, .addition)
        XCTAssertEqual(header2[1].content, "delta")
        XCTAssertEqual(header2[1].newLineNumber, 11)

        XCTAssertEqual(header2[2].type, .context)
        XCTAssertEqual(header2[2].content, "epsilon")
        XCTAssertEqual(header2[2].oldLineNumber, 11)
        XCTAssertEqual(header2[2].newLineNumber, 12)
    }

    // MARK: - Malformed hunk header

    /// A `@@` header without parseable line numbers yields (0, 0), so the first
    /// body line starts numbering at 0 rather than crashing or drifting.
    func testMalformedHunkHeaderStartsAtZero() async {
        let diffOutput = """
        diff --git a/file.swift b/file.swift
        @@ garbage header @@
        +added line
         context line
        """
        let hunks = sut.parseDiff(diffOutput)

        XCTAssertEqual(hunks.count, 1)
        let lines = hunks[0].lines
        XCTAssertEqual(lines.count, 2)

        // parseHunkLineNumbers found no -N / +N → (0, 0): numbering starts at 0.
        XCTAssertEqual(lines[0].type, .addition)
        XCTAssertNil(lines[0].oldLineNumber)
        XCTAssertEqual(lines[0].newLineNumber, 0)

        // Context line advances from the (0, 0) origin: old 0, new 1.
        XCTAssertEqual(lines[1].type, .context)
        XCTAssertEqual(lines[1].oldLineNumber, 0)
        XCTAssertEqual(lines[1].newLineNumber, 1)
    }
}
