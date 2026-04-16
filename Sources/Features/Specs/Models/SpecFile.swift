// MARK: - SpecFile
// Domain model for a single CodeSpeak spec file.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - SpecStatus

/// Build status of a single spec file.
enum SpecStatus: Equatable {
    /// Status not yet determined (no build run).
    case unknown
    /// All assertions in this spec pass.
    case passing
    /// One or more assertions in this spec fail.
    case failing
}

// MARK: - SpecFile

/// A single `*.cs.md` spec file tracked by the SPECS sidebar section.
struct SpecFile: Identifiable, Hashable {

    let id: UUID
    let url: URL

    /// Human-readable file name without the `.cs.md` extension.
    var name: String

    /// Build status from the last `codespeak build` run.
    var status: SpecStatus

    /// Number of passing assertions (from last build output).
    var passCount: Int

    /// Total number of assertions (from last build output).
    var totalCount: Int

    init(url: URL) {
        self.id = UUID()
        self.url = url
        self.name = url.deletingPathExtension()
            .deletingPathExtension()
            .lastPathComponent
        self.status = .unknown
        self.passCount = 0
        self.totalCount = 0
    }
}
