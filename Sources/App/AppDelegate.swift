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
            agentAvailability: agentAvailabilityService,
            appReadyState: appReadyState,
            navigationCoordinator: navigationCoordinator,
            themeService: themeService
        )
    }()

    // MARK: - Private Services

    private lazy var projectStore = ProjectStore()
    private lazy var terminalService = TerminalService(themeService: themeService)
    private lazy var gitService = GitService()
    private lazy var fileSystemWatcher = FileSystemWatcher()
    private lazy var sessionStore = SessionStore()
    private lazy var aiCommitService = AICommitService()
    private lazy var gitStatusPoller = GitStatusPoller(gitService: gitService)
    private lazy var agentAvailabilityService = AgentAvailabilityService()
    private let appReadyState = AppReadyState()
    private let navigationCoordinator = AppNavigationCoordinator()
    private lazy var themeService = ThemeService()

    /// Lifecycle coordinator — manages TCC, session restore/save, polling, events.
    private lazy var lifecycleCoordinator = AppLifecycleCoordinator(
        container: container,
        projectStore: projectStore
    )

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
        // Apply stored appearance before any view renders (no TCC needed for UserDefaults).
        themeService.applyStoredAppearance()

        // Load persisted project list (reads ~/Library/Application Support — no TCC).
        do {
            try projectStore.load()
        } catch {
            Logger.session.error("Failed to load projects: \(error.localizedDescription, privacy: .public)")
        }

        // Delegate TCC consent + startup sequencing to the lifecycle coordinator.
        // See AppLifecycleCoordinator for the detailed explanation of TCC ordering.
        lifecycleCoordinator.startAfterLaunch()
    }

    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        Task { @MainActor [weak self] in
            await self?.lifecycleCoordinator.stopBeforeTermination()
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

}

