// MARK: - TerminalService+Session
// Session lifecycle (attach/detach, kill, split), query, scrollback and input.
// Extracted from TerminalService.swift to keep the primary type body small.
// macOS 14+, Swift 5.10

import AppKit
import OSLog
import SwiftTerm

extension TerminalService {

    // MARK: - Attach / Detach

    func attachView(to sessionId: UUID) throws -> NSView {
        guard let view = store.view(for: sessionId) else {
            throw TerminalSessionError.sessionNotFound(sessionId)
        }

        // Reinstall display callbacks that were cleared when the view left its
        // previous window (see `TaggedTerminalView.viewDidMoveToWindow`). This
        // covers the tab-switch re-attach path: the view was dismantled by the
        // previous `TerminalHostView`, callbacks were cleared, and now a new
        // `TerminalHostView.makeNSView` is creating a fresh container for it.
        //
        // `onProcessExited` is preserved across detach (not cleared by
        // `viewDidMoveToWindow`) so we only need to restore the display callbacks.
        // `onRawData` is intentionally NOT reinstalled here — `RemoteBridgeRegistry`
        // is responsible for wiring it via `registerBridge` when an active bridge
        // exists for this session.
        guard let projectId = store.projectId(for: sessionId) else {
            throw TerminalSessionError.sessionNotFound(sessionId)
        }
        installCallbacks(on: view, sessionId: sessionId, projectId: projectId)

        return view
    }

    func detachView(from sessionId: UUID) {
        // Intentionally does NOT kill the PTY process.
        // The view remains in the cache for re-attachment.
        //
        // Clear display-related callbacks while the view is not displayed. This
        // prevents stale callbacks from firing during a tab switch (when the view
        // is removed from the window but the PTY continues running). Callbacks
        // are reinstalled by `attachView` → `installCallbacks` when the session
        // is next attached to a window via `TerminalHostView.makeNSView`.
        //
        // `onProcessExited` is intentionally kept: process exit is a terminal-
        // lifecycle event that must be delivered even while the view is detached.
        // `TerminalService.handleProcessExit` will clear it once handled.
        //
        // `onRawData` is cleared: any active RemoteSessionBridge will reinstall
        // it via `RemoteBridgeRegistry.registerBridge` after the view is
        // re-attached to a window.
        //
        // NOTE: `TaggedTerminalView.viewDidMoveToWindow` also clears these
        // callbacks when the view leaves its window, which is the primary
        // teardown path. `detachView` is an explicit API for callers that
        // know the session is being unloaded (e.g. the session manager).
        guard let view = store.view(for: sessionId) else { return }
        view.onRangeChanged = nil
        view.onTitleChanged = nil
        view.onRawData = nil
        // onProcessExited is intentionally preserved — see above.
    }

    // MARK: - Resize

    func resize(session sessionId: UUID, to size: TerminalSize) {
        // ARCH-M8: PTY access funneled through `withProcess` — same guard
        // as `sendInput` / `sendSignal` to avoid the implicit-unwrap crash
        // documented in the helper.
        withProcess(sessionId, label: "resize") { process in
            let fd = process.childfd
            guard fd >= 0 else { return }
            var ws = winsize(
                ws_row: UInt16(clamping: size.rows),
                ws_col: UInt16(clamping: size.columns),
                ws_xpixel: 0,
                ws_ypixel: 0
            )
            _ = PseudoTerminalHelpers.setWinSize(masterPtyDescriptor: fd, windowSize: &ws)
        }
    }

    // MARK: - Kill

    func killSession(_ sessionId: UUID, force: Bool) {
        guard let view = store.view(for: sessionId) else {
            // View was already released by handleProcessExit (natural exit path).
            removeSession(sessionId)
            return
        }

        if force {
            view.onRangeChanged = nil
            view.onProcessExited = nil
            view.onRawData = nil
            view.onParsedOutput = nil
            view.onTitleChanged = nil
            sendSignal(to: view, signal: SIGKILL)
            removeSession(sessionId)
        } else {
            sendSignal(to: view, signal: SIGTERM)
            view.onRangeChanged = nil
            view.onProcessExited = nil
            view.onRawData = nil
            view.onParsedOutput = nil
            view.onTitleChanged = nil
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

    // MARK: - Split Panels

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

    // MARK: - Query

    func session(for id: UUID) -> TerminalSession? {
        guard let projectId = store.projectId(for: id) else { return nil }
        return sessionsByProject[projectId]?.first(where: { $0.id == id })
    }

    func sessions(for projectId: UUID) -> [TerminalSession] {
        sessionsByProject[projectId] ?? []
    }

    // MARK: - Scrollback

    /// Returns the TaggedTerminalView for a session (if it exists).
    /// Used by Remote Control (`RemoteBridgeRegistry`) to install the `onRawData`
    /// callback for raw PTY streaming to WebSocket clients.
    func terminalView(for sessionId: UUID) -> TaggedTerminalView? {
        store.view(for: sessionId)
    }

    /// Returns the full terminal buffer content WITHOUT requiring the view
    /// to be in a window hierarchy. Used by Remote Control to serve
    /// scrollback to WebSocket clients.
    func rawScrollbackContent(for sessionId: UUID) -> String? {
        guard let view = store.view(for: sessionId) else { return nil }
        let terminal = view.getTerminal()
        let data = terminal.getBufferAsData(kind: .active, encoding: .utf8)
        let result = String(data: data, encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (result?.isEmpty ?? true) ? nil : result
    }

    // MARK: - Input

    func sendInput(_ text: String, to sessionId: UUID) {
        withProcess(sessionId, label: "sendInput") { process in
            #if DEBUG
            // ARCH-M1: hot-path debug — keystroke-level. Keep only in
            // Debug builds to avoid leaking input timing in Console.app.
            Logger.terminal.debug("sendInput: running=\(process.running)")
            #endif
            let bytes = [UInt8](text.utf8)
            process.send(data: bytes[...])
        }
    }

    // MARK: - Clear Scrollback

    /// Clear the terminal scrollback buffer for a session.
    ///
    /// Resets the terminal emulator buffer via SwiftTerm's terminal `resetToInitialState()`.
    /// The PTY process is NOT killed — the session stays alive and new output continues.
    ///
    /// Unlike `scrollbackContent(for:)`, this does NOT gate on `view.window != nil`
    /// because clearing the buffer makes sense even when the view is off-screen:
    /// new PTY output will fill a clean buffer. If the view is absent entirely (E14),
    /// returns `false` so the caller can send a failure `control_ack`.
    ///
    /// - Parameter sessionId: Target session.
    /// - Returns: `true` if the clear succeeded; `false` if the session was not found.
    @discardableResult
    func clearScrollback(for sessionId: UUID) -> Bool {
        guard let view = store.view(for: sessionId) else {
            Logger.terminal.warning("clearScrollback: view not found for session \(sessionId)")
            return false
        }
        let terminal = view.getTerminal()
        terminal.resetToInitialState()
        Logger.terminal.info("clearScrollback: buffer reset for session \(sessionId)")
        return true
    }

    // MARK: - Rerun Agent Session

    /// Restart an agent session using the server-stored agent kind and working directory.
    ///
    /// Only applicable to sessions with `isAgentSession == true` and a non-nil `agentKind`.
    /// The current agent process is force-killed and a new session is started with the
    /// same parameters. The API key is resolved via `apiKeyResolver` (e.g. Keychain) —
    /// NEVER from a WS payload (SEC-C2 invariant).
    ///
    /// - Parameters:
    ///   - sessionId: The existing agent session to restart.
    ///   - apiKeyResolver: Called with the agent to produce the API key value (from Keychain).
    /// - Returns: The newly created `TerminalSession`, or `nil` on failure.
    ///
    /// Failure reasons (E9): session not found, not an agent session, no stored `agentKind`.
    @discardableResult
    func rerunAgentSession(
        _ sessionId: UUID,
        apiKeyResolver: @MainActor (AIAssistant) -> String?
    ) -> TerminalSession? {
        guard let projectId = store.projectId(for: sessionId),
              let existing = sessionsByProject[projectId]?.first(where: { $0.id == sessionId }),
              existing.isAgentSession,
              let agent = existing.agentKind else {
            Logger.terminal.warning("rerunAgentSession: not an agent session or agentKind missing: \(sessionId)")
            return nil
        }

        let workingDir = existing.workingDirectory ?? NSHomeDirectory()
        let apiKey = apiKeyResolver(agent)

        // Force-kill the existing session to free the PTY. Callbacks are
        // cleared inside killSession(force:true) — no double-clear needed.
        killSession(sessionId, force: true)

        // Relaunch the same agent in the same directory.
        return startAgentSession(
            agent: agent,
            for: projectId,
            workingDirectory: workingDir,
            apiKeyValue: apiKey
        )
    }

    // MARK: - Activity

    /// Mark a project as seen by the user -- clears the yellow indicator.
    func markProjectSeen(_ projectId: UUID) {
        activityTracker.markProjectSeen(projectId, currentState: projectActivityStates[projectId])
    }
}
