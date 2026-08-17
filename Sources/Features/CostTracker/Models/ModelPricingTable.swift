// MARK: - ModelPricingTable
// Static table of Claude model prices for local cost estimation.
// macOS 14+, Swift 5.10

import Foundation

/// Static table of Claude API model prices for cost estimation.
///
/// Prices are per-million-token (input / output), sourced from
/// https://www.anthropic.com/api. The table is intentionally hardcoded
/// at build time and versioned with `lastUpdated`. An override mechanism
/// via UserDefaults lets ops update prices without a full app release.
///
/// **Accuracy disclaimer:** These prices may not reflect recent changes.
/// Always check https://anthropic.com/api for current pricing.
enum ModelPricingTable {

    /// Month the default table was last updated (YYYY-MM format).
    static let lastUpdated = "2026-08"

    /// Human-readable disclaimer shown in the cost badge's help text.
    static let disclaimer = "Estimated cost based on Claude API pricing from \(lastUpdated). Prices may be outdated. See anthropic.com/api for current rates."

    /// UserDefaults key for an operator-supplied JSON override (Codable).
    ///
    /// Value must be a JSON object: `{ "claude-opus-4": [15.0, 75.0], ... }`
    /// where the array is [inputPerMToken, outputPerMToken].
    static let overrideDefaultsKey = "vs_model_prices_override"

    // MARK: - Default Pricing Table

    /// Price pair (input per M tokens, output per M tokens) in USD.
    struct PricePair: Codable {
        let inputPerMToken: Double
        let outputPerMToken: Double
    }

    /// Default table — Claude API prices as of 2026-08.
    /// Prefix-match: shorter key matches longer model string.
    private static let defaultTable: [(prefix: String, prices: PricePair)] = [
        // Claude 4 family
        ("claude-opus-4",      PricePair(inputPerMToken: 15.0,   outputPerMToken: 75.0)),
        ("claude-sonnet-4",    PricePair(inputPerMToken: 3.0,    outputPerMToken: 15.0)),
        // Claude 3.7
        ("claude-sonnet-3-7",  PricePair(inputPerMToken: 3.0,    outputPerMToken: 15.0)),
        ("claude-sonnet-3.7",  PricePair(inputPerMToken: 3.0,    outputPerMToken: 15.0)),
        // Claude 3.5 family
        ("claude-haiku-3-5",   PricePair(inputPerMToken: 0.8,    outputPerMToken: 4.0)),
        ("claude-haiku-3.5",   PricePair(inputPerMToken: 0.8,    outputPerMToken: 4.0)),
        ("claude-sonnet-3-5",  PricePair(inputPerMToken: 3.0,    outputPerMToken: 15.0)),
        ("claude-sonnet-3.5",  PricePair(inputPerMToken: 3.0,    outputPerMToken: 15.0)),
        ("claude-opus-3-5",    PricePair(inputPerMToken: 15.0,   outputPerMToken: 75.0)),
        ("claude-opus-3.5",    PricePair(inputPerMToken: 15.0,   outputPerMToken: 75.0)),
        // Claude 3 family
        ("claude-haiku-3",     PricePair(inputPerMToken: 0.25,   outputPerMToken: 1.25)),
        ("claude-sonnet-3",    PricePair(inputPerMToken: 3.0,    outputPerMToken: 15.0)),
        ("claude-opus-3",      PricePair(inputPerMToken: 15.0,   outputPerMToken: 75.0)),
        // Generic fallback prefixes (must be after specific ones)
        ("claude-haiku",       PricePair(inputPerMToken: 0.8,    outputPerMToken: 4.0)),
        ("claude-sonnet",      PricePair(inputPerMToken: 3.0,    outputPerMToken: 15.0)),
        ("claude-opus",        PricePair(inputPerMToken: 15.0,   outputPerMToken: 75.0)),
        ("claude",             PricePair(inputPerMToken: 3.0,    outputPerMToken: 15.0)),
    ]

    // MARK: - Override Support

    /// Cached runtime override loaded from UserDefaults.
    ///
    /// Populated lazily on first lookup. Use `reloadOverride()` to refresh
    /// after a UserDefaults update.
    private static var _override: [(prefix: String, prices: PricePair)]?

    /// Load (or reload) the operator price override from UserDefaults.
    ///
    /// Expected JSON format:
    /// ```json
    /// {"claude-opus-4": [15.0, 75.0], "claude-sonnet-4": [3.0, 15.0]}
    /// ```
    static func reloadOverride() {
        guard let raw = UserDefaults.standard.data(forKey: overrideDefaultsKey) else {
            _override = nil
            return
        }
        struct OverridePayload: Codable {
            // model-prefix → [inputPerM, outputPerM]
            subscript(key: String) -> [Double]? { _vals[key] }
            private var _vals: [String: [Double]]
            init(from decoder: Decoder) throws {
                _vals = try [String: [Double]](from: decoder)
            }
            var keys: [String] { Array(_vals.keys) }
        }
        guard let payload = try? JSONDecoder().decode(OverridePayload.self, from: raw) else {
            _override = nil
            return
        }
        _override = payload.keys.compactMap { key -> (String, PricePair)? in
            guard let arr = payload[key], arr.count >= 2 else { return nil }
            return (key, PricePair(inputPerMToken: arr[0], outputPerMToken: arr[1]))
        }
        .sorted { $0.0.count > $1.0.count } // longest prefix first
    }

    // MARK: - Lookup

    /// Look up the price pair for a model string using prefix matching.
    ///
    /// The override table is checked first; then the default table.
    /// Returns `nil` when no prefix matches — cost will be shown as `$?`.
    ///
    /// - Parameter model: Model identifier string from CLI output
    ///   (e.g. `"claude-opus-4-5-20251001"`).
    static func priceFor(model: String) -> PricePair? {
        let lower = model.lowercased()

        // Check operator override first (fail-open on nil override)
        if let override = _override {
            for (prefix, prices) in override where lower.hasPrefix(prefix.lowercased()) {
                return prices
            }
        }

        // Fall back to default table
        for (prefix, prices) in defaultTable where lower.hasPrefix(prefix.lowercased()) {
            return prices
        }
        return nil
    }

    /// Compute estimated USD cost for a given usage and model.
    ///
    /// Returns `nil` when the model is not in the pricing table.
    static func estimateCost(promptTokens: Int, completionTokens: Int, model: String?) -> Double? {
        guard let model, let prices = priceFor(model: model) else { return nil }
        let inputCost  = Double(promptTokens)    / 1_000_000.0 * prices.inputPerMToken
        let outputCost = Double(completionTokens) / 1_000_000.0 * prices.outputPerMToken
        return inputCost + outputCost
    }
}
