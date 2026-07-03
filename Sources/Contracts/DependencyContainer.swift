// MARK: - Dependency Injection
// DI-контейнер и SwiftUI Environment интеграция.
//
// Environment ключи, `EnvironmentValues`-аксессоры и preview/no-op реализации
// вынесены в `DependencyContainer+Environment.swift`.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - Service Container

/// Централизованный контейнер сервисов.
///
/// Создаётся один раз при старте приложения (@main App).
/// В production содержит реальные реализации.
/// В тестах/previews -- моки.
///
/// Паттерн: Composition Root. Все зависимости создаются здесь,
/// не внутри сервисов. Сервисы получают зависимости через init.
@MainActor
final class ServiceContainer {

    let projectManager: any ProjectManaging
    let terminalSessionManager: any TerminalSessionManaging
    /// Concrete reference to the terminal service for `@Observable` injection.
    ///
    /// SwiftUI's `@Observable` tracking does not work through `any Protocol`
    /// existentials. Views that need to observe reactive state (e.g. tab activity
    /// indicators) must receive the concrete `TerminalService` object directly via
    /// `.environment(container.terminalService)` so that SwiftUI's
    /// `withObservationTracking` can register property-level subscriptions.
    let terminalService: TerminalService
    let gitService: any GitServicing
    let fileSystemWatcher: any FileSystemWatching
    let sessionPersistence: any SessionPersisting
    let aiCommitService: any AICommitServicing
    let gitStatusPoller: any GitStatusPolling
    let agentAvailability: any AgentAvailabilityChecking
    /// Startup readiness gate — prevents SwiftUI views from accessing TCC-protected
    /// directories before the user has granted consent.
    let appReadyState: AppReadyState
    /// Type-safe coordinator for global navigation events (install wizard, settings).
    ///
    /// Replaces `NotificationCenter` posts for `.showInstallAgentWizard` and
    /// `.showAppSettings` with direct `@Observable` property mutations that
    /// SwiftUI can observe without hidden string-keyed coupling.
    let navigationCoordinator: AppNavigationCoordinator

    /// App-wide theme/appearance service.
    ///
    /// Stored as the concrete `ThemeService` for the same
    /// reason as `terminalService`: SwiftUI's `@Observable` tracking does not work
    /// through `any Protocol` existentials. Views observing `selectedAppearance` or
    /// `resolvedColorScheme` need direct access to the concrete `@Observable` type so
    /// that `withObservationTracking` registers property-level subscriptions and
    /// triggers automatic re-renders. To inject a test double, create a concrete class
    /// that inherits from `ThemeService` or use `PreviewThemeService` below.
    let themeService: ThemeService

    /// Store for project-free terminal tabs.
    ///
    /// Concrete `@Observable` type for the same reason as `terminalService` and
    /// `themeService`: SwiftUI observation tracking requires direct access.
    let freeTabStore: FreeTabStore

    /// CodeSpeak config detection and build stats cache.
    ///
    /// Concrete `@Observable` type — same reason as `terminalService`: SwiftUI
    /// `withObservationTracking` requires the concrete type for property-level subscriptions.
    let codeSpeak: CodeSpeakService

    /// Syntax parser registry for syntax-highlighted editors.
    ///
    /// Concrete `@Observable` type — same reason as `terminalService`: SwiftUI
    /// `withObservationTracking` requires the concrete type for property-level subscriptions.
    let syntaxParserRegistry: SyntaxParserRegistry

    /// User-facing CodeSpeak behaviour preferences (auto-build, notifications, etc.).
    ///
    /// Concrete `@Observable` type for the same reason as `themeService`.
    let csPreferences: CodeSpeakPreferences

    /// General app preferences (tab close confirmation, etc.).
    ///
    /// Concrete `@Observable` type for the same reason as `themeService`.
    let generalPreferences: GeneralPreferences

    /// Remote Control HTTP/WS server.
    ///
    /// Concrete `@Observable` type -- SwiftUI views observe `connectedDeviceCount`,
    /// `isRunning`, `currentPin` for the toolbar indicator and settings pane.
    let remoteControlServer: RemoteControlServer

    /// Remote Control user preferences (port, enabled, etc.).
    ///
    /// Concrete `@Observable` type for the same reason as `generalPreferences`.
    let remoteControlPreferences: RemoteControlPreferences

    init(
        projectManager: any ProjectManaging,
        terminalSessionManager: any TerminalSessionManaging,
        terminalService: TerminalService,
        gitService: any GitServicing,
        fileSystemWatcher: any FileSystemWatching,
        sessionPersistence: any SessionPersisting,
        aiCommitService: any AICommitServicing,
        gitStatusPoller: any GitStatusPolling,
        agentAvailability: any AgentAvailabilityChecking,
        appReadyState: AppReadyState,
        navigationCoordinator: AppNavigationCoordinator,
        themeService: ThemeService,
        freeTabStore: FreeTabStore,
        codeSpeak: CodeSpeakService,
        syntaxParserRegistry: SyntaxParserRegistry,
        csPreferences: CodeSpeakPreferences,
        generalPreferences: GeneralPreferences,
        remoteControlServer: RemoteControlServer,
        remoteControlPreferences: RemoteControlPreferences
    ) {
        self.projectManager = projectManager
        self.terminalSessionManager = terminalSessionManager
        self.terminalService = terminalService
        self.gitService = gitService
        self.fileSystemWatcher = fileSystemWatcher
        self.sessionPersistence = sessionPersistence
        self.aiCommitService = aiCommitService
        self.gitStatusPoller = gitStatusPoller
        self.agentAvailability = agentAvailability
        self.appReadyState = appReadyState
        self.navigationCoordinator = navigationCoordinator
        self.themeService = themeService
        self.freeTabStore = freeTabStore
        self.codeSpeak = codeSpeak
        self.syntaxParserRegistry = syntaxParserRegistry
        self.csPreferences = csPreferences
        self.generalPreferences = generalPreferences
        self.remoteControlServer = remoteControlServer
        self.remoteControlPreferences = remoteControlPreferences
    }
}

// MARK: - View Modifier for injecting all services

extension View {
    /// Внедрить все сервисы из контейнера в environment.
    ///
    /// Использование:
    /// ```swift
    /// @main
    /// struct VibeStudioApp: App {
    ///     let container = ServiceContainer.production()
    ///
    ///     var body: some Scene {
    ///         WindowGroup {
    ///             ContentView()
    ///                 .injectServices(from: container)
    ///         }
    ///     }
    /// }
    /// ```
    @MainActor
    func injectServices(from container: ServiceContainer) -> some View {
        self
            .environment(\.projectManager, container.projectManager)
            .environment(\.terminalSessionManager, container.terminalSessionManager)
            // Inject the concrete TerminalService for @Observable tracking.
            // Views reading projectActivityStates must use this concrete type
            // so SwiftUI's withObservationTracking registers the dependency.
            .environment(container.terminalService)
            .environment(\.gitService, container.gitService)
            .environment(\.fileSystemWatcher, container.fileSystemWatcher)
            .environment(\.sessionPersistence, container.sessionPersistence)
            .environment(\.aiCommitService, container.aiCommitService)
            .environment(\.gitStatusPoller, container.gitStatusPoller)
            .environment(\.agentAvailability, container.agentAvailability)
            .environment(container.appReadyState)
            // Inject navigationCoordinator via both styles:
            // - Observable-style (.environment(object)) ensures @Observable property tracking
            //   works in RootView/ToolbarView (currentMode switch).
            // - EnvironmentKey-style (.environment(\.key, ...)) keeps existing views that
            //   use @Environment(\.navigationCoordinator) working unchanged.
            .environment(container.navigationCoordinator)
            .environment(\.navigationCoordinator, container.navigationCoordinator)
            .environment(\.themeService, container.themeService)
            .environment(\.freeTabStore, container.freeTabStore)
            .environment(\.codeSpeak, container.codeSpeak)
            .environment(\.syntaxParserRegistry, container.syntaxParserRegistry)
            .environment(\.csPreferences, container.csPreferences)
            .environment(\.generalPreferences, container.generalPreferences)
            .environment(\.remoteControlServer, container.remoteControlServer)
            .environment(\.remoteControlPreferences, container.remoteControlPreferences)
    }
}

// MARK: - Usage in Views
//
// ```swift
// struct SidebarView: View {
//     @Environment(\.projectManager) private var projectManager
//     @Environment(\.gitService) private var gitService
//
//     var body: some View {
//         List(projectManager.projects) { project in
//             ProjectRow(project: project)
//         }
//     }
// }
// ```
