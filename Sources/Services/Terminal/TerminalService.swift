// MARK: - TerminalService
// PTY process lifecycle management with SwiftTerm.
// PTY processes live in the service, not in views.
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
@Observable
@MainActor
final class TerminalService: TerminalSessionManaging {

    // MARK: - Constants

    /// Maximum number of terminal sessions per project.
    private let maxSessionsPerProject = 8

    // MARK: - Observable State

    private(set) var sessionsByProject: [UUID: [TerminalSession]] = [:]
    private(set) var projectActivityStates: [UUID: TabActivityState] = [:]

    // MARK: - Private State

    /// Cache of SwiftTerm views keyed by session ID.
    private var terminalViews: [UUID: TaggedTerminalView] = [:]

    /// O(1) reverse index: sessionId -> projectId.
    private var sessionProjectIndex: [UUID: UUID] = [:]

    /// Tracks last activity time per session for debouncing.
    private var lastActivityTime: [UUID: Date] = [:]
    private let activityDebounceInterval: TimeInterval = 0.1

    /// Per-project timers: fire after silence to transition running → waitingForInput.
    private var idleTimers: [UUID: Task<Void, Never>] = [:]
    private let idleTimeoutInterval: TimeInterval = 1.5

    /// Continuation for the session events stream.
    private let eventContinuation: AsyncStream<TerminalSessionEvent>.Continuation

    /// The session events stream.
    let sessionEvents: AsyncStream<TerminalSessionEvent>

    // MARK: - Init

    init() {
        let (stream, continuation) = AsyncStream<TerminalSessionEvent>.makeStream()
        sessionEvents = stream
        eventContinuation = continuation
    }

    deinit {
        eventContinuation.finish()
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
        guard Self.isValidShell(shellPath) else {
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

        // Configure terminal appearance.
        configureTerminalAppearance(terminalView)

        // Set up activity detection callback.
        terminalView.onRangeChanged = { [weak self] sid in
            Task { @MainActor in
                self?.handleActivity(sessionId: sid, projectId: projectId)
            }
        }

        // Set up process exit callback.
        terminalView.onProcessExited = { [weak self] sid, exitCode in
            Task { @MainActor in
                self?.handleProcessExit(sessionId: sid, projectId: projectId, exitCode: exitCode)
            }
        }

        // Start the PTY process.
        // SwiftTerm's LocalProcessTerminalView handles forkpty internally.
        // args must be empty for an interactive shell — SwiftTerm prepends execName
        // as argv[0] automatically. Passing [shellName] would set argv[1] = "zsh",
        // which zsh interprets as a script filename → "can't open input file: zsh".
        //
        // Prefix execName with "-" so the shell treats itself as a login shell
        // (Unix convention: argv[0][0] == '-' → login shell). This ensures
        // ~/.zprofile is sourced, which adds Homebrew, nvm, cargo etc. to PATH.
        // Without this, apps launched from Finder/DMG get launchd's minimal PATH
        // and cannot find tools like `claude`, `node`, `cargo`.
        let shellName = "-" + (shellPath as NSString).lastPathComponent
        let env = buildEnvironment()
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
        terminalViews[sessionId] = terminalView
        sessionsByProject[projectId, default: []].append(session)
        sessionProjectIndex[sessionId] = projectId

        return session
    }

    func attachView(to sessionId: UUID) throws -> NSView {
        guard let view = terminalViews[sessionId] else {
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
        // This method exists for explicit resize requests.
        // No manual ioctl needed.
    }

    func killSession(_ sessionId: UUID, force: Bool) {
        guard let view = terminalViews[sessionId] else { return }

        if force {
            sendSignal(to: view, signal: SIGKILL)
            removeSession(sessionId)
        } else {
            sendSignal(to: view, signal: SIGTERM)
            // Capture PID before removeSession clears view reference.
            let pid = view.process?.shellPid ?? 0
            Task { @MainActor [weak self] in
                try? await Task.sleep(for: .seconds(2))
                guard let self else { return }
                // Check if process still exists (using PID, not view reference).
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
        guard let existingView = terminalViews[sessionId] else {
            throw TerminalSessionError.sessionNotFound(sessionId)
        }

        // Create a new session in the same project.
        let session = try createSession(
            for: existingView.projectId,
            shell: nil,
            workingDirectory: nil,
            size: size
        )

        // Mark split direction on the new session.
        updateSessionState(session.id) { s in
            s.splitDirection = direction
        }

        return session
    }

    // MARK: - TerminalSessionManaging: Query

    func session(for id: UUID) -> TerminalSession? {
        guard let projectId = sessionProjectIndex[id] else { return nil }
        return sessionsByProject[projectId]?.first(where: { $0.id == id })
    }

    func sessions(for projectId: UUID) -> [TerminalSession] {
        sessionsByProject[projectId] ?? []
    }

    // MARK: - TerminalSessionManaging: Scrollback

    func scrollbackContent(for sessionId: UUID) -> String? {
        guard let view = terminalViews[sessionId] else { return nil }
        let terminal = view.getTerminal()
        // Extract full buffer content including scrollback history.
        // `getBufferAsData` iterates all lines in the active buffer
        // (scrollback + visible), not just the visible viewport.
        let data = terminal.getBufferAsData(kind: .active, encoding: .utf8)
        let result = String(data: data, encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (result?.isEmpty ?? true) ? nil : result
    }

    // MARK: - TerminalSessionManaging: Input

    func sendInput(_ text: String, to sessionId: UUID) {
        guard let view = terminalViews[sessionId] else {
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

    // MARK: - Private: Shell Validation

    /// Validate that the shell path is listed in /etc/shells.
    ///
    /// If /etc/shells cannot be read, falls back to allowing only /bin/zsh.
    ///
    /// - Parameter path: Absolute path to the shell binary.
    /// - Returns: Whether the shell is valid.
    static func isValidShell(_ path: String) -> Bool {
        guard let shellsFile = try? String(contentsOfFile: "/etc/shells", encoding: .utf8) else {
            return path == "/bin/zsh"
        }
        return shellsFile.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.hasPrefix("#") && !$0.isEmpty }
            .contains(path)
    }

    // MARK: - Private: Terminal Configuration

    /// Apply VibeStudio design tokens to a terminal view.
    private func configureTerminalAppearance(_ view: TaggedTerminalView) {
        let font = DSFont.terminalNSFont(size: 13)
        view.font = font
        view.nativeForegroundColor = DSTerminalColors.foreground
        view.nativeBackgroundColor = DSTerminalColors.background
        view.caretColor = DSTerminalColors.cursor
        view.selectedTextBackgroundColor = DSTerminalColors.selection

        // Enable rangeChanged delegate callbacks for activity detection.
        // SwiftTerm gates this behind a flag that defaults to false.
        // Without it, TaggedTerminalView.rangeChanged is never called.
        view.notifyUpdateChanges = true

        // Convert NSColor palette to SwiftTerm.Color for installColors.
        let swiftTermPalette = DSTerminalColors.palette.map { nsColor -> SwiftTerm.Color in
            let c = nsColor.usingColorSpace(.sRGB) ?? nsColor
            return SwiftTerm.Color(
                red: UInt16(c.redComponent * 65535),
                green: UInt16(c.greenComponent * 65535),
                blue: UInt16(c.blueComponent * 65535)
            )
        }
        view.installColors(swiftTermPalette)
    }

    /// Build environment variables for the shell subprocess.
    private func buildEnvironment() -> [String] {
        var env = ProcessInfo.processInfo.environment
        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["LANG"] = env["LANG"] ?? "en_US.UTF-8"

        // Strip Claude Code session vars so nested `claude` invocations
        // don't get rejected with "already inside a Claude Code session".
        // Prefix-based matching catches any future CLAUDE_* additions.
        let keysToStrip = env.keys.filter { key in
            key.hasPrefix("CLAUDE_") || key == "CLAUDECODE" ||
            key == "ANTHROPIC_API_KEY" || key == "ANTHROPIC_API_KEY_HELPER"
        }
        keysToStrip.forEach { env.removeValue(forKey: $0) }

        return env.map { "\($0.key)=\($0.value)" }
    }

    // MARK: - Private: Process Management

    /// Send a POSIX signal to the terminal process.
    private func sendSignal(to view: TaggedTerminalView, signal sig: Int32) {
        // SwiftTerm's LocalProcessTerminalView exposes the process
        // through the `process` property. shellPid is on LocalProcess.
        guard let process = view.process else { return }
        let pid = process.shellPid
        if pid > 0 {
            kill(pid, sig)
        }
    }

    /// Mark a project as seen by the user — clears the yellow indicator.
    func markProjectSeen(_ projectId: UUID) {
        idleTimers[projectId]?.cancel()
        idleTimers.removeValue(forKey: projectId)
        // Only clear if the state is "waiting" — don't interrupt running or error.
        if case .waitingForInput? = projectActivityStates[projectId] {
            projectActivityStates[projectId] = .idle
        }
    }

    /// Remove a session from internal tracking.
    private func removeSession(_ sessionId: UUID) {
        terminalViews.removeValue(forKey: sessionId)
        lastActivityTime.removeValue(forKey: sessionId)

        if let projectId = sessionProjectIndex.removeValue(forKey: sessionId) {
            if let index = sessionsByProject[projectId]?.firstIndex(where: { $0.id == sessionId }) {
                sessionsByProject[projectId]!.remove(at: index)
                if sessionsByProject[projectId]!.isEmpty {
                    sessionsByProject.removeValue(forKey: projectId)
                    idleTimers[projectId]?.cancel()
                    idleTimers.removeValue(forKey: projectId)
                    projectActivityStates[projectId] = .idle
                }
            }
        }
    }

    /// Update a session's state in the internal model.
    private func updateSessionState(_ sessionId: UUID, _ mutate: (inout TerminalSession) -> Void) {
        guard let projectId = sessionProjectIndex[sessionId],
              let index = sessionsByProject[projectId]?.firstIndex(where: { $0.id == sessionId }) else { return }
        mutate(&sessionsByProject[projectId]![index])
    }

    // MARK: - Private: Shell Prompt Detection

    /// Precompiled regex for stripping ANSI escape sequences from terminal output.
    private static let ansiRegex: NSRegularExpression? = try? NSRegularExpression(
        pattern: "\\x1B(?:\\[[0-9;]*[A-Za-z]|[()][0-9A-Z]|[^\\[\\(\\)])"
    )

    /// Strip ANSI escape codes from a string for reliable pattern matching.
    private func stripANSI(_ text: String) -> String {
        guard let regex = TerminalService.ansiRegex else { return text }
        let range = NSRange(text.startIndex..., in: text)
        return regex.stringByReplacingMatches(in: text, range: range, withTemplate: "")
    }

    /// Read the last non-empty visible line from a terminal session's buffer.
    private func lastVisibleLine(sessionId: UUID) -> String {
        guard let view = terminalViews[sessionId] else { return "" }
        let terminal = view.getTerminal()
        let data = terminal.getBufferAsData(kind: .active, encoding: .utf8)
        guard let text = String(data: data, encoding: .utf8) else { return "" }
        // Only scan the tail to avoid processing large scrollback buffers.
        let tail = String(text.suffix(1024))
        return tail
            .components(separatedBy: .newlines)
            .last(where: { !$0.trimmingCharacters(in: .whitespaces).isEmpty }) ?? ""
    }

    /// Returns true if the terminal session is currently showing a shell prompt,
    /// which means Claude Code has exited and the shell is awaiting a new command.
    ///
    /// Detects common prompt endings after stripping ANSI codes:
    /// `$ `, `% `, `# `, `❯ `, `➜ `, `→ `
    private func isAtShellPrompt(sessionId: UUID) -> Bool {
        let raw = lastVisibleLine(sessionId: sessionId)
        let line = stripANSI(raw).trimmingCharacters(in: .whitespaces)
        guard !line.isEmpty else { return false }
        let promptSuffixes = ["$ ", "% ", "# ", "❯ ", "➜ ", "→ ", "> "]
        return promptSuffixes.contains(where: { line.hasSuffix($0) })
            || line.hasSuffix("$") || line.hasSuffix("%") || line.hasSuffix("#")
    }

    /// Handle activity detection from a terminal view.
    private func handleActivity(sessionId: UUID, projectId: UUID) {
        let now = Date()
        let last = lastActivityTime[sessionId] ?? .distantPast
        guard now.timeIntervalSince(last) >= activityDebounceInterval else { return }
        lastActivityTime[sessionId] = now

        // Emit activity event for tab indicator (kept for backward compatibility).
        eventContinuation.yield(
            .activityDetected(sessionId: sessionId, projectId: projectId)
        )

        // Update observable project activity state (multicast to all views).
        projectActivityStates[projectId] = .running

        // Cancel previous silence timer and start a new one.
        // When output stops for idleTimeoutInterval, check terminal content:
        // - shell prompt visible → Claude is not running, go idle (gray)
        // - otherwise → Claude finished or is waiting for input (yellow blink)
        idleTimers[projectId]?.cancel()
        idleTimers[projectId] = Task { [weak self, sessionId] in
            try? await Task.sleep(for: .seconds(self?.idleTimeoutInterval ?? 1.5))
            guard !Task.isCancelled, let self else { return }
            guard case .running? = self.projectActivityStates[projectId] else { return }
            if self.isAtShellPrompt(sessionId: sessionId) {
                self.projectActivityStates[projectId] = .idle
            } else {
                self.projectActivityStates[projectId] = .waitingForInput
            }
        }

        // Update session state to hasActivity if not already.
        updateSessionState(sessionId) { session in
            if case .running = session.state {
                session.state = .hasActivity
            }
        }
    }

    /// Handle process exit from a terminal view.
    private func handleProcessExit(sessionId: UUID, projectId: UUID, exitCode: Int32) {
        // Emit event for backward compatibility.
        eventContinuation.yield(
            .processExited(sessionId: sessionId, projectId: projectId, exitCode: exitCode)
        )

        // Update observable project activity state (multicast to all views).
        projectActivityStates[projectId] = exitCode != 0 ? .error : .idle

        // Update session state.
        updateSessionState(sessionId) { session in
            session.state = .exited(code: exitCode)
        }
    }
}
