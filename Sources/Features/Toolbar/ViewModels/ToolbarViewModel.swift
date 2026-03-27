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
/// - running flag (disables picker, switches play to stop)
/// - agent session tracking (dedicated PTY per agent launch)
@Observable
@MainActor
final class ToolbarViewModel {

    // MARK: - Per-project State

    private var runningAssistants: [UUID: Bool] = [:]
    private var selectedAssistants: [UUID: AIAssistant] = [:]
    /// Maps projectId -> sessionId of the agent's dedicated PTY.
    private var agentSessionIds: [UUID: UUID] = [:]

    // MARK: - Dependencies

    private let projectManager: any ProjectManaging
    private let terminalManager: any TerminalSessionManaging
    let agentAvailability: any AgentAvailabilityChecking

    // MARK: - Init

    init(
        projectManager: any ProjectManaging,
        terminalManager: any TerminalSessionManaging,
        agentAvailability: any AgentAvailabilityChecking
    ) {
        self.projectManager = projectManager
        self.terminalManager = terminalManager
        self.agentAvailability = agentAvailability
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
        guard let id = activeId else {
            Logger.ui.debug("ToolbarViewModel.startAssistant: no active project")
            return
        }

        let agent = currentAssistant

        // Check if agent can be launched.
        guard agentAvailability.canLaunch(agent) else {
            Logger.ui.warning("ToolbarViewModel.startAssistant: agent \(agent.displayName, privacy: .public) cannot be launched (not installed or missing API key)")
            return
        }

        // Resolve working directory from active project.
        guard let project = projectManager.projects.first(where: { $0.id == id }) else {
            Logger.ui.debug("ToolbarViewModel.startAssistant: project not found for id \(id)")
            return
        }
        let workingDirectory = project.path.path

        // Resolve API key: Keychain first, then environment fallback.
        let apiKeyValue: String? = {
            guard let envVar = agent.apiKeyEnvironmentVariable else { return nil }
            if let keychainValue = KeychainHelper.load(account: envVar), !keychainValue.isEmpty {
                return keychainValue
            }
            return ProcessInfo.processInfo.environment[envVar]
        }()

        Logger.ui.debug("ToolbarViewModel.startAssistant: launching \(agent.displayName, privacy: .public) for project \(id)")

        // Launch agent in a dedicated PTY.
        if let session = terminalManager.startAgentSession(
            agent: agent,
            for: id,
            workingDirectory: workingDirectory,
            apiKeyValue: apiKeyValue
        ) {
            runningAssistants[id] = true
            agentSessionIds[id] = session.id
            Logger.ui.info("ToolbarViewModel.startAssistant: agent session \(session.id) created")
        } else {
            Logger.ui.error("ToolbarViewModel.startAssistant: failed to create agent session")
        }
    }

    func stopAssistant() {
        guard let id = activeId else { return }

        // Find the agent's dedicated session, or fall back to the first session.
        let sessionId: UUID? = agentSessionIds[id]
            ?? terminalManager.sessions(for: id).first?.id

        guard let targetSessionId = sessionId else { return }

        let agent = currentAssistant

        switch agent.exitSequence {
        case .ctrlC:
            terminalManager.sendInput("\u{03}", to: targetSessionId)
            runningAssistants[id] = false
            agentSessionIds.removeValue(forKey: id)

        case .ctrlCThenCommand(let command):
            terminalManager.sendInput("\u{03}", to: targetSessionId)
            Task { @MainActor [weak self] in
                try? await Task.sleep(for: .milliseconds(300))
                guard let self else { return }
                self.terminalManager.sendInput(command + "\n", to: targetSessionId)
                self.runningAssistants[id] = false
                self.agentSessionIds.removeValue(forKey: id)
            }
        }
    }

    // MARK: - Agent Availability

    /// Refresh the cached availability status for all agents.
    func refreshAgentAvailability() {
        agentAvailability.refreshAll()
    }

    /// Get the availability status for a specific agent.
    func statusForAssistant(_ assistant: AIAssistant) -> AgentAvailabilityStatus {
        agentAvailability.check(assistant)
    }
}
