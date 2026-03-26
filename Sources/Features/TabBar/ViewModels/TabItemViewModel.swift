// MARK: - TabItemViewModel
// Presentation logic for a single tab item.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// Manages close-project business logic for a tab item.
@Observable
@MainActor
final class TabItemViewModel {

    // MARK: - Dependencies

    private let projectManager: any ProjectManaging
    private let terminalManager: any TerminalSessionManaging

    // MARK: - Init

    init(projectManager: any ProjectManaging, terminalManager: any TerminalSessionManaging) {
        self.projectManager = projectManager
        self.terminalManager = terminalManager
    }

    // MARK: - Actions

    func closeProject(_ projectId: UUID) {
        terminalManager.killAllSessions(for: projectId)
        try? projectManager.removeProject(projectId)
    }
}
