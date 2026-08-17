// MARK: - TokenUsageParser
// Pure stateless parser for agent CLI usage/cost lines.
// macOS 14+, Swift 5.10

import Foundation

/// Stateless parser that extracts ``TokenUsage`` from a single line of
/// agent CLI output. Pure and side-effect free — testable in isolation.
///
/// Supported formats:
/// - Claude text: `"Total tokens: 1,234 (1,000 input, 234 output)"`
/// - Claude cost: `"Total cost: $0.0234"`
/// - Claude compact: `"Tokens: 1,234 input, 234 output (total: 1,468)"`
/// - Claude JSON fragment: `{"usage":{"input_tokens":N,"output_tokens":N}}`
/// - opencode: `"Tokens used: 1234 (input: 234, output: 1000)"`
/// - Combined usage+model: any of the above with model embedded
///
/// ANSI escape sequences must be stripped BEFORE calling `parse(from:)`.
/// ``CostTrackerService`` handles stripping via ``ANSIStripper``.
///
/// Guard caps (E3 / DoD): values outside these ranges are rejected to
/// protect against injected fake usage lines from a rogue agent:
/// - tokens: 0...10_000_000
/// - cost (USD): 0.0...1_000.0
enum TokenUsageParser {

    // MARK: - Caps

    private static let maxTokens = 10_000_000
    private static let maxCostUSD = 1_000.0

    // MARK: - Precompiled Regexes

    // Claude text format: "Total tokens: 1,234 (1,000 input, 234 output)"
    private static let claudeTokensTextRegex: NSRegularExpression? = try? NSRegularExpression(
        pattern: #"[Tt]otal\s+tokens?:\s*([\d,]+)\s*\(([\d,]+)\s+input,?\s+([\d,]+)\s+output"#
    )

    // Claude cost format: "Total cost: $0.0234" or "Cost: $0.0234"
    private static let claudeCostRegex: NSRegularExpression? = try? NSRegularExpression(
        pattern: #"[Cc]ost:\s*\$\s*([\d]+\.[\d]+)"#
    )

    // Claude compact: "Tokens: 1,234 input, 234 output" or similar
    private static let claudeCompactRegex: NSRegularExpression? = try? NSRegularExpression(
        pattern: #"[Tt]okens?:\s*([\d,]+)\s+input,?\s+([\d,]+)\s+output"#
    )

    // Claude JSON usage fragment: {"usage":{"input_tokens":N,"output_tokens":N}}
    private static let claudeJSONRegex: NSRegularExpression? = try? NSRegularExpression(
        pattern: #""input_tokens"\s*:\s*(\d+)[^}]*"output_tokens"\s*:\s*(\d+)"#
    )

    // Reverse JSON order variant
    private static let claudeJSONReverseRegex: NSRegularExpression? = try? NSRegularExpression(
        pattern: #""output_tokens"\s*:\s*(\d+)[^}]*"input_tokens"\s*:\s*(\d+)"#
    )

    // opencode format: "Tokens used: 1234 (input: 234, output: 1000)"
    private static let opencodeRegex: NSRegularExpression? = try? NSRegularExpression(
        pattern: #"[Tt]okens?\s+used:\s*([\d,]+)\s*\(?input:\s*([\d,]+),?\s+output:\s*([\d,]+)"#
    )

    // opencode alt: "input: N tokens, output: M tokens"
    private static let opencodeAltRegex: NSRegularExpression? = try? NSRegularExpression(
        pattern: #"input:\s*([\d,]+)\s+tokens?,\s+output:\s*([\d,]+)\s+tokens?"#
    )

    // Model extraction: "model: claude-xxx-xxx" or "Model: ..."
    private static let modelRegex: NSRegularExpression? = try? NSRegularExpression(
        pattern: #"[Mm]odel[: ]+([a-zA-Z0-9._-]+)"#
    )

    // MARK: - Fast Pre-check

    /// Byte-level pre-check: returns true only when the slice contains at least
    /// one of the ASCII bytes likely to appear in a usage line.
    /// Avoids expensive String/regex for irrelevant PTY chunks.
    static func mightContainUsage(_ slice: ArraySlice<UInt8>) -> Bool {
        // Look for '$', 't', 'T', 'i', 'u' — common in cost/tokens/input/usage
        for byte in slice {
            if byte == UInt8(ascii: "$") || byte == UInt8(ascii: "T")
               || byte == UInt8(ascii: "t") || byte == UInt8(ascii: "{") {
                return true
            }
        }
        return false
    }

    // MARK: - Parse

    /// Parse a single (already ANSI-stripped) line into a ``TokenUsage``, or
    /// `nil` if no usage data is present or values are outside the guard caps.
    ///
    /// - Parameter line: A single line of agent CLI output (no newline char).
    ///   Must already have ANSI escape sequences removed.
    /// - Returns: `TokenUsage` or `nil`.
    static func parse(from line: String) -> TokenUsage? {
        guard !line.isEmpty else { return nil }

        let ns = line as NSString
        let fullRange = NSRange(location: 0, length: ns.length)

        // 1. Try Claude text format (most common, has input+output)
        if let regex = claudeTokensTextRegex,
           let match = regex.firstMatch(in: line, range: fullRange),
           match.numberOfRanges >= 4 {
            let total = parseTokenCount(ns, match.range(at: 1))
            let input = parseTokenCount(ns, match.range(at: 2))
            let output = parseTokenCount(ns, match.range(at: 3))
            if let input, let output, validTokens(input), validTokens(output) {
                _ = total
                let model = extractModel(from: line)
                let cost = extractCost(from: line)
                return TokenUsage(
                    promptTokens: input,
                    completionTokens: output,
                    model: model,
                    estimatedCostUSD: cost
                )
            }
        }

        // 2. Try Claude compact format
        if let regex = claudeCompactRegex,
           let match = regex.firstMatch(in: line, range: fullRange),
           match.numberOfRanges >= 3 {
            let input = parseTokenCount(ns, match.range(at: 1))
            let output = parseTokenCount(ns, match.range(at: 2))
            if let input, let output, validTokens(input), validTokens(output) {
                let model = extractModel(from: line)
                let cost = extractCost(from: line)
                return TokenUsage(
                    promptTokens: input,
                    completionTokens: output,
                    model: model,
                    estimatedCostUSD: cost
                )
            }
        }

        // 3. Try Claude JSON format (input_tokens first)
        if let regex = claudeJSONRegex,
           let match = regex.firstMatch(in: line, range: fullRange),
           match.numberOfRanges >= 3 {
            let input  = parseTokenCount(ns, match.range(at: 1))
            let output = parseTokenCount(ns, match.range(at: 2))
            if let input, let output, validTokens(input), validTokens(output) {
                let model = extractModel(from: line)
                let cost = extractCost(from: line) ??
                    ModelPricingTable.estimateCost(promptTokens: input, completionTokens: output, model: model)
                return TokenUsage(
                    promptTokens: input,
                    completionTokens: output,
                    model: model,
                    estimatedCostUSD: cost
                )
            }
        }

        // 4. Try Claude JSON format (output_tokens first)
        if let regex = claudeJSONReverseRegex,
           let match = regex.firstMatch(in: line, range: fullRange),
           match.numberOfRanges >= 3 {
            let output = parseTokenCount(ns, match.range(at: 1))
            let input  = parseTokenCount(ns, match.range(at: 2))
            if let input, let output, validTokens(input), validTokens(output) {
                let model = extractModel(from: line)
                let cost = extractCost(from: line) ??
                    ModelPricingTable.estimateCost(promptTokens: input, completionTokens: output, model: model)
                return TokenUsage(
                    promptTokens: input,
                    completionTokens: output,
                    model: model,
                    estimatedCostUSD: cost
                )
            }
        }

        // 5. Try opencode format
        if let regex = opencodeRegex,
           let match = regex.firstMatch(in: line, range: fullRange),
           match.numberOfRanges >= 3 {
            let input  = parseTokenCount(ns, match.range(at: 2))
            let output = parseTokenCount(ns, match.range(at: 3))
            if let input, let output, validTokens(input), validTokens(output) {
                let model = extractModel(from: line)
                let cost = extractCost(from: line) ??
                    ModelPricingTable.estimateCost(promptTokens: input, completionTokens: output, model: model)
                return TokenUsage(
                    promptTokens: input,
                    completionTokens: output,
                    model: model,
                    estimatedCostUSD: cost
                )
            }
        }

        // 6. Try opencode alt format
        if let regex = opencodeAltRegex,
           let match = regex.firstMatch(in: line, range: fullRange),
           match.numberOfRanges >= 3 {
            let input  = parseTokenCount(ns, match.range(at: 1))
            let output = parseTokenCount(ns, match.range(at: 2))
            if let input, let output, validTokens(input), validTokens(output) {
                let model = extractModel(from: line)
                let cost = extractCost(from: line) ??
                    ModelPricingTable.estimateCost(promptTokens: input, completionTokens: output, model: model)
                return TokenUsage(
                    promptTokens: input,
                    completionTokens: output,
                    model: model,
                    estimatedCostUSD: cost
                )
            }
        }

        return nil
    }

    // MARK: - Private Helpers

    /// Parse a comma-formatted token count from an NSRange capture group.
    private static func parseTokenCount(_ ns: NSString, _ range: NSRange) -> Int? {
        guard range.location != NSNotFound else { return nil }
        let raw = ns.substring(with: range).replacingOccurrences(of: ",", with: "")
        return Int(raw)
    }

    /// Validate token count is within the guard cap.
    private static func validTokens(_ n: Int) -> Bool {
        n >= 0 && n <= maxTokens
    }

    /// Extract a USD cost figure from the line (e.g. `$0.0234`).
    /// Returns `nil` if not found or value exceeds cap.
    private static func extractCost(from line: String) -> Double? {
        let ns = line as NSString
        let fullRange = NSRange(location: 0, length: ns.length)
        guard let regex = claudeCostRegex,
              let match = regex.firstMatch(in: line, range: fullRange),
              match.numberOfRanges >= 2,
              match.range(at: 1).location != NSNotFound else { return nil }
        let raw = ns.substring(with: match.range(at: 1))
        guard let value = Double(raw), value >= 0.0, value <= maxCostUSD else { return nil }
        return value
    }

    /// Extract model name from the line if present.
    private static func extractModel(from line: String) -> String? {
        let ns = line as NSString
        let fullRange = NSRange(location: 0, length: ns.length)
        guard let regex = modelRegex,
              let match = regex.firstMatch(in: line, range: fullRange),
              match.numberOfRanges >= 2,
              match.range(at: 1).location != NSNotFound else { return nil }
        let name = ns.substring(with: match.range(at: 1))
        return name.isEmpty ? nil : name
    }
}
