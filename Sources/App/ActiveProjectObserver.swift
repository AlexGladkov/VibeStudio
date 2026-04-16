// MARK: - ActiveProjectObserver
// Observes active project changes and updates dependent services.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Observes `ProjectStore.activeProjectId` changes and synchronises
/// git polling, FSEvents forwarding, and CodeSpeak mode state.
///
/// Extracted from `AppLifecycleCoordinator` to respect SRP — active project
/// observation is a distinct responsibility with its own dependencies.
@MainActor
final class ActiveProjectObserver {

    // MARK: - Dependencies

    private let container: ServiceContainer
    /// Concrete reference for `withObservationTracking` on `activeProjectId`.
    /// `@Observable` property tracking does not work through `any Protocol` existentials.
    private let projectStore: ProjectStore

    // MARK: - Observation Tasks

    private var activeProjectTask: Task<Void, Never>?
    private var fileEventTask: Task<Void, Never>?

    // MARK: - Init

    init(container: ServiceContainer, projectStore: ProjectStore) {
        self.container = container
        self.projectStore = projectStore
    }

    // MARK: - Lifecycle

    /// Start observing `activeProjectId` and file system events.
    ///
    /// `updatePolling` is called **synchronously** here so that `currentMode` is set
    /// before `TCCConsentCoordinator.revealUI()` opens the UI gate.  The async Task
    /// created by `startActiveProjectObservation()` only handles subsequent changes.
    func start() {
        updatePolling(for: projectStore.activeProjectId)
        startActiveProjectObservation()
        startFileEventForwarding()
    }

    /// Stop all observation tasks.
    func stop() {
        activeProjectTask?.cancel()
        fileEventTask?.cancel()
    }

    // MARK: - Private: Active Project Observation

    private func startActiveProjectObservation() {
        let initialId = projectStore.activeProjectId
        activeProjectTask = Task { @MainActor [weak self, weak projectStore] in
            guard let self, let projectStore else { return }
            // initialId was already passed to updatePolling synchronously in start().
            // The loop only handles subsequent changes.
            var lastProjectId: UUID? = initialId

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
            container.navigationCoordinator.syncMode(isCodeSpeak: false)
            Logger.git.debug("ActiveProjectObserver: git polling stopped — no active project")
            return
        }

        container.gitStatusPoller.startPolling(for: project.path, isActive: true)
        Logger.git.info("ActiveProjectObserver: git polling started for \(project.name, privacy: .public)")
        container.codeSpeak.checkConfig(for: project)
        let isCS = container.codeSpeak.isCodeSpeakProject(activeId)
        container.navigationCoordinator.syncMode(isCodeSpeak: isCS)
    }

    // MARK: - Private: File Event Forwarding

    private func startFileEventForwarding() {
        fileEventTask = Task { [weak self] in
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
