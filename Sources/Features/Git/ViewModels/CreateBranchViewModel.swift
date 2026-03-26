// MARK: - CreateBranchViewModel
// Presentation logic for the Create Branch sheet.
// macOS 14+, Swift 5.10

import Foundation
import Observation

/// Manages state and business logic for creating a new git branch.
@Observable
@MainActor
final class CreateBranchViewModel {

    // MARK: - Input State

    var branchName: String = ""

    // MARK: - Output State

    private(set) var isCreating = false
    private(set) var errorMessage: String?

    // MARK: - Dependencies

    private let gitService: any GitServicing
    private let project: Project
    private let fromBranch: String?

    // MARK: - Init

    init(gitService: any GitServicing, project: Project, fromBranch: String? = nil) {
        self.gitService = gitService
        self.project = project
        self.fromBranch = fromBranch
    }

    // MARK: - Actions

    /// Creates the branch. Returns true on success, false on failure.
    func create() async -> Bool {
        let name = branchName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return false }

        isCreating = true
        errorMessage = nil
        do {
            try await gitService.createBranch(name: name, from: fromBranch, at: project.path)
            isCreating = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isCreating = false
            return false
        }
    }

    // MARK: - Derived State

    /// Whether the form has enough data to attempt branch creation.
    var canCreate: Bool {
        !branchName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
