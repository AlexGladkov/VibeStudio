import XCTest
@testable import VibeStudio

/// Unit tests for ``SyntaxTokenizer.tokenizeAll`` — the shared line-by-line
/// document walk. Uses a stub ``SyntaxParsing`` that emits one token per line
/// and records every call so we can assert line count, ranges, and that the
/// ``LineContext`` is threaded between lines. Pure: no main-actor / IO.
final class SyntaxTokenizerTests: XCTestCase {

    // MARK: - Test doubles

    /// Thread-safe recorder of every `parseLine` invocation.
    private final class CallRecorder: @unchecked Sendable {
        private let lock = NSLock()
        private var _lines: [String] = []
        private var _ranges: [NSRange] = []
        private var _contexts: [LineContext] = []

        func record(line: String, range: NSRange, context: LineContext) {
            lock.lock(); defer { lock.unlock() }
            _lines.append(line)
            _ranges.append(range)
            _contexts.append(context)
        }

        var lines: [String] { lock.lock(); defer { lock.unlock() }; return _lines }
        var ranges: [NSRange] { lock.lock(); defer { lock.unlock() }; return _ranges }
        var contexts: [LineContext] { lock.lock(); defer { lock.unlock() }; return _contexts }
        var callCount: Int { lock.lock(); defer { lock.unlock() }; return _lines.count }
    }

    /// Emits exactly one `.plain` token spanning the line and toggles
    /// `inFrontmatter` in the returned context so context threading is
    /// observable across successive lines.
    private struct StubParser: SyntaxParsing {
        let recorder: CallRecorder
        var supportedExtensions: [String] { [] }

        func parseLine(
            _ line: String,
            lineRange: NSRange,
            context: LineContext
        ) -> (tokens: [SyntaxToken], nextContext: LineContext) {
            recorder.record(line: line, range: lineRange, context: context)
            var next = context
            next.inFrontmatter.toggle()
            return ([SyntaxToken(kind: .plain, range: lineRange)], next)
        }
    }

    // MARK: - Empty

    func testEmptyDocumentProducesNoTokens() {
        let recorder = CallRecorder()
        let tokens = SyntaxTokenizer.tokenizeAll(text: "", parser: StubParser(recorder: recorder))
        XCTAssertEqual(tokens.count, 0)
        XCTAssertEqual(recorder.callCount, 0)
    }

    // MARK: - Line counting

    func testTwoLinesNoTrailingNewline() {
        let recorder = CallRecorder()
        let tokens = SyntaxTokenizer.tokenizeAll(text: "a\nb", parser: StubParser(recorder: recorder))
        XCTAssertEqual(tokens.count, 2)
        XCTAssertEqual(recorder.callCount, 2)
        XCTAssertEqual(recorder.lines, ["a", "b"])
    }

    func testTrailingNewlineDoesNotAddEmptyLine() {
        let recorder = CallRecorder()
        let tokens = SyntaxTokenizer.tokenizeAll(text: "a\nb\n", parser: StubParser(recorder: recorder))
        XCTAssertEqual(tokens.count, 2)
        XCTAssertEqual(recorder.lines, ["a", "b"])
    }

    // MARK: - CRLF

    func testCRLFLineEndingsYieldCorrectLineCountAndFullCoverage() {
        let recorder = CallRecorder()
        let text = "a\r\nb\r\n"
        let tokens = SyntaxTokenizer.tokenizeAll(text: text, parser: StubParser(recorder: recorder))

        XCTAssertEqual(tokens.count, 2)
        XCTAssertEqual(recorder.callCount, 2)
        // Only the trailing 0x0A is stripped from the content range; the 0x0D
        // remains, matching the tokenizer's single-terminator-char rule.
        XCTAssertEqual(recorder.lines, ["a\r", "b\r"])
        // Ranges tile the whole document (each line range spans "x\r\n" = 3 UTF-16 units).
        XCTAssertEqual(recorder.ranges.first, NSRange(location: 0, length: 3))
        XCTAssertEqual(NSMaxRange(recorder.ranges.last!), (text as NSString).length)
    }

    // MARK: - Context threading

    func testLineContextIsThreadedBetweenLines() {
        let recorder = CallRecorder()
        _ = SyntaxTokenizer.tokenizeAll(text: "a\nb\nc", parser: StubParser(recorder: recorder))

        let contexts = recorder.contexts
        XCTAssertEqual(contexts.count, 3)
        // First line receives the initial context; the stub toggles thereafter.
        XCTAssertFalse(contexts[0].inFrontmatter)
        XCTAssertTrue(contexts[1].inFrontmatter)
        XCTAssertFalse(contexts[2].inFrontmatter)
    }
}
