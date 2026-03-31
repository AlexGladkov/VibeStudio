// MARK: - SaveSessionUseCase
// Captures and persists the current application session state.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Saves the current application state (active projects, terminal layouts) to disk.
///
/// Extracts session-saving business logic from AppDelegate into a focused,
/// testable value type. Call ``execute()`` at application termination or
/// during periodic auto-save.
@MainActor
struct SaveSessionUseCase {

    // MARK: - Dependencies

    private let projectManager: any ProjectManaging
    private let terminalManager: any TerminalSessionManaging
    private let sessionPersistence: any SessionPersisting

    // MARK: - Init

    /// Creates a use case wired to the provided service implementations.
    ///
    /// - Parameters:
    ///   - projectManager: Source of truth for the active project list.
    ///   - terminalManager: Provides per-project terminal session layouts.
    ///   - sessionPersistence: Sink that serialises the snapshot to disk.
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

    /// Capture current application state and write it to persistent storage.
    ///
    /// Errors from `SessionPersisting.save` are logged but not re-thrown so
    /// that callers (application termination handlers) are not blocked.
    func execute() async {
        let projectSessions = projectManager.projects.map { project in
            let sessions = terminalManager.sessions(for: project.id)
            let layouts = sessions.map { session in
                TerminalLayoutSnapshot(
                    sessionId: session.id,
                    title: session.title,
                    splitDirection: session.splitDirection,
                    workingDirectory: project.path
                )
            }
            return ProjectSessionSnapshot(
                projectId: project.id,
                terminalLayouts: layouts,
                scrollbackFile: nil,
                sidebarVisible: true,
                sidebarWidth: Double(DSLayout.sidebarDefaultWidth)
            )
        }

        // Resolve the active ID: only persist it when it belongs to a real project.
        // If a FreeTab was active at quit, its UUID is not a real project ID and
        // would not survive the next RestoreSessionUseCase lookup — leaving
        // activeProjectId unresolved on startup and causing ActivateFirstProjectUseCase
        // to create an extra session on top of the already-restored ones.
        let resolvedActiveId: UUID? = projectManager.activeProjectId.flatMap { id in
            projectManager.project(for: id) != nil ? id : nil
        }

        let snapshot = AppSessionSnapshot(
            version: sessionPersistence.currentSnapshotVersion,
            capturedAt: .now,
            activeProjectId: resolvedActiveId,
            projectSessions: projectSessions
        )

        do {
            try await sessionPersistence.save(snapshot: snapshot)
        } catch {
            Logger.session.error("SaveSessionUseCase: failed to save session: \(error.localizedDescription, privacy: .public)")
        }
    }
}
