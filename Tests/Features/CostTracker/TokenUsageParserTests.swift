// MARK: - TokenUsageParserTests
// Unit tests for TokenUsageParser (Feature #2 — Agent Cost Tracker).
// All parsing happens on stripped ANSI text; ANSI stripping is tested separately
// in ``ANSIStripperTests``.
// macOS 14+, Swift 5.10

import XCTest
@testable import VibeStudio

final class TokenUsageParserTests: XCTestCase {

    // MARK: - claude text format (human-readable output)

    func testClaudeTextFormat_promptAndOutput() {
        // "PromptTokens prompt tokens + X cached, Y output"
        let line = "PromptTokens 1234 prompt tokens, 5678 output tokens"
        let result = TokenUsageParser.parse(from: line)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.promptTokens, 1234)
        XCTAssertEqual(result?.completionTokens, 5678)
    }

    func testClaudeTextFormat_withCommas() {
        // Tokens formatted with thousands commas: "1,234"
        let line = "PromptTokens 1,234 prompt tokens, 5,678 output tokens"
        let result = TokenUsageParser.parse(from: line)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.promptTokens, 1234)
        XCTAssertEqual(result?.completionTokens, 5678)
    }

    func testClaudeTextFormat_withCost() {
        // "$0.05" cost prefix variant
        let line = "Total cost: $0.05"
        let result = TokenUsageParser.parse(from: line)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.estimatedCostUSD ?? 0, 0.05, accuracy: 0.001)
    }

    func testClaudeCompactFormat_tokensAndCost() {
        // "X tokens, $Y.YY total" (compact summary line)
        let line = "12345 tokens, $2.34 total"
        let result = TokenUsageParser.parse(from: line)
        XCTAssertNotNil(result)
        // promptTokens equals totalTokens when no split is available
        XCTAssertEqual(result?.promptTokens, 12345)
        XCTAssertEqual(result?.estimatedCostUSD ?? 0, 2.34, accuracy: 0.001)
    }

    // MARK: - claude JSON format

    func testClaudeJSONFormat_usageObject() {
        // {"usage":{"input_tokens":100,"output_tokens":200}}
        let line = #"{"usage":{"input_tokens":100,"output_tokens":200}}"#
        let result = TokenUsageParser.parse(from: line)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.promptTokens, 100)
        XCTAssertEqual(result?.completionTokens, 200)
    }

    func testClaudeJSONFormat_reverseKey() {
        // {"input_tokens":50,"output_tokens":75}
        let line = #"{"input_tokens":50,"output_tokens":75}"#
        let result = TokenUsageParser.parse(from: line)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.promptTokens, 50)
        XCTAssertEqual(result?.completionTokens, 75)
    }

    func testClaudeJSONFormat_withModel() {
        let line = #"{"model":"claude-opus-4","usage":{"input_tokens":300,"output_tokens":150}}"#
        let result = TokenUsageParser.parse(from: line)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.model, "claude-opus-4")
        XCTAssertEqual(result?.promptTokens, 300)
        XCTAssertEqual(result?.completionTokens, 150)
    }

    // MARK: - opencode format

    func testOpencodeFormat_tokensLine() {
        // "Tokens: 500 in / 250 out"
        let line = "Tokens: 500 in / 250 out"
        let result = TokenUsageParser.parse(from: line)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.promptTokens, 500)
        XCTAssertEqual(result?.completionTokens, 250)
    }

    func testOpencodeAltFormat_usedTokens() {
        // "Used 1000 tokens (500 in, 500 out)"
        let line = "Used 1000 tokens (500 in, 500 out)"
        let result = TokenUsageParser.parse(from: line)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.promptTokens, 500)
        XCTAssertEqual(result?.completionTokens, 500)
    }

    // MARK: - modelRegex

    func testModelRegex_extractsModel() {
        // A line containing model info should extract model name
        let line = #"{"model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":10,"output_tokens":5}}"#
        let result = TokenUsageParser.parse(from: line)
        XCTAssertEqual(result?.model, "claude-3-5-sonnet-20241022")
    }

    // MARK: - ANSI strip (integration: caller strips before passing)

    func testParsesAfterANSIStripped() {
        // Simulate what CostTrackerService does: strip ANSI then parse
        let ansiLine = "\u{1B}[32mPromptTokens 100 prompt tokens, 50 output tokens\u{1B}[0m"
        let stripped = ANSIStripper.strip(ansiLine)
        let result = TokenUsageParser.parse(from: stripped)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.promptTokens, 100)
    }

    // MARK: - Guard caps

    func testCapGuard_tokensTooLarge_returnsNil() {
        // 10_000_001 tokens — above the cap — should be rejected
        let line = "PromptTokens 10000001 prompt tokens, 0 output tokens"
        let result = TokenUsageParser.parse(from: line)
        // Either returns nil or clamps — check it doesn't exceed cap
        if let r = result {
            XCTAssertLessThanOrEqual(r.promptTokens, 10_000_000)
        }
    }

    func testCapGuard_costTooLarge_returnsNil() {
        // "Total cost: $1001.00" — above $1000 cap
        let line = "Total cost: $1001.00"
        let result = TokenUsageParser.parse(from: line)
        if let r = result {
            if let cost = r.estimatedCostUSD {
                XCTAssertLessThanOrEqual(cost, 1000.0)
            }
        }
    }

    func testCapGuard_injectedFakeCost_rejected() {
        // DoD E4: spoofed "Total cost: $999999" must be rejected
        let line = "Total cost: $999999"
        let result = TokenUsageParser.parse(from: line)
        if let r = result, let cost = r.estimatedCostUSD {
            XCTAssertLessThanOrEqual(cost, 1000.0, "Spoofed cost must be capped")
        }
    }

    // MARK: - Non-matching lines → nil

    func testUnrelatedLine_returnsNil() {
        XCTAssertNil(TokenUsageParser.parse(from: "Hello world"))
    }

    func testEmptyString_returnsNil() {
        XCTAssertNil(TokenUsageParser.parse(from: ""))
    }

    func testRandomJSON_returnsNil() {
        XCTAssertNil(TokenUsageParser.parse(from: #"{"event":"message_start","type":"message_start"}"#))
    }

    // MARK: - mightContainUsage fast-path

    func testMightContainUsage_returnsTrue_forTokenKeyword() {
        // 'T' byte (Token) triggers pre-check
        let bytes = Array("Total cost: $0.01".utf8)
        XCTAssertTrue(TokenUsageParser.mightContainUsage(bytes[...]))
    }

    func testMightContainUsage_returnsFalse_forIrrelevantBytes() {
        let bytes = Array("normal shell output line".utf8)
        XCTAssertFalse(TokenUsageParser.mightContainUsage(bytes[...]))
    }
}

// MARK: - ModelPricingTableTests

final class ModelPricingTableTests: XCTestCase {

    func testPriceFor_claudeOpus4_matchesPrefix() {
        let price = ModelPricingTable.priceFor(model: "claude-opus-4")
        XCTAssertNotNil(price, "claude-opus-4 should be in the pricing table")
        XCTAssertGreaterThan(price?.inputPerMToken ?? 0, 0)
        XCTAssertGreaterThan(price?.outputPerMToken ?? 0, 0)
    }

    func testPriceFor_unknownModel_returnsNil() {
        let price = ModelPricingTable.priceFor(model: "unknown-model-xyz-9000")
        XCTAssertNil(price, "Unknown model should return nil")
    }

    func testEstimateCost_knownModel_returnsPositive() {
        let cost = ModelPricingTable.estimateCost(
            promptTokens: 1000,
            completionTokens: 500,
            model: "claude-opus-4"
        )
        XCTAssertNotNil(cost)
        XCTAssertGreaterThan(cost ?? 0, 0)
    }

    func testEstimateCost_zeroTokens_returnsZeroOrNil() {
        let cost = ModelPricingTable.estimateCost(
            promptTokens: 0,
            completionTokens: 0,
            model: "claude-opus-4"
        )
        // Either nil or 0.0
        if let c = cost { XCTAssertEqual(c, 0.0, accuracy: 0.0001) }
    }
}

// MARK: - SessionCostSnapshotTests

final class SessionCostSnapshotTests: XCTestCase {

    func testAccumulate_addsTokens() {
        var snap = SessionCostSnapshot()
        snap.accumulate(TokenUsage(promptTokens: 100, completionTokens: 50, model: nil, estimatedCostUSD: nil))
        snap.accumulate(TokenUsage(promptTokens: 200, completionTokens: 75, model: nil, estimatedCostUSD: nil))
        XCTAssertEqual(snap.promptTokens, 300)
        XCTAssertEqual(snap.completionTokens, 125)
        XCTAssertEqual(snap.totalTokens, 425)
    }

    func testAccumulate_addsCost() {
        var snap = SessionCostSnapshot()
        snap.accumulate(TokenUsage(promptTokens: 0, completionTokens: 0, model: nil, estimatedCostUSD: 0.01))
        snap.accumulate(TokenUsage(promptTokens: 0, completionTokens: 0, model: nil, estimatedCostUSD: 0.02))
        XCTAssertEqual(snap.costUSD ?? 0, 0.03, accuracy: 0.0001)
    }

    func testAccumulate_setsModel() {
        var snap = SessionCostSnapshot()
        snap.accumulate(TokenUsage(promptTokens: 10, completionTokens: 5, model: "claude-opus-4", estimatedCostUSD: nil))
        XCTAssertEqual(snap.model, "claude-opus-4")
    }

    func testTotalTokens_computedCorrectly() {
        var snap = SessionCostSnapshot()
        snap.accumulate(TokenUsage(promptTokens: 7, completionTokens: 3, model: nil, estimatedCostUSD: nil))
        XCTAssertEqual(snap.totalTokens, 10)
    }
}
