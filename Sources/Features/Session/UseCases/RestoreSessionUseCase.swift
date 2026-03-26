// MARK: - RestoreSessionUseCase
// Restores the application session from persisted snapshot.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Restores the previous application session from disk.
///
/// Extracts session-restore business logic from AppDelegate into a focused,
/// testable value type. Call ``execute()`` once during
/// `applicationDidFinishLaunching` before the first frame is rendered.
///
/// - Note: Must run on `@MainActor` because both `ProjectManaging` and
///   `TerminalSessionManaging` are `@MainActor`-isolated.
@MainActor
struct RestoreSessionUseCase {

    // MARK: - Dependencies

    private let projectManager: any ProjectManaging
    private let terminalManager: any TerminalSessionManaging
    private let sessionPersistence: any SessionPersisting

    // MARK: - Init

    /// Creates a use case wired to the provided service implementations.
    ///
    /// - Parameters:
    ///   - projectManager: Receives the restored active project selection.
    ///   - terminalManager: Receives recreated terminal sessions for each project.
    ///   - sessionPersistence: Source that deserialises the snapshot from disk.
    init(
        projectManager: any ProjectManaging,
        terminalManager: any TerminalSessionManaging,
        sessionPersistence: any SessionPersisting
    ) {
        self.projectManager = projectManager
        self.terminalManager = terminalManager
        self.sessionPersistence = sessionPersistence
    }

    // MARK: - Execute

    /// Restore the previously persisted application session.
    ///
    /// - Returns: `true` if a snapshot was found and applied; `false` when no
    ///   snapshot exists (first launch or after a reset). Errors during decode
    ///   or terminal creation are logged but do not propagate.
    @discardableResult
    func execute() async -> Bool {
        do {
            guard let snapshot = try await sessionPersistence.restore() else {
                return false
            }

            // Restore active project selection.
            if let activeId = snapshot.activeProjectId,
               projectManager.project(for: activeId) != nil {
                projectManager.activeProjectId = activeId
            }

            // Restore terminal sessions for each project.
            for projectSession in snapshot.projectSessions {
                guard let project = projectManager.project(for: projectSession.projectId) else {
                    continue
                }

                // Restore at most one terminal per project.
                // Multiple saved layouts can occur if the user had a split open —
                // restoring all of them silently creates duplicate windows on startup.
                for layout in projectSession.terminalLayouts.prefix(1) {
                    do {
                        try terminalManager.createSession(
                            for: project.id,
                            shell: project.shellPath,
                            workingDirectory: layout.workingDirectory ?? project.path
                        )
                    } catch {
                        Logger.terminal.error("RestoreSessionUseCase: failed to restore terminal session: \(error.localizedDescription, privacy: .public)")
                    }
                }
            }
            return true
        } catch {
            Logger.session.error("RestoreSessionUseCase: failed to restore session: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }
}
