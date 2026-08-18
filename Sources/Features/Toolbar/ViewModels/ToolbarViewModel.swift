// MARK: - ToolbarViewModel
// Presentation logic for the ToolbarView.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog
import os

/// Manages state and business logic for the run-configuration toolbar.
///
/// Per-project state:
/// - selected assistant (defaults to `.claude`)
/// - running flag (disables picker, switches play to stop)
/// - agent session tracking (dedicated PTY per agent launch)
@Observable
@MainActor
final class ToolbarViewModel {

    // MARK: - Constants

    /// Delay between sending Ctrl+C and the follow-up command for agents whose
    /// exit sequence is `.ctrlCThenCommand`, giving the shell time to interrupt
    /// the running process before the next command is written to the PTY.
    private static let ctrlCCommandDelayMilliseconds = 300

    // MARK: - Per-project State

    private var runningAssistants: [UUID: Bool] = [:]
    private var selectedAssistants: [UUID: AIAssistant] = [:]
    /// Maps projectId -> sessionId of the agent's dedicated PTY.
    private var agentSessionIds: [UUID: UUID] = [:]

    // MARK: - Background Tasks

    /// P1-3: Background observation/throttle tasks protected by a lock so
    /// `deinit` (nonisolated) can cancel safely without racing `@MainActor` writes.
    private struct BackgroundTasks: @unchecked Sendable {
        var sessionEvent: Task<Void, Never>?
        var projectCleanup: Task<Void, Never>?
        var costUpdate: Task<Void, Never>?
        var costObservation: Task<Void, Never>?
    }
    private let taskLock = OSAllocatedUnfairLock(initialState: BackgroundTasks())

    nonisolated private var sessionEventTask: Task<Void, Never>? {
        get { taskLock.withLock { $0.sessionEvent } }
        set { taskLock.withLock { $0.sessionEvent = newValue } }
    }
    nonisolated private var projectCleanupTask: Task<Void, Never>? {
        get { taskLock.withLock { $0.projectCleanup } }
        set { taskLock.withLock { $0.projectCleanup = newValue } }
    }
    /// Throttle task for cost badge updates (max 1 update / 500ms).
    nonisolated private var costUpdateTask: Task<Void, Never>? {
        get { taskLock.withLock { $0.costUpdate } }
        set { taskLock.withLock { $0.costUpdate = newValue } }
    }
    /// Observation task that watches `CostTrackerService.sessionCosts` changes.
    nonisolated private var costObservationTask: Task<Void, Never>? {
        get { taskLock.withLock { $0.costObservation } }
        set { taskLock.withLock { $0.costObservation = newValue } }
    }

    // MARK: - Dependencies

    private let projectManager: any ProjectManaging
    private let terminalManager: any TerminalSessionManaging
    let agentAvailability: any AgentAvailabilityChecking
    private let apiKeyResolver: any APIKeyResolving
    private weak var costTrackerService: CostTrackerService?

    // MARK: - Cost Badge State

    /// Throttle window for cost badge updates (milliseconds).
    private static let costUpdateThrottleMs = 500

    /// Currently displayed cost snapshot for the active agent session.
    /// Updated at most every `costUpdateThrottleMs` ms to avoid toolbar flicker.
    private(set) var currentCostSnapshot: SessionCostSnapshot?

    // MARK: - Init

    init(
        projectManager: any ProjectManaging,
        terminalManager: any TerminalSessionManaging,
        agentAvailability: any AgentAvailabilityChecking,
        apiKeyResolver: any APIKeyResolving = KeychainAPIKeyResolver(),
        costTrackerService: CostTrackerService? = nil
    ) {
        self.projectManager = projectManager
        self.terminalManager = terminalManager
        self.agentAvailability = agentAvailability
        self.apiKeyResolver = apiKeyResolver
        self.costTrackerService = costTrackerService
        startProjectCleanupObservation()
        startSessionEventObservation()
        if costTrackerService != nil {
            startCostObservation()
        }
    }

    deinit {
        sessionEventTask?.cancel()
        projectCleanupTask?.cancel()
        costUpdateTask?.cancel()
        costObservationTask?.cancel()
    }

    // MARK: - Cleanup

    /// Release all per-project cached state for a removed project.
    func cleanupProject(_ projectId: UUID) {
        runningAssistants.removeValue(forKey: projectId)
        selectedAssistants.removeValue(forKey: projectId)
        agentSessionIds.removeValue(forKey: projectId)
    }

    /// Subscribe to terminal session events and clear the running state
    /// when an agent's dedicated PTY process exits (e.g. the user exits the agent
    /// or it crashes). Without this the stop button stays red forever.
    private func startSessionEventObservation() {
        sessionEventTask = Task { @MainActor [weak self] in
            guard let self else { return }
            for await event in self.terminalManager.sessionEvents {
                if case .processExited(let sessionId, let projectId, let exitCode) = event {
                    if self.agentSessionIds[projectId] == sessionId {
                        Logger.ui.info("ToolbarViewModel: agent session \(sessionId) exited with code \(exitCode), clearing running state")
                        self.runningAssistants[projectId] = false
                        self.agentSessionIds.removeValue(forKey: projectId)
                        // Keep cost snapshot visible for a few seconds after exit
                        // (E8 — user can still read the final cost).
                        // CostTrackerService resets on processExited; we keep the
                        // last displayed snapshot for UI purposes.
                        // After 10s clear it.
                        Task { @MainActor [weak self] in
                            try? await Task.sleep(for: .seconds(10))
                            guard let self else { return }
                            if self.agentSessionIds[projectId] == nil {
                                self.currentCostSnapshot = nil
                            }
                        }
                    }
                }
            }
        }
    }

    /// Watch `CostTrackerService.sessionCosts` for changes and schedule badge refresh.
    /// Uses `AsyncObservation.stream` so updates are fully reactive without polling.
    private func startCostObservation() {
        guard let tracker = costTrackerService else { return }
        costObservationTask = Task { @MainActor [weak self, weak tracker] in
            guard let self, let tracker else { return }
            let stream = AsyncObservation.stream(emitInitial: false) { [weak tracker] () -> Int? in
                guard let tracker else { return nil }
                // Touch the observed property inside the tracking closure.
                return tracker.sessionCosts.values.reduce(0) { $0 + $1.totalTokens }
            }
            for await _ in stream {
                guard !Task.isCancelled else { return }
                self.scheduleCostUpdate()
            }
        }
    }

    /// Observe the projects list and auto-cleanup entries for removed projects.
    private func startProjectCleanupObservation() {
        projectCleanupTask = Task { @MainActor [weak self] in
            guard let self else { return }
            var knownIds = Set(self.projectManager.projects.map(\.id))

            // `projectManager` is an `any ProjectManaging` existential so we
            // use the closure-based `AsyncObservation.stream` overload. The
            // closure touches `projects` inside `withObservationTracking`
            // so the tracker re-arms whenever the list mutates.
            let stream = AsyncObservation.stream(emitInitial: false) { [weak self] () -> Set<UUID>? in
                guard let self else { return nil }
                return Set(self.projectManager.projects.map(\.id))
            }
            for await currentIds in stream {
                guard !Task.isCancelled else { return }
                for removed in knownIds.subtracting(currentIds) {
                    self.cleanupProject(removed)
                }
                knownIds = currentIds
            }
        }
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

    /// Agent session cost snapshot for the active project, throttled to 500ms updates.
    ///
    /// Returns `nil` when:
    /// - No active project
    /// - No agent session running
    /// - No cost data yet parsed
    var activeAgentCostSnapshot: SessionCostSnapshot? {
        currentCostSnapshot
    }

    /// Schedule a throttled cost badge refresh.
    /// Only one refresh task runs at a time (costUpdateTask).
    func scheduleCostUpdate() {
        guard costUpdateTask == nil else { return }
        costUpdateTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(Self.costUpdateThrottleMs))
            guard let self, !Task.isCancelled else { return }
            self.costUpdateTask = nil
            self.refreshCostSnapshot()
        }
    }

    /// Immediately refresh the cost snapshot from the tracker.
    func refreshCostSnapshot() {
        guard let projectId = activeId,
              let sessionId = agentSessionIds[projectId],
              let tracker = costTrackerService else {
            currentCostSnapshot = nil
            return
        }
        currentCostSnapshot = tracker.snapshot(for: sessionId)
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
            let name = agent.displayName
            Logger.ui.warning(
                "ToolbarViewModel.startAssistant: agent \(name, privacy: .public) cannot be launched (not installed or missing API key)"
            )
            return
        }

        // Resolve working directory from active project.
        // For free tabs the ID doesn't exist in projectManager.projects, so fall
        // back to the home directory — the terminal session already runs there.
        let workingDirectory = projectManager.projects
            .first(where: { $0.id == id })?.path.path
            ?? NSHomeDirectory()

        // Resolve API key: Keychain first, then environment fallback.
        let apiKeyValue: String? = {
            guard let envVar = agent.apiKeyEnvironmentVariable else { return nil }
            if let keychainValue = apiKeyResolver.resolve(for: envVar), !keychainValue.isEmpty {
                return keychainValue
            }
            return ProcessInfo.processInfo.environment[envVar]
        }()

        Logger.ui.debug("ToolbarViewModel.startAssistant: launching \(agent.displayName, privacy: .public) for project \(id)")

        if agent.launchViaShellInput {
            // Send the launch command to the existing shell session.
            // This gives the agent the full login-shell environment (.zprofile
            // is already sourced), which is required for agents whose API keys
            // live in env vars set by the user's shell profile.
            guard let shellSession = terminalManager.sessions(for: id)
                    .first(where: { !$0.isAgentSession }) else {
                Logger.ui.warning("ToolbarViewModel.startAssistant: no shell session available for \(agent.displayName, privacy: .public)")
                return
            }
            terminalManager.sendInput(agent.launchCommand, to: shellSession.id)
            runningAssistants[id] = true
            // Track the shell session so stopAssistant can send the exit sequence.
            agentSessionIds[id] = shellSession.id
            Logger.ui.info("ToolbarViewModel.startAssistant: sent launch command to shell session \(shellSession.id)")
        } else {
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
                try? await Task.sleep(for: .milliseconds(Self.ctrlCCommandDelayMilliseconds))
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
