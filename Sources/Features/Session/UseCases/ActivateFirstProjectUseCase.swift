// MARK: - ActivateFirstProjectUseCase
// Activates the first available project when no session is saved.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Activates the first available project and opens a terminal session for it.
///
/// Called when no previous session exists (first launch) or as a fallback
/// when the restored snapshot did not produce an active project selection.
/// The use case is a no-op when `activeProjectId` is already set or when
/// the project list is empty.
///
/// - Note: Must run on `@MainActor` because both `ProjectManaging` and
///   `TerminalSessionManaging` are `@MainActor`-isolated.
@MainActor
struct ActivateFirstProjectUseCase {

    // MARK: - Dependencies

    private let projectManager: any ProjectManaging
    private let terminalManager: any TerminalSessionManaging

    // MARK: - Init

    /// Creates a use case wired to the provided service implementations.
    ///
    /// - Parameters:
    ///   - projectManager: Checked for an existing active selection and mutated to set one.
    ///   - terminalManager: Receives the initial terminal session for the activated project.
    init(projectManager: any ProjectManaging, terminalManager: any TerminalSessionManaging) {
        self.projectManager = projectManager
        self.terminalManager = terminalManager
    }

    // MARK: - Execute

    /// Activate the first project if no project is currently active.
    ///
    /// Terminal session creation errors are silently discarded — the project
    /// is still activated even when the PTY cannot be spawned immediately.
    func execute() {
        guard projectManager.activeProjectId == nil,
              let first = projectManager.projects.first else { return }
        projectManager.activeProjectId = first.id
        try? terminalManager.createSession(
            for: first.id,
            shell: first.shellPath,
            workingDirectory: first.path
        )
    }
}
