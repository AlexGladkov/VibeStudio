// MARK: - VibeStudio AppDelegate
// Composition Root: creates all real service implementations
// and manages application lifecycle.
// macOS 14+, Swift 5.10

import AppKit
import OSLog
import SwiftUI

/// Application delegate serving as the Composition Root.
///
/// All service instances are created here and injected into the
/// SwiftUI environment via ``ServiceContainer``. No service
/// creates its own dependencies -- they receive them through init.
@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {

    // MARK: - Public Properties

    /// The dependency injection container holding all live service instances.
    /// Accessed by ``VibeStudioApp`` to inject into the SwiftUI environment.
    private(set) lazy var container: ServiceContainer = {
        ServiceContainer(
            projectManager: projectStore,
            terminalSessionManager: terminalService,
            gitService: gitService,
            fileSystemWatcher: fileSystemWatcher,
            sessionPersistence: sessionStore,
            aiCommitService: aiCommitService,
            gitStatusPoller: gitStatusPoller
        )
    }()

    // MARK: - Private Services

    private lazy var projectStore = ProjectStore()
    private lazy var terminalService = TerminalService()
    private lazy var gitService = GitService()
    private lazy var fileSystemWatcher = FileSystemWatcher()
    private lazy var sessionStore = SessionStore()
    private lazy var aiCommitService = AICommitService()
    private lazy var gitStatusPoller = GitStatusPoller(gitService: gitService)

    /// Long-running tasks for observation (cancelled on termination).
    private var activeProjectObservation: Task<Void, Never>?
    private var fileEventObservation: Task<Void, Never>?

    // MARK: - NSApplicationDelegate

    func applicationDidFinishLaunching(_ notification: Notification) {
        // Load persisted project list.
        do {
            try projectStore.load()
        } catch {
            Logger.session.error("Failed to load projects: \(error.localizedDescription, privacy: .public)")
        }

        // Restore previous session asynchronously.
        Task { @MainActor in
            await restoreSession()
        }

        // Start observing active project changes to drive git status polling.
        startActiveProjectObservation()

        // Forward file system watcher events to git status poller for immediate refresh.
        startFileEventForwarding()
    }

    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        Task { @MainActor in
            // 1. Save session FIRST before killing anything.
            await saveSession()

            // 2. Kill all PTY processes.
            for projectId in terminalService.sessionsByProject.keys {
                terminalService.killAllSessions(for: projectId)
            }

            // 3. Stop git status polling and observation tasks.
            self.gitStatusPoller.stopPolling()
            self.activeProjectObservation?.cancel()
            self.fileEventObservation?.cancel()

            // 4. Stop all file watchers.
            fileSystemWatcher.unwatchAll()

            // 5. Allow termination.
            NSApp.reply(toApplicationShouldTerminate: true)
        }
        return .terminateLater
    }

    func applicationWillTerminate(_ notification: Notification) {
        // Cleanup is handled in applicationShouldTerminate(_:).
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        true
    }

    // MARK: - Session Management

    /// Restore the application session from persisted snapshot.
    private func restoreSession() async {
        do {
            guard let snapshot = try await sessionStore.restore() else {
                // No saved session — activate first project automatically.
                activateFirstProjectIfNeeded()
                return
            }

            // Restore active project selection.
            if let activeId = snapshot.activeProjectId,
               projectStore.project(for: activeId) != nil {
                projectStore.activeProjectId = activeId
            }

            // Restore terminal sessions for each project.
            for projectSession in snapshot.projectSessions {
                guard let project = projectStore.project(for: projectSession.projectId) else {
                    continue
                }

                for layout in projectSession.terminalLayouts {
                    do {
                        try terminalService.createSession(
                            for: project.id,
                            shell: project.shellPath,
                            workingDirectory: layout.workingDirectory ?? project.path
                        )
                    } catch {
                        Logger.terminal.error("Failed to restore terminal session: \(error.localizedDescription, privacy: .public)")
                    }
                }
            }
        } catch {
            Logger.session.error("Failed to restore session: \(error.localizedDescription, privacy: .public)")
        }

        // Fallback: if still no active project after restore, activate the first one.
        activateFirstProjectIfNeeded()
    }

    /// Activates the first available project and opens a terminal session for it.
    /// Called when no session was saved or the saved active project is missing.
    private func activateFirstProjectIfNeeded() {
        guard projectStore.activeProjectId == nil,
              let first = projectStore.projects.first else { return }
        projectStore.activeProjectId = first.id
        try? terminalService.createSession(
            for: first.id,
            shell: first.shellPath,
            workingDirectory: first.path
        )
    }

    /// Save current application state to disk.
    private func saveSession() async {
        let projectSessions = projectStore.projects.map { project in
            let sessions = terminalService.sessions(for: project.id)
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

        let snapshot = AppSessionSnapshot(
            version: sessionStore.currentSnapshotVersion,
            capturedAt: .now,
            activeProjectId: projectStore.activeProjectId,
            projectSessions: projectSessions
        )

        do {
            try await sessionStore.save(snapshot: snapshot)
        } catch {
            Logger.session.error("Failed to save session: \(error.localizedDescription, privacy: .public)")
        }
    }

    // MARK: - Git Status Polling

    /// Observe ``ProjectStore/activeProjectId`` and start/stop git status polling accordingly.
    ///
    /// Uses `withObservationTracking` bridged to `withUnsafeContinuation` so the
    /// task suspends with zero CPU usage between changes. `onChange` fires exactly
    /// once per property mutation and resumes the continuation, at which point the
    /// loop re-registers the next observation before suspending again.
    private func startActiveProjectObservation() {
        activeProjectObservation = Task { @MainActor [weak self] in
            guard let self else { return }
            var lastProjectId: UUID? = self.projectStore.activeProjectId
            // Drive initial polling state without waiting for a change event.
            self.updatePolling(for: lastProjectId)

            while !Task.isCancelled {
                // Suspend the task until activeProjectId is mutated.
                // withObservationTracking reads the property (registering the
                // observation), then onChange is called exactly once on the
                // next mutation and resumes the continuation. Zero polling.
                await withUnsafeContinuation { (continuation: UnsafeContinuation<Void, Never>) in
                    withObservationTracking {
                        _ = self.projectStore.activeProjectId
                    } onChange: {
                        continuation.resume()
                    }
                }

                guard !Task.isCancelled else { return }
                let newId = self.projectStore.activeProjectId
                guard newId != lastProjectId else { continue }
                lastProjectId = newId
                self.updatePolling(for: newId)
            }
        }
    }

    /// Start or stop the git status poller based on the active project.
    private func updatePolling(for activeProjectId: UUID?) {
        guard let activeId = activeProjectId,
              let project = projectStore.project(for: activeId) else {
            gitStatusPoller.stopPolling()
            Logger.git.debug("Git status polling stopped — no active project")
            return
        }

        gitStatusPoller.startPolling(for: project.path, isActive: true)
        Logger.git.info("Git status polling started for \(project.name, privacy: .public)")
    }

    /// Forward file system change events to the git status poller for immediate refresh.
    ///
    /// Iterates the ``FileSystemWatcher/events`` async stream and calls
    /// ``GitStatusPoller/refreshNow()`` on each event, ensuring the git panel
    /// updates within seconds of a file save rather than waiting for the next poll cycle.
    private func startFileEventForwarding() {
        fileEventObservation = Task { [weak self] in
            guard let self else { return }

            for await _ in self.fileSystemWatcher.events {
                guard !Task.isCancelled else { break }
                await MainActor.run {
                    self.gitStatusPoller.refreshNow()
                }
            }
        }
    }
}
