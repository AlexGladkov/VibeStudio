// MARK: - ToolbarViewModel
// Presentation logic for the ToolbarView.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// Manages state and business logic for the run-configuration toolbar.
///
/// Per-project state:
/// - selected assistant (defaults to `.claude`)
/// - running flag (disables picker, switches ▶ → ■)
@Observable
@MainActor
final class ToolbarViewModel {

    // MARK: - Per-project State

    private var runningAssistants: [UUID: Bool] = [:]
    private var selectedAssistants: [UUID: AIAssistant] = [:]

    // MARK: - Dependencies

    private let projectManager: any ProjectManaging
    private let terminalManager: any TerminalSessionManaging

    // MARK: - Init

    init(projectManager: any ProjectManaging, terminalManager: any TerminalSessionManaging) {
        self.projectManager = projectManager
        self.terminalManager = terminalManager
    }

    // MARK: - Computed Properties

    var activeId: UUID? { projectManager.activeProjectId }

    var isRunning: Bool {
        guard let id = activeId else { return false }
        return runningAssistants[id] == true
    }

    var currentAssistant: AIAssistant {
        guard let id = activeId else { return .claude }
        return selectedAssistants[id] ?? .claude
    }

    var activeProductionURL: URL? {
        guard let id = activeId,
              let project = projectManager.projects.first(where: { $0.id == id }),
              let urlString = project.productionURL,
              !urlString.isEmpty,
              let url = URL(string: urlString) else { return nil }
        return url
    }

    // MARK: - Actions

    func selectAssistant(_ assistant: AIAssistant) {
        guard let id = activeId else { return }
        selectedAssistants[id] = assistant
    }

    func startAssistant() {
        let id = activeId
        let sessions = id.map { terminalManager.sessions(for: $0) } ?? []
        Logger.ui.debug("ToolbarViewModel.startAssistant: activeId=\(String(describing: id), privacy: .public), sessions=\(sessions.count)")
        guard let id, let session = sessions.first else {
            Logger.ui.debug("ToolbarViewModel.startAssistant: guard failed — no active project or no terminal sessions")
            return
        }
        Logger.ui.debug("ToolbarViewModel.startAssistant: launching \(self.currentAssistant.displayName, privacy: .public) in session \(session.id)")
        runningAssistants[id] = true
        terminalManager.sendInput(currentAssistant.launchCommand, to: session.id)
    }

    func stopAssistant() {
        guard let id = activeId,
              let session = terminalManager.sessions(for: id).first else { return }
        terminalManager.sendInput("\u{03}", to: session.id)
        Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(300))
            guard let self else { return }
            terminalManager.sendInput("/exit\n", to: session.id)
            runningAssistants[id] = false
        }
    }
}
