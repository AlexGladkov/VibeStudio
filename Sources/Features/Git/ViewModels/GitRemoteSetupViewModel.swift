// MARK: - GitRemoteSetupViewModel
// Presentation logic for the Git Remote Setup sheet.
// macOS 14+, Swift 5.10

import Foundation
import Observation

/// Manages state and business logic for adding a git remote.
@Observable
@MainActor
final class GitRemoteSetupViewModel {

    // MARK: - Constants

    private static let dismissDelayMilliseconds: UInt64 = 800

    // MARK: - Input State

    var remoteName: String = "origin"
    var remoteUrl: String = ""

    // MARK: - Output State

    private(set) var isAdding = false
    private(set) var errorMessage: String?
    private(set) var successMessage: String?

    // MARK: - Dependencies

    private let gitService: any GitServicing
    private let project: Project

    // MARK: - Init

    init(gitService: any GitServicing, project: Project) {
        self.gitService = gitService
        self.project = project
    }

    // MARK: - Actions

    /// Adds the remote. Returns true if the operation succeeded and the sheet should dismiss.
    func addRemote() async -> Bool {
        let url = remoteUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedName = remoteName.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = trimmedName.isEmpty ? "origin" : trimmedName
        guard !url.isEmpty else { return false }

        isAdding = true
        errorMessage = nil
        successMessage = nil

        do {
            try await gitService.addRemote(name: name, url: url, at: project.path)
            successMessage = "Remote '\(name)' added"
            try? await Task.sleep(nanoseconds: Self.dismissDelayMilliseconds * 1_000_000)
            isAdding = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isAdding = false
            return false
        }
    }

    // MARK: - Derived State

    /// Whether the form has enough data to attempt adding a remote.
    var canAdd: Bool {
        !remoteUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
