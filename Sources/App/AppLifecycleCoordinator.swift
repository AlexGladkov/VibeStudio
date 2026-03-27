// MARK: - AppLifecycleCoordinator
// Application lifecycle management extracted from AppDelegate.
// Handles TCC consent, session restore/save, git polling, and file events.
// macOS 14+, Swift 5.10

import AppKit
import Foundation
import OSLog

/// Manages post-launch startup sequencing and pre-termination teardown.
///
/// Extracted from `AppDelegate` so the delegate remains a thin Composition Root.
/// Receives the fully-assembled ``ServiceContainer`` and the concrete
/// ``ProjectStore`` (needed for `withObservationTracking` which requires an
/// `@Observable` concrete type, not `any ProjectManaging` existential).
/// Orchestrates:
/// - TCC consent acquisition
/// - Session restore and save
/// - Git status polling tied to the active project
/// - FSEventStream → git poller event forwarding
@MainActor
final class AppLifecycleCoordinator {

    // MARK: - Dependencies

    private let container: ServiceContainer
    /// Concrete reference for `withObservationTracking` on `activeProjectId`.
    /// (Same pattern as `TerminalService` in `ServiceContainer` — @Observable
    /// property tracking does not work through `any Protocol` existentials.)
    private let projectStore: ProjectStore

    // MARK: - UseCases

    private lazy var saveSessionUseCase = SaveSessionUseCase(
        projectManager: container.projectManager,
        terminalManager: container.terminalSessionManager,
        sessionPersistence: container.sessionPersistence
    )

    private lazy var restoreSessionUseCase = RestoreSessionUseCase(
        projectManager: container.projectManager,
        terminalManager: container.terminalSessionManager,
        sessionPersistence: container.sessionPersistence
    )

    private lazy var activateFirstProjectUseCase = ActivateFirstProjectUseCase(
        projectManager: container.projectManager,
        terminalManager: container.terminalSessionManager
    )

    // MARK: - Observation Tasks

    private var activeProjectObservation: Task<Void, Never>?
    private var fileEventObservation: Task<Void, Never>?

    // MARK: - Init

    init(container: ServiceContainer, projectStore: ProjectStore) {
        self.container = container
        self.projectStore = projectStore
    }

    // MARK: - Startup

    /// Entry point called from `applicationDidFinishLaunching`.
    ///
    /// Acquires TCC consent for ~/Documents on a background thread (blocks the
    /// background thread on the kernel gate, keeping the main run loop free to
    /// present the consent dialog), then flips the TCC gate and starts services.
    func startAfterLaunch() {
        Task { @MainActor [weak self] in
            await self?.acquireTCCConsentThenStart()
        }
    }

    // MARK: - Teardown

    /// Entry point called from `applicationShouldTerminate`.
    ///
    /// Saves session, stops PTY processes and observation tasks, then signals
    /// `NSApp.reply(toApplicationShouldTerminate: true)`.
    func stopBeforeTermination() async {
        // 1. Save session FIRST before killing anything.
        await saveSessionUseCase.execute()

        // 2. Kill all PTY processes.
        let terminalService = container.terminalService
        for projectId in terminalService.sessionsByProject.keys {
            terminalService.killAllSessions(for: projectId)
        }

        // 3. Stop git status polling and observation tasks.
        container.gitStatusPoller.stopPolling()
        activeProjectObservation?.cancel()
        fileEventObservation?.cancel()

        // 4. Stop all file watchers (also finishes the events AsyncStream).
        container.fileSystemWatcher.unwatchAll()
    }

    // MARK: - Private: TCC + Startup Sequence

    private func acquireTCCConsentThenStart() async {
        // Run the filesystem probe on a background thread so the background thread
        // BLOCKS on the kernel-level TCC check while the main run loop stays live
        // to present the consent dialog.
        await Task.detached(priority: .userInitiated) {
            let documentsURL = FileManager.default.urls(
                for: .documentDirectory, in: .userDomainMask
            ).first!
            _ = try? FileManager.default.contentsOfDirectory(
                at: documentsURL, includingPropertiesForKeys: nil
            )
        }.value

        // TCC is now resolved — open the gate so RootView renders the full UI.
        container.appReadyState.tccGranted = true

        // Resolve agent availability now that PATH is fully inherited from the
        // TCC-granted process environment.
        container.agentAvailability.refreshAll()

        // Safe to spawn PTY and git child processes.
        await restoreSession()
        startActiveProjectObservation()
        startFileEventForwarding()
    }

    // MARK: - Private: Session Management

    private func restoreSession() async {
        await restoreSessionUseCase.execute()
        // Fallback: activate the first project if none was restored (first launch
        // or all saved projects were missing from disk).
        activateFirstProjectUseCase.execute()
    }

    // MARK: - Private: Git Status Polling

    /// Observe `ProjectStore.activeProjectId` and start/stop the git status
    /// poller accordingly. Uses `withObservationTracking` bridged to a checked
    /// continuation so the task suspends with zero CPU between changes.
    private func startActiveProjectObservation() {
        activeProjectObservation = Task { @MainActor [weak self, weak projectStore] in
            guard let self, let projectStore else { return }
            var lastProjectId: UUID? = projectStore.activeProjectId
            self.updatePolling(for: lastProjectId)

            while !Task.isCancelled {
                let holder = ContinuationHolder()
                await withTaskCancellationHandler {
                    await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                        holder.set(c)
                        withObservationTracking {
                            _ = projectStore.activeProjectId
                        } onChange: {
                            holder.resume()
                        }
                    }
                } onCancel: {
                    holder.resume()
                }

                guard !Task.isCancelled else { return }
                let newId = projectStore.activeProjectId
                guard newId != lastProjectId else { continue }
                lastProjectId = newId
                self.updatePolling(for: newId)
            }
        }
    }

    private func updatePolling(for activeProjectId: UUID?) {
        guard let activeId = activeProjectId,
              let project = container.projectManager.project(for: activeId) else {
            container.gitStatusPoller.stopPolling()
            Logger.git.debug("Git status polling stopped — no active project")
            return
        }

        container.gitStatusPoller.startPolling(for: project.path, isActive: true)
        Logger.git.info("Git status polling started for \(project.name, privacy: .public)")
    }

    // MARK: - Private: File Event Forwarding

    /// Forward FSEvent stream changes to the git status poller for near-immediate
    /// refresh after a file save, without waiting for the next poll cycle.
    private func startFileEventForwarding() {
        fileEventObservation = Task { [weak self] in
            guard let self else { return }

            for await _ in self.container.fileSystemWatcher.events {
                guard !Task.isCancelled else { break }
                await MainActor.run {
                    self.container.gitStatusPoller.refreshNow()
                }
            }
        }
    }
}
