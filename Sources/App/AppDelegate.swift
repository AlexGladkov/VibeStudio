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
            terminalService: terminalService,
            gitService: gitService,
            fileSystemWatcher: fileSystemWatcher,
            sessionPersistence: sessionStore,
            aiCommitService: aiCommitService,
            gitStatusPoller: gitStatusPoller,
            appReadyState: appReadyState
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
    private let appReadyState = AppReadyState()

    // MARK: - Session UseCases

    private lazy var saveSessionUseCase = SaveSessionUseCase(
        projectManager: projectStore,
        terminalManager: terminalService,
        sessionPersistence: sessionStore
    )

    private lazy var restoreSessionUseCase = RestoreSessionUseCase(
        projectManager: projectStore,
        terminalManager: terminalService,
        sessionPersistence: sessionStore
    )

    private lazy var activateFirstProjectUseCase = ActivateFirstProjectUseCase(
        projectManager: projectStore,
        terminalManager: terminalService
    )

    /// Long-running tasks for observation (cancelled on termination).
    private var activeProjectObservation: Task<Void, Never>?
    private var fileEventObservation: Task<Void, Never>?

    // MARK: - NSApplicationDelegate

    func applicationWillFinishLaunching(_ notification: Notification) {
        // Intentionally empty.
        //
        // Previous approach: call contentsOfDirectory(~/Documents) here as a
        // TCC preflight. This was incorrect — FileManager calls are non-blocking
        // with respect to TCC: the call returns immediately with a permission error
        // while the dialog is shown asynchronously. Because applicationWillFinishLaunching
        // runs before the main runloop starts, macOS may not be able to present the
        // TCC dialog at all at this point, making the preflight entirely ineffective.
        //
        // The correct approach (below, in applicationDidFinishLaunching) is to
        // run the TCC trigger on a background thread and await its completion
        // before spawning any child processes (PTY shells, git subprocesses).
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        // Load persisted project list (reads ~/Library/Application Support — no TCC).
        do {
            try projectStore.load()
        } catch {
            Logger.session.error("Failed to load projects: \(error.localizedDescription, privacy: .public)")
        }

        // Obtain TCC consent for ~/Documents, then start all services that
        // spawn child processes in project directories.
        //
        // WHY THIS ORDER MATTERS:
        //   `FileManager.contentsOfDirectory` is non-blocking: it returns an
        //   error immediately and shows the TCC dialog asynchronously. If we
        //   start services first, each service (TerminalService, GitService,
        //   FileTreeBuilder) independently accesses ~/Documents before the user
        //   has responded — producing one TCC dialog per service (5+ dialogs).
        //
        // HOW THIS FIXES IT:
        //   Task.detached runs on a background thread. The background thread
        //   BLOCKS on the kernel-level TCC check until the user clicks Allow/
        //   Deny, while the main thread's run loop continues running so macOS
        //   can present the TCC dialog UI. After `await` returns, TCC is
        //   resolved for this process and all subsequently spawned child
        //   processes (forkpty, Process) inherit the grant — no more dialogs.
        Task { @MainActor [weak self] in
            await self?.acquireTCCConsentThenStart()
        }
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

    // MARK: - TCC Consent + Startup Sequencing

    /// Obtain TCC consent for ~/Documents, then start all services that touch it.
    ///
    /// Runs the filesystem access on a background thread so the background thread
    /// can block on the kernel TCC gate while the main-thread run loop stays live
    /// to present the consent dialog. Awaiting ensures services start only after
    /// the grant (or denial) is recorded — child processes then inherit the grant.
    private func acquireTCCConsentThenStart() async {
        await Task.detached(priority: .userInitiated) {
            let documentsURL = FileManager.default.urls(
                for: .documentDirectory, in: .userDomainMask
            ).first!
            _ = try? FileManager.default.contentsOfDirectory(
                at: documentsURL, includingPropertiesForKeys: nil
            )
        }.value

        // TCC is now resolved. Flip the gate so RootView renders the full UI.
        // This unblocks SwiftUI views (FileTreeView, GitSidebarViewModel, etc.)
        // which were holding off on their .task modifiers.
        appReadyState.tccGranted = true

        // Safe to spawn PTY and git child processes — they inherit the TCC grant.
        await restoreSession()
        startActiveProjectObservation()
        startFileEventForwarding()
    }

    // MARK: - Session Management

    /// Restore the application session, then ensure at least one project is active.
    private func restoreSession() async {
        await restoreSessionUseCase.execute()
        // Fallback: if no active project after restore (first launch or missing
        // project), activate the first available project.
        activateFirstProjectUseCase.execute()
    }

    /// Save current application state to disk.
    private func saveSession() async {
        await saveSessionUseCase.execute()
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
