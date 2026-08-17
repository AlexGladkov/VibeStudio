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
        // Wire cost tracker into terminal service BEFORE building the container,
        // so that startAgentSession (called later) finds the tracker installed.
        terminalService.costTrackerService = costTrackerService
        // Wire cost tracker into remote control server for reconnect recovery
        // (GET /api/v1/projects includes cost fields on sessions).
        remoteControlServer.costTrackerService = costTrackerService
        return ServiceContainer(
            projectManager: projectStore,
            terminalSessionManager: terminalService,
            terminalService: terminalService,
            gitService: gitService,
            fileSystemWatcher: fileSystemWatcher,
            sessionPersistence: sessionStore,
            aiCommitService: aiCommitService,
            gitStatusPoller: gitStatusPoller,
            agentAvailability: agentAvailabilityService,
            updateService: updateService,
            appReadyState: appReadyState,
            navigationCoordinator: navigationCoordinator,
            themeService: themeService,
            freeTabStore: freeTabStore,
            codeSpeak: codeSpeakService,
            syntaxParserRegistry: syntaxParserRegistry,
            csPreferences: csPreferences,
            generalPreferences: generalPreferences,
            remoteControlServer: remoteControlServer,
            remoteControlPreferences: remoteControlPreferences,
            costTrackerService: costTrackerService
        )
    }()

    // MARK: - Private Services

    private lazy var projectStore = ProjectStore()
    private lazy var terminalService = TerminalService(themeService: themeService, generalPreferences: generalPreferences)
    private lazy var gitService = GitService()
    private lazy var fileSystemWatcher = FileSystemWatcher()
    private lazy var sessionStore = SessionStore()
    private lazy var aiCommitService = AICommitService()
    private lazy var gitStatusPoller = GitStatusPoller(gitService: gitService)
    private lazy var agentAvailabilityService = AgentAvailabilityService()
    private lazy var updateService: any UpdateServicing = {
        if AppLaunchEnvironment.isRunningHostedXCTest {
            return DisabledUpdateService()
        }
        return UpdateService()
    }()
    private let appReadyState = AppReadyState()
    private let navigationCoordinator = AppNavigationCoordinator()
    private lazy var themeService = ThemeService()
    private lazy var freeTabStore = FreeTabStore()
    private lazy var codeSpeakService = CodeSpeakService()
    private lazy var csPreferences = CodeSpeakPreferences()
    private lazy var generalPreferences = GeneralPreferences()
    private lazy var remoteControlPreferences = RemoteControlPreferences()
    private lazy var costTrackerService = CostTrackerService()
    private lazy var remoteAuthService = RemoteAuthService()
    private lazy var remoteControlServer = RemoteControlServer(
        authService: remoteAuthService,
        preferences: remoteControlPreferences,
        terminalService: terminalService,
        projectManager: projectStore
    )
    private lazy var syntaxParserRegistry: SyntaxParserRegistry = {
        let registry = SyntaxParserRegistry()
        registry.register(CodeSpeakParser())
        registry.register(MarkdownParser())
        return registry
    }()

    /// Lifecycle coordinator — manages TCC, session restore/save, polling, events.
    private lazy var lifecycleCoordinator = AppLifecycleCoordinator(
        container: container,
        projectStore: projectStore
    )

    // MARK: - NSApplicationDelegate

    func applicationWillFinishLaunching(_ notification: Notification) {
        // SwiftUI WindowGroup saves window geometry to UserDefaults under a key
        // that encodes the entire view-modifier type hierarchy (including all
        // .injectServices modifiers and WindowToolbarRemover). If any saved frame
        // has width <= windowMinWidth it means the window was pinned to its absolute
        // minimum — reset it so .defaultSize(1600, 1000) takes effect on next launch.
        //
        // Key format: "NSWindow Frame SwiftUI.ModifiedContent<...>-1-AppWindow-1"
        // Value format: "x y width height screenX screenY screenW screenH"
        let minUsableWidth = DSLayout.windowMinWidth  // 640 — anything at or below this is bad
        let defaults = UserDefaults.standard
        for key in defaults.dictionaryRepresentation().keys
            where key.hasPrefix("NSWindow Frame SwiftUI") {
            guard let frameStr = defaults.string(forKey: key) else { continue }
            let parts = frameStr.split(separator: " ").compactMap { Double($0) }
            // Index 2 is the saved window width.
            if parts.count >= 3, CGFloat(parts[2]) <= minUsableWidth {
                defaults.removeObject(forKey: key)
            }
        }
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        // When the app is launched as an XCTest host the test runner injects
        // the test bundle after applicationDidFinishLaunching returns.  Heavy
        // startup work (TCC, NIO server, file-system watchers, git polling)
        // races the XCTest handshake and causes the runner to time out without
        // executing a single test.  Skip all of it in test context — the test
        // bundle only needs the types, not the live services.
        guard !AppLaunchEnvironment.isRunningHostedXCTest else { return }

        // Apply stored appearance before any view renders (no TCC needed for UserDefaults).
        themeService.applyStoredAppearance()

        if AppLaunchEnvironment.isRunningHostedXCTest {
            // Hosted XCTest starts the real app process before the test runner
            // connection is fully established. Keep startup synchronous and
            // lightweight: no TCC probe, persisted project restore, PTY/git
            // session restoration, remote HTTP/WS server, Sparkle updater, file
            // watchers, or polling tasks before XCTest owns the process.
            appReadyState.tccGranted = true
            Logger.session.info("Hosted XCTest detected — heavy app startup is bypassed")
            return
        }

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
        // Keep the process alive when the test runner owns it — XCTest manages
        // the lifecycle and does not open any windows.
        !AppLaunchEnvironment.isRunningHostedXCTest
    }

}
