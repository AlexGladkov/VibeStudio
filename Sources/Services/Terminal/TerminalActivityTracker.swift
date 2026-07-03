// MARK: - TerminalActivityTracker
// Manages activity debouncing, idle timers, and shell prompt detection.
// Internal helper for TerminalService -- not exposed outside the Terminal module.
// macOS 14+, Swift 5.10

import Foundation
import OSLog
import SwiftTerm

/// Tracks terminal output activity, debounces rapid updates, and manages
/// per-project idle timers that transition the activity state machine
/// (`running` -> `waitingForInput` / `idle`).
///
/// This type does **not** own the observable `projectActivityStates`
/// dictionary. Instead, it receives a `stateChanged` callback that
/// writes through to `TerminalService`'s stored property so that
/// `@Observable` tracking propagates to SwiftUI.
@MainActor
final class TerminalActivityTracker {

    // MARK: - Types

    /// Callback invoked whenever the tracker wants to change a project's activity state.
    typealias StateChanged = @MainActor (UUID, TabActivityState) -> Void

    // MARK: - Constants

    /// Minimum interval between activity updates for the same session.
    private let debounceInterval: TimeInterval = 0.1

    /// How long to wait after the last output before checking shell state.
    ///
    /// Injectable (default `1.5`s = production behaviour) so tests can drive the
    /// idle-timer transition deterministically with a short timeout instead of
    /// sleeping for the full production interval.
    private let idleTimeout: TimeInterval

    // MARK: - State

    /// Tracks last activity time per session for debouncing.
    private var lastActivityTime: [UUID: Date] = [:]

    /// Per-project timers: fire after silence to transition `running` -> `waitingForInput`.
    private var idleTimers: [UUID: Task<Void, Never>] = [:]

    /// Callback to propagate activity state changes to `TerminalService`.
    ///
    /// `var` (not `let`) so that the owning service can rewire the
    /// callback after the tracker has been initialised — required by the
    /// eager-init pattern that replaces the previous `lazy var` with a
    /// `self`-capturing initialiser (ARCH-L3).
    var stateChanged: StateChanged

    // MARK: - Init

    /// - Parameters:
    ///   - idleTimeout: Seconds of silence before the idle timer checks shell
    ///     state. Defaults to `1.5` (production value); override in tests.
    ///   - stateChanged: Called whenever a project's activity state should change.
    ///     The owner (`TerminalService`) stores the value in its `@Observable` property.
    init(idleTimeout: TimeInterval = 1.5, stateChanged: @escaping StateChanged) {
        self.idleTimeout = idleTimeout
        self.stateChanged = stateChanged
    }

    deinit {
        idleTimers.values.forEach { $0.cancel() }
    }

    // MARK: - Public Interface

    /// Mark a project as seen by the user -- clears the yellow indicator.
    ///
    /// Cancels any pending idle timer and resets `waitingForInput` to `idle`.
    func markProjectSeen(_ projectId: UUID, currentState: TabActivityState?) {
        idleTimers[projectId]?.cancel()
        idleTimers.removeValue(forKey: projectId)
        if case .waitingForInput? = currentState {
            stateChanged(projectId, .idle)
        }
    }

    /// Process activity from a terminal session.
    ///
    /// Debounces rapid events, updates activity state to `.running`,
    /// and resets the idle timer. When the timer fires it checks the
    /// terminal buffer via `promptChecker` to decide between `.idle`
    /// and `.waitingForInput`.
    ///
    /// - Parameters:
    ///   - sessionId: The session that produced output.
    ///   - projectId: The owning project.
    ///   - currentState: The current activity state for this project.
    ///   - promptChecker: Returns `true` if the session shows a shell prompt.
    /// - Returns: `true` if the event was processed (not debounced).
    @discardableResult
    func handleActivity(
        sessionId: UUID,
        projectId: UUID,
        currentState: TabActivityState?,
        promptChecker: @escaping @MainActor (UUID) -> Bool
    ) -> Bool {
        let now = Date()
        let last = lastActivityTime[sessionId] ?? .distantPast
        guard now.timeIntervalSince(last) >= debounceInterval else { return false }
        lastActivityTime[sessionId] = now

        // Mark project as actively producing output.
        stateChanged(projectId, .running)

        // Cancel previous silence timer and start a new one.
        idleTimers[projectId]?.cancel()
        idleTimers[projectId] = Task { [weak self, idleTimeout, stateChanged] in
            try? await Task.sleep(for: .seconds(idleTimeout))
            guard !Task.isCancelled, let self else { return }
            // Re-read is not needed: if another handleActivity fired, it already
            // set .running and replaced this timer. So .running here is correct.
            if promptChecker(sessionId) {
                stateChanged(projectId, .idle)
            } else {
                stateChanged(projectId, .waitingForInput)
            }
        }

        return true
    }

    /// Handle process exit for a session.
    func handleProcessExit(projectId: UUID, exitCode: Int32) {
        stateChanged(projectId, exitCode != 0 ? .error : .idle)
    }

    /// Clean up activity tracking state for a session.
    func removeSession(_ sessionId: UUID) {
        lastActivityTime.removeValue(forKey: sessionId)
    }

    /// Clean up all activity tracking for a project (when all sessions removed).
    func removeProject(_ projectId: UUID) {
        idleTimers[projectId]?.cancel()
        idleTimers.removeValue(forKey: projectId)
        stateChanged(projectId, .idle)
    }

    // MARK: - Shell Prompt Detection

    /// Returns `true` if the terminal session is currently showing a shell prompt.
    ///
    /// Reads the last visible line from the terminal buffer, strips ANSI codes,
    /// and checks for common prompt endings: `$ `, `% `, `# `, etc.
    func isAtShellPrompt(
        sessionId: UUID,
        viewProvider: @MainActor (UUID) -> TaggedTerminalView?
    ) -> Bool {
        let raw = lastVisibleLine(sessionId: sessionId, viewProvider: viewProvider)
        let line = ANSIStripper.strip(raw).trimmingCharacters(in: .whitespaces)
        guard !line.isEmpty else { return false }
        let promptSuffixes = ["$ ", "% ", "# ", "\u{276F} ", "\u{279C} ", "\u{2192} ", "> "]
        return promptSuffixes.contains(where: { line.hasSuffix($0) })
            || line.hasSuffix("$") || line.hasSuffix("%") || line.hasSuffix("#")
    }

    // MARK: - Private: Buffer Reading

    /// Number of trailing characters of the active buffer inspected for the
    /// last non-empty line. The live prompt is always well within this window.
    private static let bufferTailCharacters = 1024

    /// Read the last non-empty line from a terminal session's active buffer.
    ///
    /// Idle-path only: invoked from the 1.5s silence timer, never on the hot
    /// output path. Reads the active buffer (scroll-position independent) and
    /// returns the last non-empty line. A viewport-relative read (`getLine`) was
    /// tried but depends on the user's manual scroll position — if they scroll
    /// up during the idle window, prompt detection breaks — so we read the
    /// buffer tail instead, matching the original behaviour.
    private func lastVisibleLine(
        sessionId: UUID,
        viewProvider: @MainActor (UUID) -> TaggedTerminalView?
    ) -> String {
        guard let view = viewProvider(sessionId),
              view.window != nil else { return "" }
        let terminal = view.getTerminal()
        let data = terminal.getBufferAsData(kind: .active, encoding: .utf8)
        guard let text = String(data: data, encoding: .utf8) else { return "" }
        return String(text.suffix(Self.bufferTailCharacters))
            .components(separatedBy: .newlines)
            .last { !$0.trimmingCharacters(in: .whitespaces).isEmpty } ?? ""
    }
}
