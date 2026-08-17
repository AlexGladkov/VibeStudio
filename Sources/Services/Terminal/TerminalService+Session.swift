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
        return view
    }

    func detachView(from sessionId: UUID) {
        // Intentionally does NOT kill the PTY process.
        // The view remains in the cache for re-attachment.
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
            view.onLinesChanged = nil
            view.onRawData = nil
            view.onParsedOutput = nil
            view.onTitleChanged = nil
            sendSignal(to: view, signal: SIGKILL)
            removeSession(sessionId)
        } else {
            sendSignal(to: view, signal: SIGTERM)
            view.onRangeChanged = nil
            view.onProcessExited = nil
            view.onLinesChanged = nil
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

    func scrollbackContent(for sessionId: UUID) -> String? {
        guard let view = store.view(for: sessionId),
              view.window != nil else { return nil }
        let terminal = view.getTerminal()
        let data = terminal.getBufferAsData(kind: .active, encoding: .utf8)
        let result = String(data: data, encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (result?.isEmpty ?? true) ? nil : result
    }

    /// Returns the TaggedTerminalView for a session (if it exists).
    /// Used by Remote Control to install `onLinesChanged` callback for streaming.
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

    // MARK: - Activity

    /// Mark a project as seen by the user -- clears the yellow indicator.
    func markProjectSeen(_ projectId: UUID) {
        activityTracker.markProjectSeen(projectId, currentState: projectActivityStates[projectId])
    }
}
