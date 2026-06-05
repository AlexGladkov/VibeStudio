// MARK: - YAMLFrontmatterParserTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class YAMLFrontmatterParserTests: XCTestCase {

    private let parser = YAMLFrontmatterParser()

    private func parse(_ line: String, context: LineContext = .initial)
        -> (tokens: [SyntaxToken], nextContext: LineContext) {
        let range = NSRange(location: 0, length: (line as NSString).length)
        return parser.parseLine(line, lineRange: range, context: context)
    }

    func test_openingDelimiter_togglesInFrontmatter() {
        let (tokens, ctx) = parse("---")
        XCTAssertEqual(tokens.first?.kind, .frontmatterDelimiter)
        XCTAssertTrue(ctx.inFrontmatter)
    }

    func test_closingDelimiter_togglesOff() {
        var ctx = LineContext()
        ctx.inFrontmatter = true
        let (tokens, after) = parse("---", context: ctx)
        XCTAssertEqual(tokens.first?.kind, .frontmatterDelimiter)
        XCTAssertFalse(after.inFrontmatter)
    }

    func test_keyValue_emitsBothTokens() {
        var ctx = LineContext()
        ctx.inFrontmatter = true
        let (tokens, _) = parse("name: hello", context: ctx)
        XCTAssertEqual(tokens.count, 2)
        XCTAssertEqual(tokens[0].kind, .frontmatterKey)
        XCTAssertEqual(tokens[1].kind, .frontmatterValue)
    }

    func test_keyOnly_noColon_emitsValueOnly() {
        var ctx = LineContext()
        ctx.inFrontmatter = true
        let (tokens, _) = parse("- item", context: ctx)
        XCTAssertEqual(tokens.count, 1)
        XCTAssertEqual(tokens.first?.kind, .frontmatterValue)
    }

    func test_outsideFrontmatter_returnsNoTokens() {
        let (tokens, _) = parse("name: hello") // ctx.inFrontmatter == false
        XCTAssertTrue(tokens.isEmpty)
    }
}
