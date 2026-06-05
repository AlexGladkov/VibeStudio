// MARK: - CodeSpeakParserTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class CodeSpeakParserTests: XCTestCase {

    private let parser = CodeSpeakParser()

    private func parse(_ line: String, context: LineContext = .initial)
        -> (tokens: [SyntaxToken], nextContext: LineContext) {
        let range = NSRange(location: 0, length: (line as NSString).length)
        return parser.parseLine(line, lineRange: range, context: context)
    }

    func test_frontmatterDelimiter_routedToYAMLParser() {
        let (tokens, ctx) = parse("---")
        XCTAssertEqual(tokens.first?.kind, .frontmatterDelimiter)
        XCTAssertTrue(ctx.inFrontmatter)
    }

    func test_insideFrontmatter_keyValueParsed() {
        var ctx = LineContext()
        ctx.inFrontmatter = true
        let (tokens, _) = parse("name: spec", context: ctx)
        XCTAssertTrue(tokens.contains { $0.kind == .frontmatterKey })
        XCTAssertTrue(tokens.contains { $0.kind == .frontmatterValue })
    }

    func test_doubleSlashComment_emitsComment() {
        let (tokens, _) = parse("// directive note")
        XCTAssertEqual(tokens.count, 1)
        XCTAssertEqual(tokens.first?.kind, .comment)
    }

    func test_heading_delegatesToMarkdown() {
        let (tokens, _) = parse("## Section")
        XCTAssertEqual(tokens.first?.kind, .heading)
    }

    func test_plainBody_delegatesToMarkdown_returnsEmpty() {
        let (tokens, _) = parse("body text")
        XCTAssertTrue(tokens.isEmpty)
    }

    func test_codeFenceContext_carriedFromMarkdown() {
        let (_, openCtx) = parse("```swift")
        XCTAssertTrue(openCtx.inCodeBlock)
        let (bodyTokens, _) = parse("let x = 1", context: openCtx)
        XCTAssertEqual(bodyTokens.first?.kind, .codeBlockBody)
    }
}
