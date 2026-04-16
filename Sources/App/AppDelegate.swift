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
            themeService: themeService,
            freeTabStore: freeTabStore,
            codeSpeak: codeSpeakService,
            syntaxParserRegistry: syntaxParserRegistry
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
    private lazy var freeTabStore = FreeTabStore()
    private lazy var codeSpeakService = CodeSpeakService()
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

