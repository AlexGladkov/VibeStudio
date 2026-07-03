// MARK: - FileDiffViewModel
// Presentation logic for the standalone file-diff window.
// macOS 14+, Swift 5.10

import Foundation
import Observation

/// Loads and prepares a single file's diff for ``FileDiffSheetView``.
///
/// Owns the domain logic that previously lived in the view: invoking the git
/// diff, summing the total byte size, truncating oversized diffs at
/// ``maxDiffSizeBytes``, and short-circuiting binary files by extension. The
/// view only renders the resulting observable state.
@Observable
@MainActor
final class FileDiffViewModel {

    // MARK: - Output State

    private(set) var hunks: [GitDiffHunk] = []
    private(set) var isLoading = true
    private(set) var sizeWarning: String?
    private(set) var errorMessage: String?

    // MARK: - Constants

    private static let maxDiffSizeBytes = 512 * 1024

    private static let binaryExtensions: Set<String> = [
        "png", "jpg", "jpeg", "gif", "ico", "icns", "pdf",
        "zip", "tar", "gz", "dmg", "exe", "dylib", "so", "a", "o",
        "class", "jar", "war", "ear", "bin", "dat"
    ]

    // MARK: - Dependencies

    private let gitService: any GitServicing

    // MARK: - Init

    init(gitService: any GitServicing) {
        self.gitService = gitService
    }

    // MARK: - Loading

    /// Load the diff for `file`, applying binary detection and size truncation.
    ///
    /// - Parameters:
    ///   - file: The file whose diff to load.
    ///   - staged: `true` for the staged (`--cached`) diff, `false` for unstaged.
    ///   - projectPath: Repository root; a `nil` value produces an error state.
    func load(file: GitFile, staged: Bool, projectPath: URL?) async {
        guard let path = projectPath else {
            errorMessage = "No active project"
            isLoading = false
            return
        }

        let ext = (file.path as NSString).pathExtension.lowercased()
        guard !Self.binaryExtensions.contains(ext) else {
            errorMessage = "Binary file — diff not available"
            isLoading = false
            return
        }

        do {
            let loaded = try await gitService.diff(file: file.path, staged: staged, at: path)

            let totalSize = loaded.reduce(0) { total, hunk in
                total + hunk.lines.reduce(0) { $0 + $1.content.utf8.count }
            }

            if totalSize > Self.maxDiffSizeBytes {
                sizeWarning = "Diff truncated — file too large (\(totalSize / 1024) KB)"
                var accumulated = 0
                hunks = Array(loaded.prefix(while: { hunk in
                    let size = hunk.lines.reduce(0) { $0 + $1.content.utf8.count }
                    guard accumulated + size <= Self.maxDiffSizeBytes else { return false }
                    accumulated += size
                    return true
                }))
            } else {
                hunks = loaded
            }
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}
