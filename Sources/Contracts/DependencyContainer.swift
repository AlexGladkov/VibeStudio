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
    let gitService: any GitServicing
    let fileSystemWatcher: any FileSystemWatching
    let sessionPersistence: any SessionPersisting
    let aiCommitService: any AICommitServicing
    let gitStatusPoller: any GitStatusPolling

    init(
        projectManager: any ProjectManaging,
        terminalSessionManager: any TerminalSessionManaging,
        gitService: any GitServicing,
        fileSystemWatcher: any FileSystemWatching,
        sessionPersistence: any SessionPersisting,
        aiCommitService: any AICommitServicing,
        gitStatusPoller: any GitStatusPolling
    ) {
        self.projectManager = projectManager
        self.terminalSessionManager = terminalSessionManager
        self.gitService = gitService
        self.fileSystemWatcher = fileSystemWatcher
        self.sessionPersistence = sessionPersistence
        self.aiCommitService = aiCommitService
        self.gitStatusPoller = gitStatusPoller
    }
}

// MARK: - SwiftUI Environment Keys

/// EnvironmentKey для каждого сервиса.
/// Позволяет внедрять сервисы через @Environment в любой View.

private struct ProjectManagerKey: EnvironmentKey {
    // Compile-time safety: нет default -- приложение упадёт если забыли inject.
    // Это намеренно: лучше crash при старте, чем silent nil в runtime.
    @MainActor static let defaultValue: any ProjectManaging = PlaceholderProjectManager()
}

private struct TerminalSessionManagerKey: EnvironmentKey {
    @MainActor static let defaultValue: any TerminalSessionManaging = PlaceholderTerminalSessionManager()
}

private struct GitServiceKey: EnvironmentKey {
    static let defaultValue: any GitServicing = PlaceholderGitService()
}

private struct FileSystemWatcherKey: EnvironmentKey {
    static let defaultValue: any FileSystemWatching = PlaceholderFileSystemWatcher()
}

private struct SessionPersistenceKey: EnvironmentKey {
    static let defaultValue: any SessionPersisting = PlaceholderSessionPersistence()
}

private struct AICommitServiceKey: EnvironmentKey {
    static let defaultValue: any AICommitServicing = PlaceholderAICommitService()
}

private struct GitStatusPollerKey: EnvironmentKey {
    @MainActor static let defaultValue: any GitStatusPolling = PlaceholderGitStatusPoller()
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
            .environment(\.gitService, container.gitService)
            .environment(\.fileSystemWatcher, container.fileSystemWatcher)
            .environment(\.sessionPersistence, container.sessionPersistence)
            .environment(\.aiCommitService, container.aiCommitService)
            .environment(\.gitStatusPoller, container.gitStatusPoller)
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

// MARK: - Placeholder implementations (crash on use -- safety net)

// Эти заглушки существуют только чтобы EnvironmentKey имели defaultValue.
// При реальном использовании без inject -- fatalError сообщит разработчику.

@MainActor
private final class PlaceholderProjectManager: ProjectManaging {
    var projects: [Project] { placeholderValue() }
    var activeProjectId: UUID? {
        get { placeholderValue() }
        set { placeholderCrash() }
    }
    var recentHistory: [Project] { placeholderValue() }
    var recentProjects: [Project] { placeholderValue() }
    func addProject(at path: URL) throws -> Project { placeholderValue() }
    func removeProject(_ id: UUID) throws { placeholderCrash() }
    func updateProject(_ id: UUID, _ mutate: (inout Project) -> Void) throws { placeholderCrash() }
    func moveProjects(from indices: IndexSet, to destination: Int) { placeholderCrash() }
    func project(for id: UUID) -> Project? { placeholderValue() }
    func project(at path: URL) -> Project? { placeholderValue() }
    func load() throws { placeholderCrash() }
    func save() throws { placeholderCrash() }
}

@MainActor
private final class PlaceholderTerminalSessionManager: TerminalSessionManaging {
    var sessionsByProject: [UUID: [TerminalSession]] { placeholderValue() }
    var projectActivityStates: [UUID: TabActivityState] { placeholderValue() }
    func createSession(for projectId: UUID, shell: String?, workingDirectory: URL?, size: TerminalSize) throws -> TerminalSession { placeholderValue() }
    func attachView(to sessionId: UUID) throws -> NSView { placeholderValue() }
    func detachView(from sessionId: UUID) { placeholderCrash() }
    func resize(session sessionId: UUID, to size: TerminalSize) { placeholderCrash() }
    func killSession(_ sessionId: UUID, force: Bool) { placeholderCrash() }
    func killAllSessions(for projectId: UUID) { placeholderCrash() }
    func split(_ sessionId: UUID, direction: SplitDirection, size: TerminalSize) throws -> TerminalSession { placeholderValue() }
    func session(for id: UUID) -> TerminalSession? { placeholderValue() }
    func sessions(for projectId: UUID) -> [TerminalSession] { placeholderValue() }
    var sessionEvents: AsyncStream<TerminalSessionEvent> { placeholderValue() }
    func scrollbackContent(for sessionId: UUID) -> String? { placeholderValue() }
    func sendInput(_ text: String, to sessionId: UUID) { placeholderCrash() }
}

private final class PlaceholderGitService: GitServicing {
    func status(at repository: URL) async throws -> GitStatus { placeholderValue() }
    func diff(file: String, staged: Bool, at repository: URL) async throws -> [GitDiffHunk] { placeholderValue() }
    func fullStagedDiff(at repository: URL) async throws -> String { placeholderValue() }
    func branches(at repository: URL) async throws -> [GitBranch] { placeholderValue() }
    func log(limit: Int, at repository: URL) async throws -> [GitCommitInfo] { placeholderValue() }
    func stage(files: [String], at repository: URL) async throws { placeholderCrash() }
    func unstage(files: [String], at repository: URL) async throws { placeholderCrash() }
    func commit(message: String, at repository: URL) async throws -> String { placeholderValue() }
    func push(remote: String, at repository: URL) async throws { placeholderCrash() }
    func pull(remote: String, at repository: URL) async throws { placeholderCrash() }
    func fetch(remote: String, at repository: URL) async throws { placeholderCrash() }
    func pushBranch(_ branch: String, remote: String, at repository: URL) async throws { placeholderCrash() }
    func pullBranch(_ branch: String, isCurrent: Bool, remote: String, at repository: URL) async throws { placeholderCrash() }
    func headDiff(at repository: URL) async throws -> String { placeholderValue() }
    func defaultRemote(for branch: String?, at repository: URL) async -> String { "origin" }
    func checkout(branch: String, at repository: URL) async throws { placeholderCrash() }
    func createBranch(name: String, from startPoint: String?, at repository: URL) async throws { placeholderCrash() }
    func isRepository(at path: URL) async -> Bool { placeholderValue() }
    func repositoryRoot(for path: URL) async throws -> URL { placeholderValue() }
    func initRepository(at path: URL) async throws { placeholderCrash() }
    func addRemote(name: String, url: String, at repository: URL) async throws { placeholderCrash() }
    func remoteURL(name: String, at repository: URL) async -> String? { placeholderValue() }
    func aheadBehind(at repository: URL) async throws -> (ahead: Int, behind: Int) { placeholderValue() }
}

private final class PlaceholderFileSystemWatcher: FileSystemWatching {
    func watch(directory: URL, options: WatchOptions) throws -> WatchToken { placeholderValue() }
    func unwatch(_ token: WatchToken) { placeholderCrash() }
    func unwatchAll() { placeholderCrash() }
    var events: AsyncStream<FileChangeEvent> { placeholderValue() }
    var activeWatches: [WatchInfo] { placeholderValue() }
}

private final class PlaceholderSessionPersistence: SessionPersisting {
    func save(snapshot: AppSessionSnapshot) async throws { placeholderCrash() }
    func restore() async throws -> AppSessionSnapshot? { placeholderValue() }
    func clear() async throws { placeholderCrash() }
    func saveScrollback(_ content: String, for sessionId: UUID) async throws { placeholderCrash() }
    func loadScrollback(for sessionId: UUID) async -> String? { placeholderValue() }
    func deleteScrollback(for sessionId: UUID) async throws { placeholderCrash() }
    func pruneOrphanedScrollbacks(keeping activeSessionIds: Set<UUID>) async throws -> Int { placeholderValue() }
    var storageDirectory: URL { placeholderValue() }
    var currentSnapshotVersion: Int { placeholderValue() }
}

private final class PlaceholderAICommitService: AICommitServicing {
    func generateCommitMessage(for diff: String) async throws -> String { placeholderValue() }
}

@MainActor
private final class PlaceholderGitStatusPoller: GitStatusPolling {
    var status: GitStatus { placeholderValue() }
    var isPolling: Bool { placeholderValue() }
    var lastError: Error? { placeholderValue() }
    func startPolling(for repository: URL, isActive: Bool) { placeholderCrash() }
    func stopPolling() { placeholderCrash() }
    func refreshNow() { placeholderCrash() }
}

/// Crash with a diagnostic message for typed return values.
private func placeholderValue<T>(
    file: StaticString = #file,
    line: UInt = #line
) -> T {
    fatalError(
        "Service not injected. Call .injectServices(from:) on root View.",
        file: file,
        line: line
    )
}

/// Crash with a diagnostic message for Void-returning functions.
private func placeholderCrash(
    file: StaticString = #file,
    line: UInt = #line
) -> Never {
    fatalError(
        "Service not injected. Call .injectServices(from:) on root View.",
        file: file,
        line: line
    )
}
