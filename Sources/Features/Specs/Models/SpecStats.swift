// MARK: - SpecStats
// Aggregate build stats for a project's spec suite.
// macOS 14+, Swift 5.10

import Foundation

/// Aggregate passing/total counts from the last `codespeak build` run.
struct SpecStats: Equatable {
    /// Number of specs that passed in the last build.
    var passing: Int
    /// Total number of specs evaluated.
    var total: Int
    /// When this build was completed.
    var buildDate: Date

    /// Convenience: true if all specs pass.
    var allPassing: Bool { total > 0 && passing == total }

    /// Number of failing specs.
    var failing: Int { total - passing }
}
