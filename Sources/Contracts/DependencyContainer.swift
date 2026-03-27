// MARK: - Dependency Injection
// DI-контейнер и SwiftUI Environment интеграция.
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
    /// Stored as the concrete `ThemeService` (not `any ThemeServicing`) for the same
    /// reason as `terminalService`: SwiftUI's `@Observable` tracking does not work
    /// through `any Protocol` existentials. Views observing `selectedAppearance` or
    /// `resolvedColorScheme` need direct access to the concrete `@Observable` type so
    /// that `withObservationTracking` registers property-level subscriptions and
    /// triggers automatic re-renders. To inject a test double, create a concrete class
    /// that inherits from `ThemeService` or use `PreviewThemeService` below.
    let themeService: ThemeService

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
        themeService: ThemeService
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
    }
}

// MARK: - SwiftUI Environment Keys

/// EnvironmentKey для каждого сервиса.
/// Позволяет внедрять сервисы через @Environment в любой View.

private struct ProjectManagerKey: EnvironmentKey {
    @MainActor static let defaultValue: any ProjectManaging = PreviewProjectManager()
}

private struct TerminalSessionManagerKey: EnvironmentKey {
    @MainActor static let defaultValue: any TerminalSessionManaging = PreviewTerminalSessionManager()
}

private struct GitServiceKey: EnvironmentKey {
    static let defaultValue: any GitServicing = PreviewGitService()
}

private struct FileSystemWatcherKey: EnvironmentKey {
    static let defaultValue: any FileSystemWatching = PreviewFileSystemWatcher()
}

private struct SessionPersistenceKey: EnvironmentKey {
    static let defaultValue: any SessionPersisting = PreviewSessionPersistence()
}

private struct AICommitServiceKey: EnvironmentKey {
    static let defaultValue: any AICommitServicing = PreviewAICommitService()
}

private struct GitStatusPollerKey: EnvironmentKey {
    @MainActor static let defaultValue: any GitStatusPolling = PreviewGitStatusPoller()
}

private struct AgentAvailabilityKey: EnvironmentKey {
    @MainActor static let defaultValue: any AgentAvailabilityChecking = PreviewAgentAvailability()
}

private struct NavigationCoordinatorKey: EnvironmentKey {
    @MainActor static let defaultValue: AppNavigationCoordinator = AppNavigationCoordinator()
}

private struct ThemeServiceKey: EnvironmentKey {
    @MainActor static let defaultValue: ThemeService = ThemeService()
}

extension EnvironmentValues {
    var projectManager: any ProjectManaging {
        get { self[ProjectManagerKey.self] }
        set { self[ProjectManagerKey.self] = newValue }
    }

    var terminalSessionManager: any TerminalSessionManaging {
        get { self[TerminalSessionManagerKey.self] }
        set { self[TerminalSessionManagerKey.self] = newValue }
    }

    var gitService: any GitServicing {
        get { self[GitServiceKey.self] }
        set { self[GitServiceKey.self] = newValue }
    }

    var fileSystemWatcher: any FileSystemWatching {
        get { self[FileSystemWatcherKey.self] }
        set { self[FileSystemWatcherKey.self] = newValue }
    }

    var sessionPersistence: any SessionPersisting {
        get { self[SessionPersistenceKey.self] }
        set { self[SessionPersistenceKey.self] = newValue }
    }

    var aiCommitService: any AICommitServicing {
        get { self[AICommitServiceKey.self] }
        set { self[AICommitServiceKey.self] = newValue }
    }

    var gitStatusPoller: any GitStatusPolling {
        get { self[GitStatusPollerKey.self] }
        set { self[GitStatusPollerKey.self] = newValue }
    }

    var agentAvailability: any AgentAvailabilityChecking {
        get { self[AgentAvailabilityKey.self] }
        set { self[AgentAvailabilityKey.self] = newValue }
    }

    var navigationCoordinator: AppNavigationCoordinator {
        get { self[NavigationCoordinatorKey.self] }
        set { self[NavigationCoordinatorKey.self] = newValue }
    }

    var themeService: ThemeService {
        get { self[ThemeServiceKey.self] }
        set { self[ThemeServiceKey.self] = newValue }
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
            .environment(\.navigationCoordinator, container.navigationCoordinator)
            .environment(\.themeService, container.themeService)
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

// MARK: - Preview / Test implementations (safe no-ops)

// Эти реализации используются как default value в EnvironmentKey.
// Возвращают пустые/нейтральные значения — без crash.
// Позволяют использовать SwiftUI Previews без инъекции реальных сервисов.

@Observable
@MainActor
private final class PreviewProjectManager: ProjectManaging {
    var projects: [Project] = []
    var activeProjectId: UUID? = nil
    var recentHistory: [Project] = []
    var recentProjects: [Project] = []
    func addProject(at path: URL) throws -> Project {
        throw ProjectManagerError.invalidPath(path)
    }
    func removeProject(_ id: UUID) throws {}
    func updateProject(_ id: UUID, _ mutate: (inout Project) -> Void) throws {}
    func moveProjects(from indices: IndexSet, to destination: Int) {}
    func project(for id: UUID) -> Project? { nil }
    func project(at path: URL) -> Project? { nil }
    func load() throws {}
    func save() throws {}
}

@Observable
@MainActor
private final class PreviewTerminalSessionManager: TerminalSessionManaging {
    var sessionsByProject: [UUID: [TerminalSession]] = [:]
    var projectActivityStates: [UUID: TabActivityState] = [:]
    func createSession(
        for projectId: UUID,
        shell: String?,
        workingDirectory: URL?,
        size: TerminalSize
    ) throws -> TerminalSession {
        throw TerminalSessionError.sessionLimitReached(projectId: projectId, max: 0)
    }
    func attachView(to sessionId: UUID) throws -> NSView {
        throw TerminalSessionError.sessionNotFound(sessionId)
    }
    func detachView(from sessionId: UUID) {}
    func resize(session sessionId: UUID, to size: TerminalSize) {}
    func killSession(_ sessionId: UUID, force: Bool) {}
    func killAllSessions(for projectId: UUID) {}
    func split(
        _ sessionId: UUID,
        direction: SplitDirection,
        size: TerminalSize
    ) throws -> TerminalSession {
        throw TerminalSessionError.sessionNotFound(sessionId)
    }
    func session(for id: UUID) -> TerminalSession? { nil }
    func sessions(for projectId: UUID) -> [TerminalSession] { [] }
    var sessionEvents: AsyncStream<TerminalSessionEvent> {
        AsyncStream(TerminalSessionEvent.self) { $0.finish() }
    }
    func scrollbackContent(for sessionId: UUID) -> String? { nil }
    func sendInput(_ text: String, to sessionId: UUID) {}
    func markProjectSeen(_ projectId: UUID) {}
    @discardableResult
    func startAgentSession(
        agent: AIAssistant,
        for projectId: UUID,
        workingDirectory: String,
        apiKeyValue: String?
    ) -> TerminalSession? { nil }
}

private final class PreviewGitService: GitServicing {
    func status(at repository: URL) async throws -> GitStatus { .empty }
    func diff(file: String, staged: Bool, at repository: URL) async throws -> [GitDiffHunk] { [] }
    func fullStagedDiff(at repository: URL) async throws -> String { "" }
    func branches(at repository: URL) async throws -> [GitBranch] { [] }
    func log(limit: Int, at repository: URL) async throws -> [GitCommitInfo] { [] }
    func stage(files: [String], at repository: URL) async throws {}
    func unstage(files: [String], at repository: URL) async throws {}
    func commit(message: String, at repository: URL) async throws -> String { "" }
    func push(remote: String, at repository: URL) async throws {}
    func pull(remote: String, at repository: URL) async throws {}
    func fetch(remote: String, at repository: URL) async throws {}
    func pushBranch(_ branch: String, remote: String, at repository: URL) async throws {}
    func pullBranch(_ branch: String, isCurrent: Bool, remote: String, at repository: URL) async throws {}
    func headDiff(at repository: URL) async throws -> String { "" }
    func defaultRemote(for branch: String?, at repository: URL) async -> String { "origin" }
    func checkout(branch: String, at repository: URL) async throws {}
    func createBranch(name: String, from startPoint: String?, at repository: URL) async throws {}
    func isRepository(at path: URL) async -> Bool { false }
    func repositoryRoot(for path: URL) async throws -> URL {
        throw GitServiceError.notARepository(path: path)
    }
    func initRepository(at path: URL) async throws {}
    func addRemote(name: String, url: String, at repository: URL) async throws {}
    func remoteURL(name: String, at repository: URL) async -> String? { nil }
    func aheadBehind(at repository: URL) async throws -> (ahead: Int, behind: Int) { (0, 0) }
}

private final class PreviewFileSystemWatcher: FileSystemWatching {
    @discardableResult
    func watch(directory: URL, options: WatchOptions) throws -> WatchToken { WatchToken() }
    func unwatch(_ token: WatchToken) {}
    func unwatchAll() {}
    var events: AsyncStream<FileChangeEvent> {
        AsyncStream(FileChangeEvent.self) { $0.finish() }
    }
    var activeWatches: [WatchInfo] { [] }
}

private final class PreviewSessionPersistence: SessionPersisting {
    func save(snapshot: AppSessionSnapshot) async throws {}
    func restore() async throws -> AppSessionSnapshot? { nil }
    func clear() async throws {}
    func saveScrollback(_ content: String, for sessionId: UUID) async throws {}
    func loadScrollback(for sessionId: UUID) async -> String? { nil }
    func deleteScrollback(for sessionId: UUID) async throws {}
    func pruneOrphanedScrollbacks(keeping activeSessionIds: Set<UUID>) async throws -> Int { 0 }
    var storageDirectory: URL { URL(fileURLWithPath: NSTemporaryDirectory()) }
    var currentSnapshotVersion: Int { 0 }
}

private final class PreviewAICommitService: AICommitServicing {
    func generateCommitMessage(for diff: String) async throws -> String {
        throw AICommitServiceError.missingAPIKey
    }
}

@Observable
@MainActor
private final class PreviewGitStatusPoller: GitStatusPolling {
    var status: GitStatus = .empty
    var isPolling: Bool = false
    var lastError: Error? = nil
    func startPolling(for repository: URL, isActive: Bool) {}
    func stopPolling() {}
    func refreshNow() {}
}

@Observable
@MainActor
private final class PreviewAgentAvailability: AgentAvailabilityChecking {
    var availability: [AIAssistant: AgentAvailabilityStatus] = {
        var dict: [AIAssistant: AgentAvailabilityStatus] = [:]
        for agent in AIAssistant.allCases {
            dict[agent] = .notInstalled(installHint: agent.installHint)
        }
        return dict
    }()
    func refreshAll() {}
    func check(_ agent: AIAssistant) -> AgentAvailabilityStatus {
        availability[agent] ?? .notInstalled(installHint: agent.installHint)
    }
    func canLaunch(_ agent: AIAssistant) -> Bool { false }
}

