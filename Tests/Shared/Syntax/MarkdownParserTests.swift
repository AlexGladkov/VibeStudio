// MARK: - MarkdownParserTests
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class MarkdownParserTests: XCTestCase {

    private let parser = MarkdownParser()

    private func parse(_ line: String, context: LineContext = .initial)
        -> (tokens: [SyntaxToken], nextContext: LineContext) {
        let range = NSRange(location: 0, length: (line as NSString).length)
        return parser.parseLine(line, lineRange: range, context: context)
    }

    // MARK: - Headings

    func test_heading_singleHash() {
        let (tokens, _) = parse("# Title")
        XCTAssertEqual(tokens.count, 1)
        XCTAssertEqual(tokens.first?.kind, .heading)
    }

    func test_heading_h6() {
        let (tokens, _) = parse("###### Six")
        XCTAssertEqual(tokens.first?.kind, .heading)
    }

    func test_heading_sevenHashes_isNotHeading() {
        let (tokens, _) = parse("####### Too deep")
        XCTAssertFalse(tokens.contains { $0.kind == .heading })
    }

    func test_heading_noSpaceAfterHash_isNotHeading() {
        let (tokens, _) = parse("#NoSpace")
        XCTAssertFalse(tokens.contains { $0.kind == .heading })
    }

    // MARK: - Code Fence

    func test_codeFence_openClose() {
        let (openTokens, openCtx) = parse("```swift")
        XCTAssertEqual(openTokens.first?.kind, .codeBlockFence)
        XCTAssertTrue(openCtx.inCodeBlock)
        XCTAssertEqual(openCtx.codeBlockLanguage, "swift")

        let (bodyTokens, bodyCtx) = parse("let x = 1", context: openCtx)
        XCTAssertEqual(bodyTokens.first?.kind, .codeBlockBody)
        XCTAssertTrue(bodyCtx.inCodeBlock)

        let (closeTokens, closeCtx) = parse("```", context: bodyCtx)
        XCTAssertEqual(closeTokens.first?.kind, .codeBlockFence)
        XCTAssertFalse(closeCtx.inCodeBlock)
        XCTAssertNil(closeCtx.codeBlockFence)
    }

    func test_codeFence_mismatchedFenceDoesNotClose() {
        var ctx = LineContext()
        let (_, ctx1) = parse("```", context: ctx)
        ctx = ctx1
        let (_, ctx2) = parse("~~~", context: ctx)
        XCTAssertTrue(ctx2.inCodeBlock, "Tilde fence must not close backtick block")
    }

    // MARK: - Blockquote / List

    func test_blockquote() {
        let (tokens, _) = parse("> quoted")
        XCTAssertEqual(tokens.first?.kind, .blockquote)
    }

    func test_listMarker_unordered() {
        let (tokens, _) = parse("- item")
        XCTAssertEqual(tokens.first?.kind, .listMarker)
    }

    func test_listMarker_ordered() {
        let (tokens, _) = parse("1. item")
        XCTAssertEqual(tokens.first?.kind, .listMarker)
    }

    // MARK: - Inline

    func test_bold() {
        let (tokens, _) = parse("a **bold** word")
        XCTAssertTrue(tokens.contains { $0.kind == .bold })
    }

    func test_italic() {
        let (tokens, _) = parse("an _italic_ word")
        XCTAssertTrue(tokens.contains { $0.kind == .italic })
    }

    func test_inlineCode() {
        let (tokens, _) = parse("call `foo()` here")
        XCTAssertTrue(tokens.contains { $0.kind == .inlineCode })
    }

    func test_link() {
        let (tokens, _) = parse("see [docs](https://x.example)")
        XCTAssertTrue(tokens.contains { $0.kind == .link })
        XCTAssertTrue(tokens.contains { $0.kind == .linkURL })
    }

    func test_unmatchedBold_doesNotProduceBoldToken() {
        let (tokens, _) = parse("only **open here")
        XCTAssertFalse(tokens.contains { $0.kind == .bold })
    }

    func test_plainLine_producesNoTokens() {
        let (tokens, _) = parse("just regular text")
        XCTAssertTrue(tokens.isEmpty)
    }
}
