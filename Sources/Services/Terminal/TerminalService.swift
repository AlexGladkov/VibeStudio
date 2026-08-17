// MARK: - TerminalService
// PTY process lifecycle management with SwiftTerm.
// Thin facade delegating to TerminalSessionStore and TerminalActivityTracker.
// macOS 14+, Swift 5.10

import AppKit
import Observation
import OSLog
import SwiftTerm

/// Manages PTY terminal sessions and their SwiftTerm views.
///
/// Key design decisions:
/// - PTY processes are owned by this service, not by SwiftUI views.
/// - Views attach/detach without affecting the PTY lifecycle.
/// - When a view is dismantled (tab switch), only `detachView` is called.
/// - `killSession` is explicit and sends SIGTERM, then SIGKILL after 2 seconds.
/// - Maximum 8 sessions per project to prevent fork bombs.
///
/// Internal logic is delegated to:
/// - ``TerminalSessionStore`` -- view cache, session-project index.
/// - ``TerminalActivityTracker`` -- debouncing, idle timers, activity states.
/// - ``TerminalAppearanceManager`` -- fonts, colors, palette, env building.
///
/// The API surface is split across extension files for readability:
/// - ``TerminalService+Session`` -- attach/detach, kill, split, query, input.
/// - ``TerminalService+Callbacks`` -- appearance refresh, PTY callbacks, activity.
///
/// NOTE ON ACCESS LEVELS: several stored dependencies below are `internal`
/// (no explicit modifier) rather than `private` because the extension files
/// above live in separate source files and Swift's `private` is file-scoped.
/// They are intentionally NOT public -- the type's public/observable API is
/// unchanged.
@Observable
@MainActor
final class TerminalService: TerminalSessionManaging {

    // MARK: - Constants

    /// Maximum number of terminal sessions per project.
    let maxSessionsPerProject = 8

    // MARK: - Observable State

    private(set) var sessionsByProject: [UUID: [TerminalSession]] = [:]
    private(set) var projectActivityStates: [UUID: TabActivityState] = [:]

    // MARK: - Delegates

    let store = TerminalSessionStore()
    let appearance = TerminalAppearanceManager()
    /// ARCH-L3: initialised in `init` instead of `lazy var + _ = activityTracker`
    /// kludge. Required by `installCallbacks(...)` before any session exists,
    /// so eager init is correct.
    @ObservationIgnored let activityTracker: TerminalActivityTracker

    /// Cost tracker for agent sessions. Set by DI after construction
    /// (avoids circular init dependency between TerminalService and CostTrackerService).
    weak var costTrackerService: CostTrackerService?

    // MARK: - Private State

    /// Continuation for the session events stream.
    let eventContinuation: AsyncStream<TerminalSessionEvent>.Continuation

    /// The session events stream.
    let sessionEvents: AsyncStream<TerminalSessionEvent>

    /// Long-running task observing `ThemeService.selectedAppearance`.
    nonisolated(unsafe) private var themeObservationTask: Task<Void, Never>?

    /// Long-running task observing `GeneralPreferences.terminalFontSize`.
    nonisolated(unsafe) private var fontObservationTask: Task<Void, Never>?

    /// General preferences (font size).
    let generalPreferences: GeneralPreferences

    // MARK: - Init

    init(themeService: ThemeService, generalPreferences: GeneralPreferences) {
        self.generalPreferences = generalPreferences

        let (stream, continuation) = AsyncStream<TerminalSessionEvent>.makeStream()
        sessionEvents = stream
        eventContinuation = continuation

        // ARCH-L3: initialise activityTracker eagerly. We can't capture
        // `self` directly in the init param closure (use-before-init), so
        // we set a no-op callback first, then rewire it once `self` is
        // available.
        let tracker = TerminalActivityTracker(stateChanged: { _, _ in })
        self.activityTracker = tracker
        tracker.stateChanged = { [weak self] projectId, state in
            self?.projectActivityStates[projectId] = state
        }

        // Observe theme changes via @Observable directly -- no NotificationCenter.
        // `AsyncObservation.stream` bridges `withObservationTracking` to an
        // AsyncSequence, eliminating ~20 lines of boilerplate per observer.
        themeObservationTask = Task { @MainActor [weak self] in
            let stream = AsyncObservation.stream(
                of: themeService,
                keyPath: \.selectedAppearance,
                emitInitial: false
            )
            for await appearance in stream {
                guard !Task.isCancelled else { return }
                self?.refreshTerminalColors(for: appearance)
            }
        }

        // Observe font size changes via the same helper.
        fontObservationTask = Task { @MainActor [weak self] in
            let stream = AsyncObservation.stream(
                of: generalPreferences,
                keyPath: \.terminalFontSize,
                emitInitial: false
            )
            for await size in stream {
                guard !Task.isCancelled else { return }
                self?.refreshTerminalFont(size: size)
            }
        }
    }

    deinit {
        eventContinuation.finish()
        themeObservationTask?.cancel()
        fontObservationTask?.cancel()
    }

    // MARK: - TerminalSessionManaging: Lifecycle

    @discardableResult
    func createSession(
        for projectId: UUID,
        shell: String?,
        workingDirectory: URL?,
        size: TerminalSize
    ) throws -> TerminalSession {
        // Enforce session limit per project.
        let existing = sessionsByProject[projectId] ?? []
        guard existing.count < maxSessionsPerProject else {
            throw TerminalSessionError.sessionLimitReached(
                projectId: projectId,
                max: maxSessionsPerProject
            )
        }

        // Resolve and validate shell path.
        let shellPath = shell ?? ProcessInfo.processInfo.environment["SHELL"] ?? "/bin/zsh"
        guard TerminalAppearanceManager.isValidShell(shellPath) else {
            throw TerminalSessionError.shellNotFound(path: shellPath)
        }

        let sessionId = UUID()
        let workDir = workingDirectory?.path ?? NSHomeDirectory()

        // Create the SwiftTerm view with terminal emulation.
        let terminalView = TaggedTerminalView(
            sessionId: sessionId,
            projectId: projectId,
            frame: NSRect(x: 0, y: 0, width: 800, height: 600)
        )

        appearance.configure(terminalView, fontSize: generalPreferences.terminalFontSize)
        installCallbacks(on: terminalView, sessionId: sessionId, projectId: projectId)

        // Start the PTY process.
        // args must be empty for an interactive shell -- SwiftTerm prepends execName
        // as argv[0] automatically. Prefix execName with "-" so the shell treats
        // itself as a login shell (Unix convention: argv[0][0] == '-').
        let shellName = "-" + (shellPath as NSString).lastPathComponent
        let env = appearance.buildShellEnvironment()
        terminalView.startProcess(
            executable: shellPath,
            args: [],
            environment: env,
            execName: shellName,
            currentDirectory: workDir
        )

        // Create session model.
        let session = TerminalSession(
            id: sessionId,
            projectId: projectId,
            title: shellName,
            state: .running
        )

        // Store state.
        store.register(view: terminalView, sessionId: sessionId, projectId: projectId)
        sessionsByProject[projectId, default: []].append(session)

        return session
    }

    // MARK: - TerminalSessionManaging: Agent Launch

    @discardableResult
    func startAgentSession(
        agent: AIAssistant,
        for projectId: UUID,
        workingDirectory: String,
        apiKeyValue: String?
    ) -> TerminalSession? {
        // Remove any lingering exited agent sessions before launching a new one
        // to avoid HSplitView showing a dead panel alongside the new session.
        let exitedIds = (sessionsByProject[projectId] ?? [])
            .filter { s in s.isAgentSession && { if case .exited = s.state { return true }; return false }() }
            .map(\.id)
        exitedIds.forEach { removeSession($0) }

        // Enforce session limit per project.
        let existing = sessionsByProject[projectId] ?? []
        guard existing.count < maxSessionsPerProject else {
            Logger.terminal.warning("startAgentSession: session limit reached for project \(projectId)")
            return nil
        }

        // Resolve the agent binary from trusted directories.
        guard let resolvedPath = CLIAgentPathResolver.resolve(agent.executableName) else {
            Logger.terminal.error("startAgentSession: executable not found for \(agent.executableName, privacy: .public)")
            return nil
        }

        let sessionId = UUID()

        // Create the SwiftTerm view.
        let terminalView = TaggedTerminalView(
            sessionId: sessionId,
            projectId: projectId,
            frame: NSRect(x: 0, y: 0, width: 800, height: 600)
        )

        appearance.configure(terminalView, fontSize: generalPreferences.terminalFontSize)
        installCallbacks(on: terminalView, sessionId: sessionId, projectId: projectId)

        // Install cost-tracking callback for agent sessions.
        if let tracker = costTrackerService {
            installCostTrackingCallback(
                on: terminalView,
                sessionId: sessionId,
                projectId: projectId,
                costTracker: tracker
            )
        }

        // Build environment for the agent via the allowlist-based builder.
        // All agents use the same restricted environment -- agent-specific API
        // keys are injected explicitly from Keychain or settings.
        let agentEnv = AgentEnvironmentBuilder.build(for: agent, apiKeyValue: apiKeyValue)

        // Build arguments, appending --dangerously-skip-permissions for Claude when enabled.
        var args = agent.launchArguments
        if agent == .claude && generalPreferences.claudeSkipPermissions {
            args.append("--dangerously-skip-permissions")
        }

        // Start the agent process in a dedicated PTY.
        terminalView.startProcess(
            executable: resolvedPath,
            args: args,
            environment: agentEnv,
            execName: agent.executableName,
            currentDirectory: workingDirectory
        )

        // Create session model -- marked as agent session.
        let session = TerminalSession(
            id: sessionId,
            projectId: projectId,
            title: agent.displayName,
            state: .running,
            isAgentSession: true
        )

        // Store state.
        store.register(view: terminalView, sessionId: sessionId, projectId: projectId)
        sessionsByProject[projectId, default: []].append(session)

        Logger.terminal.info("startAgentSession: launched \(agent.displayName, privacy: .public) at \(resolvedPath, privacy: .public)")
        return session
    }

    // MARK: - Internal: Session State Mutation
    //
    // These two writers mutate the `private(set)` observable state, so they
    // MUST live in the primary declaration (Swift's `private(set)` setter is
    // file-scoped). They are `internal` so the extension files can call them.

    /// Remove a session from internal tracking.
    func removeSession(_ sessionId: UUID) {
        store.removeView(for: sessionId)
        activityTracker.removeSession(sessionId)

        guard let projectId = store.removeProjectIndex(for: sessionId) else { return }
        guard var sessions = sessionsByProject[projectId],
              let index = sessions.firstIndex(where: { $0.id == sessionId }) else {
            Logger.terminal.warning("removeSession: session \(sessionId) not found in sessionsByProject")
            return
        }
        sessions.remove(at: index)
        if sessions.isEmpty {
            sessionsByProject.removeValue(forKey: projectId)
            activityTracker.removeProject(projectId)
        } else {
            sessionsByProject[projectId] = sessions
        }
    }

    /// Update a session's state in the internal model.
    func updateSessionState(_ sessionId: UUID, _ mutate: (inout TerminalSession) -> Void) {
        guard let projectId = store.projectId(for: sessionId),
              var sessions = sessionsByProject[projectId],
              let index = sessions.firstIndex(where: { $0.id == sessionId }) else { return }
        // PERF (hot path): `handleActivity` calls this on EVERY terminal tick.
        // After the first `.running → .hasActivity` transition the mutate closure
        // is a no-op, so reassigning the observable array would wake every
        // subscriber (TerminalAreaView re-render + RemoteControlServer session
        // broadcast) dozens of times/sec for no state change. Only write back —
        // and thus fire the `@Observable` notification — when the session
        // actually changed.
        let old = sessions[index]
        mutate(&sessions[index])
        guard sessions[index] != old else { return }
        sessionsByProject[projectId] = sessions
    }
}
