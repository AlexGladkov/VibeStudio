// MARK: - SpecStatsLineParser
// Pure parser for `codespeak build` summary lines.
// macOS 14+, Swift 5.10

import Foundation

/// Stateless parser that extracts ``SpecStats`` from a single `codespeak build`
/// output line. Pure and side-effect free so it can be unit-tested in isolation.
///
/// Recognised patterns (case-insensitive):
/// - `"N passing, M failing"` → passing = N, total = N + M
/// - `"N passing"`            → passing = N, total = N
/// - `"N/M specs passing"`    → passing = N, total = M
///
/// Returns `nil` when the line matches no pattern, or when the derived total is
/// zero (e.g. `"0 passing"` with no failing count → total 0 → `nil`).
enum SpecStatsLineParser {

    /// Parse a single output line into ``SpecStats``, or `nil` if not a stats line.
    ///
    /// The `buildDate` is stamped with `Date()` at parse time, mirroring the
    /// original inline behaviour.
    static func parseStats(from line: String) -> SpecStats? {
        let lower = line.lowercased()

        var passing = 0
        var total = 0
        var parsed = false

        // Pattern: "N passing, M failing" / "N passing"
        if let passingMatch = lower.firstMatch(of: /(\d+)\s+passing/) {
            passing = Int(passingMatch.1) ?? 0
            total = passing
            parsed = true
        }
        if let failingMatch = lower.firstMatch(of: /(\d+)\s+failing/) {
            let failing = Int(failingMatch.1) ?? 0
            total = passing + failing
            parsed = true
        }

        // Pattern: "N/M specs passing"
        if let slashMatch = lower.firstMatch(of: /(\d+)\/(\d+)\s+specs?\s+passing/) {
            passing = Int(slashMatch.1) ?? 0
            total = Int(slashMatch.2) ?? 0
            parsed = true
        }

        guard parsed && total > 0 else { return nil }
        return SpecStats(passing: passing, total: total, buildDate: Date())
    }
}
