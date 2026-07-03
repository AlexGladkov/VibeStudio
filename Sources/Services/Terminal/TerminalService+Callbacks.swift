// MARK: - TerminalService+Callbacks
// Terminal appearance refresh, PTY view callbacks, process signalling and
// activity / process-exit handling.
// Extracted from TerminalService.swift to keep the primary type body small.
// macOS 14+, Swift 5.10

import AppKit
import OSLog
import SwiftTerm

extension TerminalService {

    /// Maximum number of characters of agent exit output logged to Console.
    private static let maxExitOutputLogCharacters = 3000

    // MARK: - Terminal Appearance

    /// Re-apply theme colors to all currently live terminal views.
    func refreshTerminalColors(for appAppearance: AppAppearance? = nil) {
        appearance.refreshColors(for: store.allViews, appearance: appAppearance)
        Logger.terminal.info("TerminalService: refreshed colors for \(self.store.viewCount) views")
    }

    /// Re-apply font size to all currently live terminal views.
    func refreshTerminalFont(size: CGFloat) {
        appearance.refreshFont(for: store.allViews, size: size)
        Logger.terminal.info("TerminalService: refreshed font size to \(size) for \(self.store.viewCount) views")
    }

    // MARK: - Terminal Configuration

    /// Install activity and process-exit callbacks on a terminal view.
    func installCallbacks(
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

    // MARK: - Process Management

    /// Send a POSIX signal to the terminal process.
    func sendSignal(to view: TaggedTerminalView, signal sig: Int32) {
        guard let process = view.process else { return }
        let pid = process.shellPid
        if pid > 0 {
            kill(pid, sig)
        }
    }

    /// Look up the live PTY view for `sessionId` and invoke `perform` with
    /// its `LocalProcess` reference.
    ///
    /// ARCH-M8: replaces the previous copy-pasted guard pattern
    /// (`store.view` → `view.process` → log warning) in `sendInput` /
    /// `resize` / signal helpers. Returns silently when the view has been
    /// removed or its PTY failed to spawn.
    ///
    /// - Parameters:
    ///   - sessionId: Target session.
    ///   - label: Short description used in the warning log line.
    ///   - perform: Closure invoked with the live `LocalProcess`.
    func withProcess(
        _ sessionId: UUID,
        label: String,
        perform: (LocalProcess) -> Void
    ) {
        guard let view = store.view(for: sessionId) else {
            Logger.terminal.warning("\(label, privacy: .public): view not found for session \(sessionId)")
            return
        }
        guard let process = view.process else {
            Logger.terminal.warning("\(label, privacy: .public): process is nil for session \(sessionId)")
            return
        }
        perform(process)
    }

    // MARK: - Activity Handling

    /// Handle activity detection from a terminal view.
    func handleActivity(sessionId: UUID, projectId: UUID) {
        // Delegate debouncing, state machine, and idle timers to the tracker.
        activityTracker.handleActivity(
            sessionId: sessionId,
            projectId: projectId,
            currentState: projectActivityStates[projectId],
            promptChecker: { [weak self] sid in
                guard let self else { return false }
                let store = self.store
                return self.activityTracker.isAtShellPrompt(
                    sessionId: sid,
                    viewProvider: { store.view(for: $0) }
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
    func handleProcessExit(sessionId: UUID, projectId: UUID, exitCode: Int32) {
        let isAgent = sessionsByProject[projectId]?.first(where: { $0.id == sessionId })?.isAgentSession ?? false
        Logger.terminal.warning("handleProcessExit: session=\(sessionId) exitCode=\(exitCode) isAgent=\(isAgent, privacy: .public)")

        // Capture terminal buffer so we can see what the agent printed before exiting.
        if isAgent, let view = store.view(for: sessionId) {
            let data = view.getTerminal().getBufferAsData(kind: .active, encoding: .utf8)
            if let raw = String(data: data, encoding: .utf8) {
                // Strip ANSI escape codes for readability in Console.app.
                let clean = ANSIStripper.strip(raw)
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty {
                    let tail = clean.prefix(Self.maxExitOutputLogCharacters)
                    Logger.terminal.warning(
                        "Agent exit output (exitCode=\(exitCode)):\n\(tail, privacy: .private)"
                    )
                }
            }
        }

        // Nil callbacks immediately to stop further updates.
        if let view = store.view(for: sessionId) {
            view.onRangeChanged = nil
            view.onProcessExited = nil
            view.onLinesChanged = nil
            view.onRawData = nil
            view.onTitleChanged = nil
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
