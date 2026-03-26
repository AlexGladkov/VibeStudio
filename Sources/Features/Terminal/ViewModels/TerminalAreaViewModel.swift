// MARK: - TerminalAreaViewModel
// Presentation logic for the terminal area container.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// Manages terminal session creation logic for the terminal area.
@Observable
@MainActor
final class TerminalAreaViewModel {

    // MARK: - State

    private(set) var isCreatingTerminal = false

    // MARK: - Dependencies

    private let projectManager: any ProjectManaging
    private let terminalManager: any TerminalSessionManaging

    // MARK: - Init

    init(projectManager: any ProjectManaging, terminalManager: any TerminalSessionManaging) {
        self.projectManager = projectManager
        self.terminalManager = terminalManager
    }

    // MARK: - Actions

    func createTerminal(for projectId: UUID) {
        guard !isCreatingTerminal else { return }
        isCreatingTerminal = true
        defer { isCreatingTerminal = false }
        let workingDirectory = projectManager.project(for: projectId)?.path
        let shellPath = projectManager.project(for: projectId)?.shellPath
        do {
            try terminalManager.createSession(
                for: projectId,
                shell: shellPath,
                workingDirectory: workingDirectory
            )
        } catch {
            Logger.terminal.error("TerminalAreaViewModel: failed to create terminal: \(error.localizedDescription, privacy: .public)")
        }
    }
}
