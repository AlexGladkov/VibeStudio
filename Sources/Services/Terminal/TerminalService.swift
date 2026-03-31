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
@Observable
@MainActor
final class TerminalService: TerminalSessionManaging {

    // MARK: - Constants

    /// Maximum number of terminal sessions per project.
    private let maxSessionsPerProject = 8

    // MARK: - Observable State

    private(set) var sessionsByProject: [UUID: [TerminalSession]] = [:]
    private(set) var projectActivityStates: [UUID: TabActivityState] = [:]

    // MARK: - Delegates

    private let store = TerminalSessionStore()
    private let appearance = TerminalAppearanceManager()
    @ObservationIgnored private lazy var activityTracker = TerminalActivityTracker { [weak self] projectId, state in
        self?.projectActivityStates[projectId] = state
    }

    // MARK: - Private State

    /// Continuation for the session events stream.
    private let eventContinuation: AsyncStream<TerminalSessionEvent>.Continuation

    /// The session events stream.
    let sessionEvents: AsyncStream<TerminalSessionEvent>

    /// Long-running task observing `ThemeService.selectedAppearance`.
    nonisolated(unsafe) private var themeObservationTask: Task<Void, Never>?

    // MARK: - Init

    init(themeService: ThemeService) {
        let (stream, continuation) = AsyncStream<TerminalSessionEvent>.makeStream()
        sessionEvents = stream
        eventContinuation = continuation

        // Force lazy var initialization so the activity tracker is ready.
        _ = activityTracker

        // Observe theme changes via @Observable directly -- no NotificationCenter.
        themeObservationTask = Task { @MainActor [weak self, weak themeService] in
            guard let themeService else { return }
            while !Task.isCancelled {
                let holder = ContinuationHolder()
                await withTaskCancellationHandler {
                    await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                        holder.set(c)
                        withObservationTracking {
                            _ = themeService.selectedAppearance
                        } onChange: {
                            holder.resume()
                        }
                    }
                } onCancel: {
                    holder.resume()
                }
                guard !Task.isCancelled else { return }
                self?.refreshTerminalColors(for: themeService.selectedAppearance)
            }
        }
    }

    deinit {
        eventContinuation.finish()
        themeObservationTask?.cancel()
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

        appearance.configure(terminalView)
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

        appearance.configure(terminalView)
        installCallbacks(on: terminalView, sessionId: sessionId, projectId: projectId)

        // Build environment for the agent via the allowlist-based builder.
        // All agents use the same restricted environment -- agent-specific API
        // keys are injected explicitly from Keychain or settings.
        let agentEnv = AgentEnvironmentBuilder.build(for: agent, apiKeyValue: apiKeyValue)

        // Start the agent process in a dedicated PTY.
        terminalView.startProcess(
            executable: resolvedPath,
            args: agent.launchArguments,
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

    func attachView(to sessionId: UUID) throws -> NSView {
        guard let view = store.view(for: sessionId) else {
            throw TerminalSessionError.sessionNotFound(sessionId)
        }
        return view
    }

    func detachView(from sessionId: UUID) {
        // Intentionally does NOT kill the PTY process.
        // The view remains in the cache for re-attachment.
    }

    func resize(session sessionId: UUID, to size: TerminalSize) {
        // SwiftTerm handles TIOCSWINSZ automatically when the NSView resizes.
    }

    func killSession(_ sessionId: UUID, force: Bool) {
        guard let view = store.view(for: sessionId) else {
            // View was already released by handleProcessExit (natural exit path).
            removeSession(sessionId)
            return
        }

        if force {
            sendSignal(to: view, signal: SIGKILL)
            removeSession(sessionId)
        } else {
            sendSignal(to: view, signal: SIGTERM)
            view.onRangeChanged = nil
            view.onProcessExited = nil
            let pid = view.process?.shellPid ?? 0
            Task { @MainActor [weak self] in
                try? await Task.sleep(for: .seconds(2))
                guard let self else { return }
                if pid > 0 && kill(pid, 0) == 0 {
                    kill(pid, SIGKILL)
                }
                self.removeSession(sessionId)
            }
        }
    }

    func killAllSessions(for projectId: UUID) {
        let sessionIds = sessionsByProject[projectId]?.map(\.id) ?? []
        for id in sessionIds {
            killSession(id, force: true)
        }
    }

    // MARK: - TerminalSessionManaging: Split Panels

    @discardableResult
    func split(
        _ sessionId: UUID,
        direction: SplitDirection,
        size: TerminalSize
    ) throws -> TerminalSession {
        guard let existingView = store.view(for: sessionId) else {
            throw TerminalSessionError.sessionNotFound(sessionId)
        }

        let session = try createSession(
            for: existingView.projectId,
            shell: nil,
            workingDirectory: nil,
            size: size
        )

        updateSessionState(session.id) { s in
            s.splitDirection = direction
        }

        return session
    }

    // MARK: - TerminalSessionManaging: Query

    func session(for id: UUID) -> TerminalSession? {
        guard let projectId = store.projectId(for: id) else { return nil }
        return sessionsByProject[projectId]?.first(where: { $0.id == id })
    }

    func sessions(for projectId: UUID) -> [TerminalSession] {
        sessionsByProject[projectId] ?? []
    }

    // MARK: - TerminalSessionManaging: Scrollback

    func scrollbackContent(for sessionId: UUID) -> String? {
        guard let view = store.view(for: sessionId),
              view.window != nil else { return nil }
        let terminal = view.getTerminal()
        let data = terminal.getBufferAsData(kind: .active, encoding: .utf8)
        let result = String(data: data, encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (result?.isEmpty ?? true) ? nil : result
    }

    // MARK: - TerminalSessionManaging: Input

    func sendInput(_ text: String, to sessionId: UUID) {
        guard let view = store.view(for: sessionId) else {
            Logger.terminal.warning("sendInput: view not found for session \(sessionId)")
            return
        }
        guard let process = view.process else {
            Logger.terminal.warning("sendInput: process is nil for session \(sessionId)")
            return
        }
        Logger.terminal.debug("sendInput: running=\(process.running)")
        let bytes = [UInt8](text.utf8)
        process.send(data: bytes[...])
    }

    // MARK: - TerminalSessionManaging: Activity

    /// Mark a project as seen by the user -- clears the yellow indicator.
    func markProjectSeen(_ projectId: UUID) {
        activityTracker.markProjectSeen(projectId, currentState: projectActivityStates[projectId])
    }

    // MARK: - Terminal Appearance

    /// Re-apply theme colors to all currently live terminal views.
    func refreshTerminalColors(for appAppearance: AppAppearance? = nil) {
        appearance.refreshColors(for: store.allViews, appearance: appAppearance)
        Logger.terminal.info("TerminalService: refreshed colors for \(self.store.viewCount) views")
    }

    // MARK: - Private: Terminal Configuration

    /// Install activity and process-exit callbacks on a terminal view.
    private func installCallbacks(
        on terminalView: TaggedTerminalView,
        sessionId: UUID,
        projectId: UUID
    ) {
        // SwiftTerm calls rangeChanged from updateDisplay(), dispatched on
        // DispatchQueue.main. Using MainActor.assumeIsolated avoids flooding
        // the executor with thousands of enqueued tasks per second.
        terminalView.onRangeChanged = { [weak self] sid in
            MainActor.assumeIsolated {
                self?.handleActivity(sessionId: sid, projectId: projectId)
            }
        }

        terminalView.onProcessExited = { [weak self] sid, exitCode in
            MainActor.assumeIsolated {
                self?.handleProcessExit(sessionId: sid, projectId: projectId, exitCode: exitCode)
            }
        }
    }

    // MARK: - Private: Process Management

    /// Send a POSIX signal to the terminal process.
    private func sendSignal(to view: TaggedTerminalView, signal sig: Int32) {
        guard let process = view.process else { return }
        let pid = process.shellPid
        if pid > 0 {
            kill(pid, sig)
        }
    }

    /// Remove a session from internal tracking.
    private func removeSession(_ sessionId: UUID) {
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
    private func updateSessionState(_ sessionId: UUID, _ mutate: (inout TerminalSession) -> Void) {
        guard let projectId = store.projectId(for: sessionId),
              var sessions = sessionsByProject[projectId],
              let index = sessions.firstIndex(where: { $0.id == sessionId }) else { return }
        mutate(&sessions[index])
        sessionsByProject[projectId] = sessions
    }

    // MARK: - Private: Activity Handling

    /// Handle activity detection from a terminal view.
    private func handleActivity(sessionId: UUID, projectId: UUID) {
        // Emit activity event for backward compatibility.
        eventContinuation.yield(
            .activityDetected(sessionId: sessionId, projectId: projectId)
        )

        // Delegate debouncing, state machine, and idle timers to the tracker.
        activityTracker.handleActivity(
            sessionId: sessionId,
            projectId: projectId,
            currentState: projectActivityStates[projectId],
            promptChecker: { [weak self] sid in
                guard let self else { return false }
                return self.activityTracker.isAtShellPrompt(
                    sessionId: sid,
                    viewProvider: { self.store.view(for: $0) }
                )
            }
        )

        // Update session state to hasActivity if not already.
        updateSessionState(sessionId) { session in
            if case .running = session.state {
                session.state = .hasActivity
            }
        }
    }

    /// Handle process exit from a terminal view.
    private func handleProcessExit(sessionId: UUID, projectId: UUID, exitCode: Int32) {
        let isAgent = sessionsByProject[projectId]?.first(where: { $0.id == sessionId })?.isAgentSession ?? false
        Logger.terminal.warning("handleProcessExit: session=\(sessionId) exitCode=\(exitCode) isAgent=\(isAgent, privacy: .public)")

        // Capture terminal buffer so we can see what the agent printed before exiting.
        if isAgent, let view = store.view(for: sessionId) {
            let data = view.getTerminal().getBufferAsData(kind: .active, encoding: .utf8)
            if let raw = String(data: data, encoding: .utf8) {
                // Strip ANSI escape codes for readability in Console.app.
                let clean = raw.replacingOccurrences(
                    of: #"\x1B\[[0-9;]*[mGKHJABCDEFG]|\x1B\][^\x07]*\x07"#,
                    with: "", options: .regularExpression
                ).trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty {
                    Logger.terminal.warning("Agent exit output (exitCode=\(exitCode)):\n\(clean.prefix(3000), privacy: .private)")
                }
            }
        }

        // Nil callbacks immediately to stop further updates.
        if let view = store.view(for: sessionId) {
            view.onRangeChanged = nil
            view.onProcessExited = nil
        }
        activityTracker.removeSession(sessionId)

        // Emit event so ToolbarViewModel clears the running state.
        eventContinuation.yield(
            .processExited(sessionId: sessionId, projectId: projectId, exitCode: exitCode)
        )
        activityTracker.handleProcessExit(projectId: projectId, exitCode: exitCode)

        if isAgent {
            // Keep agent session visible for 10 s so the user can read any error
            // output (e.g. "$PATH does not contain…"). Mark state as exited so
            // TerminalAreaView keeps displaying it. Actual cleanup is deferred.
            updateSessionState(sessionId) { s in s.state = .exited(code: exitCode) }
            Task { @MainActor [weak self] in
                try? await Task.sleep(for: .seconds(10))
                self?.removeSession(sessionId)
            }
        } else {
            // Shell sessions are removed immediately on exit.
            removeSession(sessionId)
        }
    }
}
